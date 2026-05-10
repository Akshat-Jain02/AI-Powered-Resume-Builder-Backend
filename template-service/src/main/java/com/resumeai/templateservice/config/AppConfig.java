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
    @org.springframework.cloud.client.loadbalancer.LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
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
