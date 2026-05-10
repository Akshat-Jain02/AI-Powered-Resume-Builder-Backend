package com.resumeai.templateservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new RequestLoggingFilter();
        MDC.clear();
    }

    @Test
    void doFilterInternal_WithHeaders_ShouldPopulateMDCAndHeaders() throws ServletException, IOException {
        when(request.getHeader("X-Trace-Id")).thenReturn("trace-123");
        when(request.getHeader("X-Request-Id")).thenReturn("req-456");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Trace-Id", "trace-123");
        verify(response).setHeader("X-Request-Id", "req-456");
        verify(filterChain).doFilter(request, response);
        assertNull(MDC.get("traceId")); // MDC should be cleared after request
    }

    @Test
    void doFilterInternal_WithoutHeaders_ShouldGenerateUUIDs() throws ServletException, IOException {
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/save");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Trace-Id"), anyString());
        verify(response).setHeader(eq("X-Request-Id"), anyString());
        verify(filterChain).doFilter(request, response);
    }
}
