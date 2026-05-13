package com.resumeai.templateservice.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(org.springframework.boot.web.client.RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(java.time.Duration.ofSeconds(5))
                .setReadTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }

    @Bean
    @org.springframework.cloud.client.loadbalancer.LoadBalanced
    public RestTemplate loadBalancedRestTemplate(org.springframework.boot.web.client.RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(java.time.Duration.ofSeconds(3))
                .setReadTimeout(java.time.Duration.ofSeconds(10))
                .build();

        restTemplate.getInterceptors().add((request, body, execution) -> {
            String traceId = org.slf4j.MDC.get("traceId");
            String requestId = org.slf4j.MDC.get("requestId");

            if (traceId != null && !traceId.isBlank()) {
                request.getHeaders().add("X-Trace-Id", traceId);
            }
            if (requestId != null && !requestId.isBlank()) {
                request.getHeaders().add("X-Request-Id", requestId);
            }

            // Forward X-Username and X-Roles headers from current HTTP request or SecurityContext
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            
            String username = null;
            String roles = null;

            if (attrs != null) {
                jakarta.servlet.http.HttpServletRequest httpRequest = attrs.getRequest();
                username = httpRequest.getHeader("X-Username");
                roles = httpRequest.getHeader("X-Roles");
            }

            // Fallback to SecurityContext if not found in headers
            if (username == null || username.isBlank()) {
                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                    username = auth.getName();
                    roles = auth.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                        .collect(java.util.stream.Collectors.joining(","));
                }
            }

            if (username != null && !username.isBlank()) {
                request.getHeaders().add("X-Username", username);
            }
            if (roles != null && !roles.isBlank()) {
                request.getHeaders().add("X-Roles", roles);
            }

            return execution.execute(request, body);
        });
        return restTemplate;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public Cloudinary cloudinary(
            @org.springframework.beans.factory.annotation.Value("${cloudinary.cloud_name}") String cloudName,
            @org.springframework.beans.factory.annotation.Value("${cloudinary.api_key}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${cloudinary.api_secret}") String apiSecret) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }
    // CORS is handled entirely by SecurityConfig — no WebMvcConfigurer needed here
}
