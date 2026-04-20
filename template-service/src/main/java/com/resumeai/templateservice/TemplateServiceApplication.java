package com.resumeai.templateservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
public class TemplateServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TemplateServiceApplication.class, args);
    }
}
