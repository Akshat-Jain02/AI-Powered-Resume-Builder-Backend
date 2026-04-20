package com.resumeai.payment.filter;

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
    private static final String SERVICE_NAME   = "payment-service";

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        // Propagate existing trace from gateway, or mint a new one
        String traceId   = resolveHeader(request, TRACE_HEADER);
        String requestId = resolveHeader(request, REQUEST_HEADER);

        try {
            MDC.put("traceId",   traceId);
            MDC.put("requestId", requestId);
            MDC.put("service",   SERVICE_NAME);
            MDC.put("method",    request.getMethod());
            MDC.put("uri",       request.getRequestURI());

            // Echo trace header downstream so callers can correlate
            response.setHeader(TRACE_HEADER,   traceId);
            response.setHeader(REQUEST_HEADER, requestId);

            long start = System.currentTimeMillis();
            chain.doFilter(request, response);
            long elapsed = System.currentTimeMillis() - start;

            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsed);

        } finally {
            // Always clear MDC — prevents data leaking to next request on the same thread
            MDC.clear();
        }
    }

    /** Returns existing header value, or generates a fresh UUID. */
    private String resolveHeader(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return (value != null && !value.isBlank()) ? value : UUID.randomUUID().toString();
    }
}
