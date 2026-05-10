package com.resumeai.auth.util;

import com.resumeai.auth.entity.UserAuthEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secretValue = "mysecretkeythatislongenoughforhs256testing";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secretValue);
    }

    @Test
    void generateAndValidateToken() {
        String token = jwtUtil.generateToken("testuser", "test@example.com", List.of("USER"));
        assertThat(token).isNotBlank();
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("testuser");
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("test@example.com");
        assertThat(jwtUtil.extractRoles(token)).containsExactly("USER");
    }

    @Test
    void generateTokenFromEntity() {
        UserAuthEntity user = new UserAuthEntity();
        user.setUsername("entityuser");
        user.setEmail("entity@example.com");
        user.setRoles(List.of("ADMIN"));

        String token = jwtUtil.generateToken(user);
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("entityuser");
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("entity@example.com");
    }

    @Test
    void validateToken_invalid() {
        assertThat(jwtUtil.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void generateToken_noEmail() {
        String token = jwtUtil.generateToken("noemail", List.of("USER"));
        assertThat(jwtUtil.extractEmail(token)).isNull();
    }
}
