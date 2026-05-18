package com.resumeai.interceptor;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeignTraceInterceptorTest {

    private FeignTraceInterceptor interceptor;
    private RequestTemplate template;

    @BeforeEach
    void setUp() {
        interceptor = new FeignTraceInterceptor();
        template = new RequestTemplate();
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void apply_WithMdcValues_SetsHeaders() {
        MDC.put("traceId", "test-trace");
        MDC.put("requestId", "test-req");

        interceptor.apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertTrue(headers.containsKey("X-Trace-Id"));
        assertTrue(headers.containsKey("X-Request-Id"));
        assertEquals("test-trace", headers.get("X-Trace-Id").iterator().next());
        assertEquals("test-req", headers.get("X-Request-Id").iterator().next());
    }

    @Test
    void apply_WithoutMdcButWithRequestAttributes_SetsHeadersFromRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "req-trace");
        request.addHeader("X-Request-Id", "req-req");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        interceptor.apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertTrue(headers.containsKey("X-Trace-Id"));
        assertTrue(!headers.containsKey("X-Request-Id"));
        assertEquals("req-trace", headers.get("X-Trace-Id").iterator().next());
    }

    @Test
    void apply_WithBlankMdc_SetsHeadersFromRequest() {
        MDC.put("traceId", "  ");
        MDC.put("requestId", "");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "req-trace-2");
        request.addHeader("X-Request-Id", "req-req-2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        interceptor.apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertTrue(headers.containsKey("X-Trace-Id"));
        assertTrue(!headers.containsKey("X-Request-Id"));
        assertEquals("req-trace-2", headers.get("X-Trace-Id").iterator().next());
    }

    @Test
    void apply_WithoutMdcAndWithoutRequestAttributes_DoesNotSetHeaders() {
        interceptor.apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertTrue(!headers.containsKey("X-Trace-Id"));
        assertTrue(!headers.containsKey("X-Request-Id"));
    }

    @Test
    void apply_WithOnlyTraceId_SetsOnlyTraceIdHeader() {
        MDC.put("traceId", "test-trace");

        interceptor.apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertTrue(headers.containsKey("X-Trace-Id"));
        assertTrue(!headers.containsKey("X-Request-Id"));
    }

    @Test
    void apply_WithOnlyRequestId_SetsOnlyRequestIdHeader() {
        MDC.put("requestId", "test-req");

        interceptor.apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertTrue(!headers.containsKey("X-Trace-Id"));
        assertTrue(headers.containsKey("X-Request-Id"));
        assertEquals("test-req", headers.get("X-Request-Id").iterator().next());
    }
}
