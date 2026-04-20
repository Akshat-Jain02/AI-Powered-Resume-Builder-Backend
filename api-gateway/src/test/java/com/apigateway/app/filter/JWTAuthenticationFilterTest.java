package com.apigateway.app.filter;

import com.apigateway.app.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.Key;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class JWTAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private GatewayFilterChain chain;

    private JWTAuthenticationFilter filter;
    private static final String SECRET = "my-very-long-secret-key-for-testing-purposes-min256bits!!";
    private Key signingKey;

    @BeforeEach
    void setUp() {
        filter = new JWTAuthenticationFilter(jwtUtil);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private Claims buildClaims(String username, List<String> roles, String email) {
        return (Claims) Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(
                        Jwts.builder()
                                .setSubject(username)
                                .claim("roles", roles)
                                .claim("email", email)
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                                .signWith(signingKey, SignatureAlgorithm.HS256)
                                .compact()
                ).getBody();
    }

    @Test
    void filter_optionsRequest_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .options("/api/resume/generate").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // not set
    }

    @Test
    void filter_publicPath_signin_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/auth/signin").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(any());
    }

    @Test
    void filter_publicPath_register_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/auth/register").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(any());
    }

    @Test
    void filter_publicPath_forgotPassword_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/auth/forgot-password").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(any());
    }

    @Test
    void filter_missingAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/resume/saved").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_malformedAuthHeader_noBearer_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/resume/saved")
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_invalidToken_returns401() {
        when(jwtUtil.validateTokenAndGetClaims("bad-token"))
                .thenThrow(new io.jsonwebtoken.JwtException("Invalid"));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/resume/saved")
                .header("Authorization", "Bearer bad-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_validToken_userEndpoint_passesThrough() {
        Claims claims = buildClaims("alice", List.of("USER"), "alice@example.com");
        when(jwtUtil.validateTokenAndGetClaims(anyString())).thenReturn(claims);
        when(jwtUtil.isTokenExpired(claims)).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/resume/saved")
                .header("Authorization", "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(any());
    }

    @Test
    void filter_validToken_adminPath_withAdminRole_passesThrough() {
        Claims claims = buildClaims("admin", List.of("ADMIN"), "admin@example.com");
        when(jwtUtil.validateTokenAndGetClaims(anyString())).thenReturn(claims);
        when(jwtUtil.isTokenExpired(claims)).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/admin/users")
                .header("Authorization", "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(any());
    }

    @Test
    void filter_validToken_adminPath_withoutAdminRole_returns403() {
        Claims claims = buildClaims("alice", List.of("USER"), "alice@example.com");
        when(jwtUtil.validateTokenAndGetClaims(anyString())).thenReturn(claims);
        when(jwtUtil.isTokenExpired(claims)).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/admin/users")
                .header("Authorization", "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void filter_expiredToken_returns401() {
        Claims claims = buildClaims("alice", List.of("USER"), "alice@example.com");
        when(jwtUtil.validateTokenAndGetClaims(anyString())).thenReturn(claims);
        when(jwtUtil.isTokenExpired(claims)).thenReturn(true);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/resume/saved")
                .header("Authorization", "Bearer expired-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_validToken_injectsXUsernameHeader() {
        Claims claims = buildClaims("alice", List.of("USER"), "alice@example.com");
        when(jwtUtil.validateTokenAndGetClaims(anyString())).thenReturn(claims);
        when(jwtUtil.isTokenExpired(claims)).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/payment/credits")
                .header("Authorization", "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        // Verify chain was called (headers are mutated on the new exchange passed to chain)
        verify(chain).filter(argThat(ex ->
                "alice".equals(ex.getRequest().getHeaders().getFirst("X-Username"))
        ));
    }

    @Test
    void filter_validToken_injectsXUserEmailHeader() {
        Claims claims = buildClaims("alice", List.of("USER"), "alice@example.com");
        when(jwtUtil.validateTokenAndGetClaims(anyString())).thenReturn(claims);
        when(jwtUtil.isTokenExpired(claims)).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/payment/credits")
                .header("Authorization", "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(argThat(ex ->
                "alice@example.com".equals(ex.getRequest().getHeaders().getFirst("X-User-Email"))
        ));
    }

    @Test
    void filter_getOrder_returnsNegativeOne() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }

    @Test
    void filter_oauth2Path_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/oauth2/authorization/google").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(any());
    }

    @Test
    void filter_nullRoles_works() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("alice");
        when(claims.get("roles", List.class)).thenReturn(null);
        when(claims.get("email", String.class)).thenReturn(null);
        when(jwtUtil.validateTokenAndGetClaims(anyString())).thenReturn(claims);
        when(jwtUtil.isTokenExpired(claims)).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/resume/saved")
                .header("Authorization", "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(argThat(ex ->
                "".equals(ex.getRequest().getHeaders().getFirst("X-Roles"))
        ));
    }

    @Test
    void filter_blankEmail_skipsHeader() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("alice");
        when(claims.get("roles", List.class)).thenReturn(List.of("USER"));
        when(claims.get("email", String.class)).thenReturn("  ");
        when(jwtUtil.validateTokenAndGetClaims(anyString())).thenReturn(claims);
        when(jwtUtil.isTokenExpired(claims)).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/resume/saved")
                .header("Authorization", "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(argThat(ex ->
                !ex.getRequest().getHeaders().containsKey("X-User-Email")
        ));
    }
}
