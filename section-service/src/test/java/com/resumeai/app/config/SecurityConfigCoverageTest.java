package com.resumeai.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SecurityConfigCoverageTest {

    @Test
    void headerAuthenticationFilter_withRoles() throws Exception {
        SecurityConfig config = new SecurityConfig();
        OncePerRequestFilter filter = config.headerAuthenticationFilter();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Username", "alice");
        req.addHeader("X-Roles", "ADMIN, ROLE_USER");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
        
        // This is package private or protected, we need to call it via reflection or 
        // a standard filter invocation. Since we have a web.filter.OncePerRequestFilter, 
        // we can call doFilter(req, res, chain)
        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        
        verify(chain).doFilter(req, res);
    }

    @Test
    void headerAuthenticationFilter_withoutRoles() throws Exception {
        SecurityConfig config = new SecurityConfig();
        OncePerRequestFilter filter = config.headerAuthenticationFilter();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Username", "bob");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("bob");
        
        verify(chain).doFilter(req, res);
    }
    
    @Test
    void headerAuthenticationFilter_noUsername() throws Exception {
        SecurityConfig config = new SecurityConfig();
        OncePerRequestFilter filter = config.headerAuthenticationFilter();

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
    }
}
