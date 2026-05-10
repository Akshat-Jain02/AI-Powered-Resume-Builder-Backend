package com.resumeai.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.resumeai.auth.repository.UserAuthRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Reads the X-Username and X-Roles headers injected by the API Gateway
 * (after JWT validation) and populates the Spring Security context.
 *
 * This allows @PreAuthorize role checks to work for admin endpoints
 * in the auth-service without re-validating the JWT here.
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private UserAuthRepository userAuthRepository;

    private void lazyInit(HttpServletRequest request) {
        if (userAuthRepository == null) {
            ApplicationContext ctx = WebApplicationContextUtils
                    .getRequiredWebApplicationContext(request.getServletContext());
            this.userAuthRepository = ctx.getBean(UserAuthRepository.class);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String username = request.getHeader("X-Username");

        if (username != null && !username.isBlank()) {
            lazyInit(request);
            
            // SECURITY: Check if user is banned even if they have a valid JWT
            // This ensures immediate enforcement of bans across the auth-service.
            boolean isBanned = userAuthRepository.findByUsername(username)
                    .map(u -> u.isBanned())
                    .orElse(false);

            if (isBanned) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"message\": \"Account is banned\"}");
                return;
            }

            String rolesHeader = request.getHeader("X-Roles");

            List<SimpleGrantedAuthority> authorities;
            if (rolesHeader != null && !rolesHeader.isBlank()) {
                authorities = Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(r -> !r.isEmpty())
                        .map(role -> new SimpleGrantedAuthority(
                                role.startsWith("ROLE_") ? role : "ROLE_" + role))
                        .collect(Collectors.toList());
            } else {
                authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
            }

            final UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }

        filterChain.doFilter(request, response);
    }
}
