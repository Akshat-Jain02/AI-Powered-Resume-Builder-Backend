package com.jobmatch.config;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Logger;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;

public class FeignConfig {

    private final ObjectFactory<HttpMessageConverters> messageConverters;

    public FeignConfig(ObjectFactory<HttpMessageConverters> messageConverters) {
        this.messageConverters = messageConverters;
    }

    // 🔥 COMBINED ENCODER (VERY IMPORTANT)
    @Bean
    public Encoder feignEncoder() {
        return new SpringEncoder(messageConverters);
    }

    @Bean
    public feign.RequestInterceptor requestInterceptor() {
        return requestTemplate -> requestTemplate.header("X-Username", "system-internal");
    }

    // Logging
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}