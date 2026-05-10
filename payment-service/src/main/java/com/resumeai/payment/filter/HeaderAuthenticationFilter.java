package com.resumeai.payment.filter;

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

/**
 * Reads trusted headers forwarded by the API Gateway and populates
 * the Spring Security context. This is the ONLY authentication
 * mechanism for internal microservices.
 *
 * Headers consumed:
 *   - X-Username : authenticated user's username
 *   - X-Roles    : comma-separated roles (e.g. "USER,ADMIN")
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String xUsername = request.getHeader("X-Username");

        if (xUsername != null && !xUsername.isBlank()) {
            final String xRoles = request.getHeader("X-Roles");

            final List<SimpleGrantedAuthority> authorities = parseAuthorities(xRoles);

            final UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(xUsername, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> parseAuthorities(String roles) {
        if (roles == null || roles.isBlank()) {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(role -> new SimpleGrantedAuthority(
                        role.startsWith("ROLE_") ? role : "ROLE_" + role))
                .collect(Collectors.toList());
    }
}
