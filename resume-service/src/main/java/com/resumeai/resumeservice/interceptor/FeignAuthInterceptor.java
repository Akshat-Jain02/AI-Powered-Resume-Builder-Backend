package com.resumeai.resumeservice.interceptor;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // Forward X-Trace-Id and X-Request-Id from MDC
        String traceId = MDC.get("traceId");
        String requestId = MDC.get("requestId");

        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();

            // Forward X-Username and X-Roles headers (set by API Gateway JWT filter)
            String username = request.getHeader("X-Username");
            String roles = request.getHeader("X-Roles");

            if (username != null) {
                template.header("X-Username", username);
            }
            if (roles != null) {
                template.header("X-Roles", roles);
            }

            // Fallback for trace headers from incoming request if MDC didn't have them
            if (traceId == null || traceId.isBlank()) {
                traceId = request.getHeader("X-Trace-Id");
            }
            if (requestId == null || requestId.isBlank()) {
                requestId = request.getHeader("X-Request-Id");
            }
        }

        if (traceId != null && !traceId.isBlank()) {
            template.header("X-Trace-Id", traceId);
        }
        if (requestId != null && !requestId.isBlank()) {
            template.header("X-Request-Id", requestId);
        }
    }
}
