package com.resumeai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.resumeai.filter.HeaderAuthenticationFilter;

/**
 * Security configuration for ai-service (internal microservice).
 *
 * This service runs BEHIND the API Gateway, which is the sole authentication
 * boundary. The gateway validates JWT tokens and forwards trusted headers:
 *   - X-Username  : the authenticated user's username
 *   - X-Roles     : comma-separated roles (e.g. "USER,ADMIN")
 *   - X-User-Email: the user's email address
 *
 * Industry-standard internal service security:
 *   1. NO JWT validation (gateway already did it)
 *   2. NO UserDetailsService / generated password
 *   3. Stateless sessions (no cookies, no JSESSIONID)
 *   4. CSRF disabled (stateless REST API)
 *   5. No form login, no HTTP Basic
 *   6. HeaderAuthenticationFilter reads gateway headers → SecurityContext
 *   7. @PreAuthorize for role-based authorization where needed
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter() {
        return new HeaderAuthenticationFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless REST API — no CSRF needed
            .csrf(csrf -> csrf.disable())
            // No sessions — each request is independently authenticated via headers
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // No login forms or browser popups
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
            )
            // Read trusted gateway headers before Spring Security's default filters
            .addFilterBefore(headerAuthenticationFilter(),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

