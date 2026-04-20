package com.apigateway.app.filter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.apigateway.app.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;

import io.jsonwebtoken.Claims;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JWTAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtils;

    public JWTAuthenticationFilter(JwtUtil jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/signin",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/login",
            "/oauth2",
            "/v3/api-docs",
            "/swagger-ui",
            "/webjars"
    );

    /** Paths that require the caller to have the ADMIN role in their JWT. */
    private static final List<String> ADMIN_PATHS = List.of(
            "/api/admin/",
            "/api/templates/admin/",
            "/api/payment/admin/",
            "/api/resume/admin/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        // Allow preflight OPTIONS requests through without auth
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // Allow public endpoints and any proxied swagger endpoints
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith) || path.contains("/v3/api-docs")) {
            return chain.filter(exchange);
        }

        // Get Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Request to {} rejected: missing or malformed Authorization header", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        Claims claims;
        try {
            claims = jwtUtils.validateTokenAndGetClaims(token);
        } catch (Exception e) {
            log.warn("Request to {} rejected: invalid JWT - {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (jwtUtils.isTokenExpired(claims)) {
            log.warn("Request to {} rejected: JWT expired for user: {}", path, claims.getSubject());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String username = claims.getSubject();

        List<?> rawRoles = claims.get("roles", List.class);
        String rolesHeader = rawRoles == null ? "" :
                rawRoles.stream().map(Object::toString).collect(Collectors.joining(","));

        // ── Admin-path enforcement ──────────────────────────────────────────
        // Any path under these prefixes requires the ADMIN role embedded in the JWT.
        // This is the gateway-level guard; individual services add a second layer
        // via @PreAuthorize / hasRole("ADMIN") in their own SecurityConfig.
        boolean isAdminPath = ADMIN_PATHS.stream().anyMatch(path::startsWith);
        if (isAdminPath) {
            boolean hasAdminRole = rawRoles != null && rawRoles.stream()
                    .map(Object::toString)
                    .anyMatch(r -> r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("ROLE_ADMIN"));
            if (!hasAdminRole) {
                log.warn("Forbidden: user '{}' attempted admin path '{}' without ADMIN role", username, path);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }
        // ───────────────────────────────────────────────────────────────────

        // Extract email claim embedded by auth-service; forward so downstream services avoid DB lookups
        String email = claims.get("email", String.class);

        ServerHttpRequest.Builder reqBuilder = exchange.getRequest().mutate()
                .header("X-Username", username)
                .header("X-Roles", rolesHeader);

        if (email != null && !email.isBlank()) {
            reqBuilder.header("X-User-Email", email);
        }

        ServerHttpRequest mutatedRequest = reqBuilder.build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
