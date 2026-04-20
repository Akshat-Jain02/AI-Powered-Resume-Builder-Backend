package com.resumeai.auth.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.resumeai.auth.entity.UserAuthEntity;

import java.security.Key;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtil {

    @Value("${JWT_KEY}")
    private String secret;

    /** 24 hours — consistent across all token generation paths */
    private static final long EXPIRATION_MS = 1000L * 60 * 60 * 24;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /** Generate token from entity (used by OAuth2SuccessHandler) */
    public String generateToken(UserAuthEntity user) {
        return generateToken(user.getUsername(), user.getEmail(), user.getRoles());
    }

    /**
     * Generate token from username + roles (used by login controller).
     * Kept for backward-compat; prefer the overload that includes email.
     */
    public String generateToken(String username, List<String> roles) {
        return generateToken(username, null, roles);
    }

    /** Primary token builder — always embeds email so downstream services can use it. */
    public String generateToken(String username, String email, List<String> roles) {
        var builder = Jwts.builder()
                .setSubject(username)
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS));

        if (email != null && !email.isBlank()) {
            builder.claim("email", email);
        }

        return builder.signWith(getSigningKey(), SignatureAlgorithm.HS256).compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String extractEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    public List<String> extractRoles(String token) {
        return getClaims(token).get("roles", List.class);
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
