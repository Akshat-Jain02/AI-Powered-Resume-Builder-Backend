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
            "/webjars",
            "/api/templates"
    );

    /** Paths that require the caller to have the ADMIN role in their JWT. */
    private static final List<String> ADMIN_PATHS = List.of(
            "/api/admin",
            "/api/templates/admin",
            "/api/payment/admin",
            "/api/resume/admin"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        boolean isPublicPath = PUBLIC_PATHS.stream().anyMatch(path::startsWith) || path.contains("/v3/api-docs");
        boolean isAdminPath = ADMIN_PATHS.stream().anyMatch(path::startsWith);

        if (isPublicPath && !isAdminPath) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Request to {} rejected: missing Authorization header", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = jwtUtils.validateTokenAndGetClaims(token);
        } catch (Exception _) {
            log.warn("Request to {} rejected: invalid JWT", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (jwtUtils.isTokenExpired(claims)) {
            log.warn("Request to {} rejected: JWT expired for user: {}", path, claims.getSubject());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (isAdminPath && !hasAdminRole(claims)) {
            log.warn("Forbidden: user '{}' attempted admin path '{}' without ADMIN role", claims.getSubject(), path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        ServerHttpRequest mutatedRequest = mutateRequestWithClaims(exchange, claims);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean hasAdminRole(Claims claims) {
        List<?> rawRoles = claims.get("roles", List.class);
        return rawRoles != null && rawRoles.stream()
                .map(Object::toString)
                .anyMatch(r -> r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("ROLE_ADMIN"));
    }

    private ServerHttpRequest mutateRequestWithClaims(ServerWebExchange exchange, Claims claims) {
        String username = claims.getSubject();
        List<?> rawRoles = claims.get("roles", List.class);
        String rolesHeader = rawRoles == null ? "" :
                rawRoles.stream().map(Object::toString).collect(Collectors.joining(","));
        String email = claims.get("email", String.class);

        ServerHttpRequest.Builder reqBuilder = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-Username");
                    headers.remove("X-Roles");
                    headers.remove("X-User-Email");
                })
                .header("X-Username", username)
                .header("X-Roles", rolesHeader);

        if (email != null && !email.isBlank()) {
            reqBuilder.header("X-User-Email", email);
        }
        return reqBuilder.build();
    }
}
