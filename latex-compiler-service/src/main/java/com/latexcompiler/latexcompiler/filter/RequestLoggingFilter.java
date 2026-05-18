package com.latexcompiler.latexcompiler.filter;

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
 * RequestLoggingFilter — runs first on every HTTP request for latex-compiler-service.
 *
 * Populates MDC with:
 *   traceId   — X-Trace-Id header (from API Gateway) or newly generated UUID
 *   requestId — unique UUID for this specific request leg
 *   service   — application name
 */
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER   = "X-Trace-Id";
    private static final String REQUEST_HEADER = "X-Request-Id";
    private static final String SERVICE_NAME   = "latex-compiler-service";

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String currentTraceId = resolveHeader(request, TRACE_HEADER);
        final String currentRequestId = UUID.randomUUID().toString(); // Always generate new ID for current leg
        final String parentRequestId = request.getHeader(REQUEST_HEADER);

        try {
            MDC.put("traceId", currentTraceId);
            MDC.put("requestId", currentRequestId);
            MDC.put("service", SERVICE_NAME);
            MDC.put("method", request.getMethod());
            MDC.put("uri", request.getRequestURI());
            if (parentRequestId != null && !parentRequestId.isBlank()) {
                MDC.put("parentRequestId", parentRequestId);
            }

            response.setHeader(TRACE_HEADER, currentTraceId);
            response.setHeader(REQUEST_HEADER, currentRequestId);

            final long startTime = System.currentTimeMillis();
            filterChain.doFilter(request, response);
            final long totalTime = System.currentTimeMillis() - startTime;

            log.info("LATEX-COMPILER: {} {} -> {} in {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    totalTime);

        } finally {
            MDC.clear();
        }
    }

    private String resolveHeader(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return (value != null && !value.isBlank()) ? value : UUID.randomUUID().toString();
    }
}
