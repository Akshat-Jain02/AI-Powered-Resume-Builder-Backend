package com.resumeai.auth.filter;

import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.repository.UserAuthRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HeaderAuthenticationFilterTest {

    private HeaderAuthenticationFilter filter;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @Mock private ServletContext servletContext;
    @Mock private WebApplicationContext webApplicationContext;
    @Mock private UserAuthRepository userAuthRepository;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        filter = new HeaderAuthenticationFilter();
        
        // Setup lazyInit dependencies
        when(request.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE))
                .thenReturn(webApplicationContext);
        when(webApplicationContext.getBean(UserAuthRepository.class)).thenReturn(userAuthRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void doFilter_withHeaders_setsAuthentication() throws ServletException, IOException {
        when(request.getHeader("X-Username")).thenReturn("testuser");
        when(request.getHeader("X-Roles")).thenReturn("USER,ADMIN");
        
        UserAuthEntity user = new UserAuthEntity();
        user.setBanned(false);
        when(userAuthRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        filter.doFilter(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("testuser");
        assertThat(auth.getAuthorities()).hasSize(2);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_bannedUser_returnsUnauthorized() throws ServletException, IOException {
        when(request.getHeader("X-Username")).thenReturn("banneduser");
        
        UserAuthEntity user = new UserAuthEntity();
        user.setBanned(true);
        when(userAuthRepository.findByUsername("banneduser")).thenReturn(Optional.of(user));
        
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(sw.toString()).contains("Account is banned");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_noHeaders_skipsAuthentication() throws ServletException, IOException {
        when(request.getHeader("X-Username")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
