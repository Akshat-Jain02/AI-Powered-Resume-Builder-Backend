package com.resumeai.filter;

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
    private static final String SERVICE_NAME   = "ai-service";

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Use helper to resolve IDs
        String traceId = getHeaderOrDefault(request, TRACE_HEADER);
        String requestId = getHeaderOrDefault(request, REQUEST_HEADER);

        try {
            populateMdc(traceId, requestId);

            response.setHeader(TRACE_HEADER, traceId);
            response.setHeader(REQUEST_HEADER, requestId);

            long startTime = System.currentTimeMillis();
            filterChain.doFilter(request, response);
            long duration = System.currentTimeMillis() - startTime;

            log.info("AI-REQ: {} {} completed in {}ms with status {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    duration,
                    response.getStatus());

        } finally {
            MDC.clear();
        }
    }

    private void populateMdc(String tId, String rId) {
        MDC.put("traceId", tId);
        MDC.put("requestId", rId);
        MDC.put("service", SERVICE_NAME);
        MDC.put("method", "AI_EXEC"); // Differentiate method
    }

    private String getHeaderOrDefault(HttpServletRequest req, String head) {
        String val = req.getHeader(head);
        return (val == null || val.isBlank()) ? UUID.randomUUID().toString() : val;
    }
}
