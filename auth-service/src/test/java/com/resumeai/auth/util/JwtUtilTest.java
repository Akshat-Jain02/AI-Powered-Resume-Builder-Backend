package com.resumeai.auth.util;

import com.resumeai.auth.entity.UserAuthEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String SECRET = "my-very-long-secret-key-for-testing-purposes-minimum-256-bits";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
    }

    @Test
    void generateAndValidateToken() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", List.of("USER"));
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void extractUsername() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", List.of("USER"));
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void extractEmail() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", List.of("USER"));
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("alice@example.com");
    }

    @Test
    void extractRoles() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", List.of("USER", "ADMIN"));
        assertThat(jwtUtil.extractRoles(token)).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void generateToken_fromEntity() {
        UserAuthEntity entity = new UserAuthEntity();
        entity.setUsername("bob");
        entity.setEmail("bob@example.com");
        entity.setRoles(List.of("USER"));

        String token = jwtUtil.generateToken(entity);
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("bob");
    }

    @Test
    void generateToken_withoutEmail_noEmailClaim() {
        String token = jwtUtil.generateToken("alice", (String) null, List.of("USER"));
        assertThat(jwtUtil.extractEmail(token)).isNull();
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertThat(jwtUtil.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", List.of("USER"));
        String tampered = token + "tampered";
        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }
}
