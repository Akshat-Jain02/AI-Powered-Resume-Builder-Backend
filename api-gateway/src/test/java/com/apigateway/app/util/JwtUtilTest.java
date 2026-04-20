package com.apigateway.app.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String SECRET = "my-very-long-secret-key-for-testing-purposes-min256bits!!";
    private Key signingKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    @Test
    void validateTokenAndGetClaims_validToken_returnsClaims() {
        String token = Jwts.builder()
                .setSubject("testuser")
                .setExpiration(new Date(System.currentTimeMillis() + 10000))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        Claims claims = jwtUtil.validateTokenAndGetClaims(token);
        assertThat(claims.getSubject()).isEqualTo("testuser");
    }

    @Test
    void isTokenExpired_notExpired_returnsFalse() {
        Claims claims = Jwts.claims().setExpiration(new Date(System.currentTimeMillis() + 10000));
        assertThat(jwtUtil.isTokenExpired(claims)).isFalse();
    }

    @Test
    void isTokenExpired_expired_returnsTrue() {
        Claims claims = Jwts.claims().setExpiration(new Date(System.currentTimeMillis() - 10000));
        assertThat(jwtUtil.isTokenExpired(claims)).isTrue();
    }
}
