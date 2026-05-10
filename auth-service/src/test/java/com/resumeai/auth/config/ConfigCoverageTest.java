package com.resumeai.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class ConfigCoverageTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    void beansArePresent() {
        // This hits the @Bean methods in the config classes
        assertNotNull(applicationContext.getBean(SecurityFilterChain.class));
        assertNotNull(applicationContext.getBean(KafkaTemplate.class));
        assertNotNull(applicationContext.getBean(AppConfig.class));
        assertNotNull(applicationContext.getBean(OpenApiConfig.class));
    }
}
