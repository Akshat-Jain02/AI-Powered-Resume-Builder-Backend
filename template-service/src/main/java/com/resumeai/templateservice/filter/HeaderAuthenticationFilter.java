package com.resumeai.templateservice.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String userFromGateway = request.getHeader("X-Username");

        if (userFromGateway != null && !userFromGateway.trim().isEmpty()) {
            final String rolesFromGateway = request.getHeader("X-Roles");

            final List<SimpleGrantedAuthority> mappedAuthorities = parseRoles(rolesFromGateway);

            final UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userFromGateway, null, mappedAuthorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> parseRoles(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(role -> {
                    String r = role.toUpperCase();
                    return new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r);
                })
                .collect(Collectors.toList());
    }
}
