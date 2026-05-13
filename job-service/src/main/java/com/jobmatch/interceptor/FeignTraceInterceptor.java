package com.jobmatch.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class FeignTraceInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get("traceId");
        String requestId = MDC.get("requestId");

        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();

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
