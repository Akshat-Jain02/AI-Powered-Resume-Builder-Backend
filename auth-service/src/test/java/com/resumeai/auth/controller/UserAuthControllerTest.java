package com.resumeai.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.auth.auth2.OAuth2SuccessHandler;
import com.resumeai.auth.dto.request.UserLoginDTO;
import com.resumeai.auth.dto.request.UserRegistrationDTO;
import com.resumeai.auth.entity.PasswordResetToken;
import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.repository.UserAuthRepository;
import com.resumeai.auth.service.PasswordResetService;
import com.resumeai.auth.service.UserAuthService;
import com.resumeai.auth.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = UserAuthController.class,
        excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@Import(UserAuthControllerTest.TestSecurityConfig.class)
@ExtendWith(MockitoExtension.class)
class UserAuthControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .anyRequest().authenticated())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .build();
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserAuthRepository userAuthRepository;
    @MockBean UserAuthService userAuthService;
    @MockBean AuthenticationManager authenticationManager;
    @MockBean JwtUtil jwtUtil;
    @MockBean PasswordEncoder passwordEncoder;
    @MockBean PasswordResetService passwordResetService;
    @MockBean OAuth2SuccessHandler oAuth2SuccessHandler;

    @Test
    void register_success() throws Exception {
        UserRegistrationDTO dto = new UserRegistrationDTO("alice", "password123", "alice@example.com");

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully"));

        verify(userAuthService).save(any(UserRegistrationDTO.class));
    }

    @Test
    void login_success() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername("alice");
        dto.setPassword("pass123");

        UserAuthEntity entity = new UserAuthEntity();
        entity.setEmail("alice@example.com");

        var auth = new UsernamePasswordAuthenticationToken("alice", "pass123",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userAuthRepository.findByUsername("alice")).thenReturn(Optional.of(entity));
        when(jwtUtil.generateToken(eq("alice"), eq("alice@example.com"), any())).thenReturn("jwt-token");

        mockMvc.perform(post("/api/auth/signin")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername("alice");
        dto.setPassword("wrong");

        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/auth/signin")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPassword_success_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void me_authenticated_returnsUsername() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken("alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(get("/api/auth/me")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void me_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_success() throws Exception {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("valid-token");
        token.setExpiryDate(java.time.LocalDateTime.now().plusHours(1));
        UserAuthEntity user = new UserAuthEntity();
        user.setUsername("alice");
        token.setUserAuthEntity(user);

        when(passwordResetService.findByToken("valid-token")).thenReturn(token);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pass");

        mockMvc.perform(post("/api/auth/reset-password")
                .param("token", "valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newpassword\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"));

        verify(userAuthRepository).save(user);
    }

    @Test
    void resetPassword_tokenExpired() throws Exception {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("expired-token");
        token.setExpiryDate(java.time.LocalDateTime.now().minusHours(1));

        when(passwordResetService.findByToken("expired-token")).thenReturn(token);

        mockMvc.perform(post("/api/auth/reset-password")
                .param("token", "expired-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newpassword\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token is invalid or expired"));
    }

    @Test
    void resetPassword_invalidToken() throws Exception {
        when(passwordResetService.findByToken("invalid")).thenThrow(new RuntimeException("not found"));

        mockMvc.perform(post("/api/auth/reset-password")
                .param("token", "invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newpassword\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token is invalid or expired"));
    }

    @Test
    void resetPassword_shortPassword() throws Exception {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("valid-token");
        token.setExpiryDate(java.time.LocalDateTime.now().plusHours(1));

        when(passwordResetService.findByToken("valid-token")).thenReturn(token);

        mockMvc.perform(post("/api/auth/reset-password")
                .param("token", "valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must be at least 6 characters"));
    }

    @Test
    void login_userNotFoundAfterAuth_returnsNullEmail() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername("unknown");
        dto.setPassword("pass");

        var auth = new UsernamePasswordAuthenticationToken("unknown", "pass", List.of());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userAuthRepository.findByUsername("unknown")).thenReturn(java.util.Optional.empty());
        when(jwtUtil.generateToken(any(), isNull(), any())).thenReturn("token");

        mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }
}
