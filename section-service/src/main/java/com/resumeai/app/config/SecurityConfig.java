package com.resumeai.app.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security configuration for section-service (internal microservice).
 *
 * Runs behind the API Gateway. Trusts X-Username and X-Roles headers.
 *
 * No JWT validation, no UserDetailsService, no generated password.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public OncePerRequestFilter headerAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {
                final String userHeaderValue = request.getHeader("X-Username");
                if (userHeaderValue != null && !userHeaderValue.trim().isEmpty()) {
                    final String rawRolesHeader = request.getHeader("X-Roles");
                    final List<SimpleGrantedAuthority> grantedAuthorities = extractRoles(rawRolesHeader);
                    
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userHeaderValue, null, grantedAuthorities));
                }
                filterChain.doFilter(request, response);
            }

            private List<SimpleGrantedAuthority> extractRoles(String roles) {
                if (roles == null || roles.isBlank()) {
                    return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
                }
                return Arrays.stream(roles.split(","))
                        .map(String::trim)
                        .filter(r -> !r.isEmpty())
                        .map(role -> new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role))
                        .collect(Collectors.toList());
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(headerAuthenticationFilter(),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

