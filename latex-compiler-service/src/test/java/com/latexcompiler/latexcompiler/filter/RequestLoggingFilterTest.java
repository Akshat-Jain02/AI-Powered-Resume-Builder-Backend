package com.latexcompiler.latexcompiler.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @Test
    void testDoFilterInternal_WithHeaders() throws ServletException, IOException {
        when(request.getHeader("X-Trace-Id")).thenReturn("trace-123");
        when(request.getHeader("X-Request-Id")).thenReturn("request-456");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/compile");
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Trace-Id", "trace-123");
        verify(response).setHeader("X-Request-Id", "request-456");
        verify(filterChain).doFilter(request, response);
        assertNull(MDC.get("traceId")); // MDC should be cleared after request
    }

    @Test
    void testDoFilterInternal_GeneratedIds() throws ServletException, IOException {
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/health");
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Trace-Id"), anyString());
        verify(response).setHeader(eq("X-Request-Id"), anyString());
        verify(filterChain).doFilter(request, response);
    }
}
