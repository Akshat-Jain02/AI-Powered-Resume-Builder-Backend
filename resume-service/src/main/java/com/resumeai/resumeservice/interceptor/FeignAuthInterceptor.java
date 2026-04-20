package com.resumeai.resumeservice.interceptor;

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

        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs == null) return;

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
    }
}
