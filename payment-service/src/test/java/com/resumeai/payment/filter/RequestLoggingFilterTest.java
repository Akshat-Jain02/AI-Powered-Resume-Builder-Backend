package com.resumeai.payment.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void withTraceHeaders_propagatesExisting() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/payment/health");
        req.addHeader("X-Trace-Id", "trace-123");
        req.addHeader("X-Request-Id", "req-456");

        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        assertThat(resp.getHeader("X-Trace-Id")).isEqualTo("trace-123");
        assertThat(resp.getHeader("X-Request-Id")).isNotBlank();
        verify(chain).doFilter(req, resp);
        assertThat(MDC.get("traceId")).isNull(); // cleared after
    }

    @Test
    void withoutHeaders_generatesUUIDs() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/payment/verify");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        assertThat(resp.getHeader("X-Trace-Id")).isNotBlank();
        assertThat(resp.getHeader("X-Request-Id")).isNotBlank();
        verify(chain).doFilter(req, resp);
    }

    @Test
    void blankHeaders_generatesUUIDs() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/payment/credits");
        req.addHeader("X-Trace-Id", "  ");
        req.addHeader("X-Request-Id", "");

        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        assertThat(resp.getHeader("X-Trace-Id")).isNotEqualTo("  ");
        verify(chain).doFilter(req, resp);
    }
}
