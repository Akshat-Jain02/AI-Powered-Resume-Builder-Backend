package com.resumeai.app.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * RequestLoggingFilter — runs first on every HTTP request.
 *
 * Populates MDC with:
 *   traceId   — X-Trace-Id header (from API Gateway) or newly generated UUID
 *   requestId — unique UUID for this specific request leg
 *   service   — application name (useful in aggregated log dashboards)
 *   method    — HTTP method (GET, POST, …)
 *   uri       — request URI
 *
 * MDC is automatically cleared after each request to prevent leakage
 * across thread-pool reuse (critical for servlet containers).
 */
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER   = "X-Trace-Id";
    private static final String REQUEST_HEADER = "X-Request-Id";
    private static final String SERVICE_NAME   = "section-service";

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String trace = resolveHeader(request, TRACE_HEADER);
        final String reqId = UUID.randomUUID().toString(); // Always generate new ID for current leg
        final String parentRequestId = request.getHeader(REQUEST_HEADER);

        try {
            MDC.put("traceId", trace);
            MDC.put("requestId", reqId);
            MDC.put("service", SERVICE_NAME);
            MDC.put("method", request.getMethod());
            MDC.put("uri", request.getRequestURI());
            if (parentRequestId != null && !parentRequestId.isBlank()) {
                MDC.put("parentRequestId", parentRequestId);
            }

            response.setHeader(TRACE_HEADER, trace);
            response.setHeader(REQUEST_HEADER, reqId);

            long begin = System.currentTimeMillis();
            filterChain.doFilter(request, response);
            long end = System.currentTimeMillis();

            log.info("SECTION-LOG: {} {} returned {} ({}ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    (end - begin));

        } finally {
            MDC.clear();
        }
    }

    /** Returns existing header value, or generates a fresh UUID. */
    private String resolveHeader(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return (value != null && !value.isBlank()) ? value : UUID.randomUUID().toString();
    }
}
