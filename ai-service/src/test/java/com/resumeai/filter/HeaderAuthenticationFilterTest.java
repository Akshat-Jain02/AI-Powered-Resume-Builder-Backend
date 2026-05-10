package com.resumeai.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeaderAuthenticationFilterTest {

    private HeaderAuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new HeaderAuthenticationFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_withUsernameAndRoles_setsAuthentication() throws Exception {
        when(request.getHeader("X-Username")).thenReturn("alice");
        when(request.getHeader("X-Roles")).thenReturn("ADMIN, USER");

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("alice", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withUsernameAndNoRoles_defaultsToUser() throws Exception {
        when(request.getHeader("X-Username")).thenReturn("bob");
        when(request.getHeader("X-Roles")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("bob", auth.getPrincipal());
        assertEquals(1, auth.getAuthorities().size());
        assertEquals("ROLE_USER", auth.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void doFilterInternal_withRoleAlreadyPrefixed_doesNotDoublePrefix() throws Exception {
        when(request.getHeader("X-Username")).thenReturn("alice");
        when(request.getHeader("X-Roles")).thenReturn("ROLE_MANAGER");

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("ROLE_MANAGER", auth.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void doFilterInternal_noUsername_doesNotSetAuthentication() throws Exception {
        when(request.getHeader("X-Username")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_blankUsername_doesNotSetAuthentication() throws Exception {
        when(request.getHeader("X-Username")).thenReturn("  ");

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
