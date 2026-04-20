package com.resumeai.resumeservice.filter;

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
    void withTraceHeaders_propagates() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/resume/saved");
        req.addHeader("X-Trace-Id", "t1");
        req.addHeader("X-Request-Id", "r1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(req, resp, chain);
        assertThat(resp.getHeader("X-Trace-Id")).isEqualTo("t1");
        assertThat(MDC.get("traceId")).isNull();
        verify(chain).doFilter(req, resp);
    }

    @Test
    void withoutHeaders_generates() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/resume/generate");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(req, resp, chain);
        assertThat(resp.getHeader("X-Trace-Id")).isNotBlank();
        verify(chain).doFilter(req, resp);
    }

    @Test
    void blankHeaders_generates() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/resume/saved");
        req.addHeader("X-Trace-Id", " ");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(req, resp, chain);
        assertThat(resp.getHeader("X-Trace-Id")).isNotEqualTo(" ");
    }
}
