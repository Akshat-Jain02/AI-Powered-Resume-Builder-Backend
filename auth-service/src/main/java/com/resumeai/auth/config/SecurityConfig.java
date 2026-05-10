package com.resumeai.auth.config;

import java.util.List;

import com.resumeai.auth.auth2.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.resumeai.auth.auth2.OAuth2SuccessHandler;
import com.resumeai.auth.filter.HeaderAuthenticationFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter() {
        return new HeaderAuthenticationFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }



    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   OAuth2SuccessHandler successHandler,
                                                   CustomOAuth2UserService customOAuth2UserService) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            // OAuth2 login REQUIRES sessions — do NOT use STATELESS here
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/login/**", "/oauth2/**", "/error").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Admin routes: the gateway forwards X-Roles header; @PreAuthorize enforces ADMIN check
                .requestMatchers("/api/admin/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .oauth2Login(oauth2 -> oauth2
                .successHandler(successHandler)
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
            )
            .addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
