package com.resumeai.resumeservice.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HeaderAuthenticationFilterTest {

    private HeaderAuthenticationFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new HeaderAuthenticationFilter();
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void withUsernameAndRoles() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Username", "alice");
        req.addHeader("X-Roles", "USER,ADMIN");
        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).hasSize(2);
    }

    @Test
    void withUsernameOnly_defaultsToUser() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Username", "bob");
        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_USER");
    }

    @Test
    void noHeader_noAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void blankUsername_noAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Username", "  ");
        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void blankRoles_defaultsToUser() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Username", "dave");
        req.addHeader("X-Roles", " ");
        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_USER");
    }

    @Test
    void roleAlreadyPrefixed() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Username", "carol");
        req.addHeader("X-Roles", "ROLE_ADMIN");
        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_ADMIN");
    }
}
