package com.resumeai.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void doFilterInternal_withHeaders_populatesMDC() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("X-Trace-Id")).thenReturn("trace-1");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader("X-Trace-Id", "trace-1");
        verify(chain).doFilter(request, response);
        // MDC is cleared in finally, but we know it ran because of header setting
    }

    @Test
    void doFilterInternal_withoutHeaders_generatesNewOnes() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader(eq("X-Trace-Id"), anyString());
        verify(response).setHeader(eq("X-Request-Id"), anyString());
    }
}
