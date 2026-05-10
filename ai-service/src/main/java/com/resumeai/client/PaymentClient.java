package com.resumeai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @GetMapping("/api/payment/credits/check")
    Map<String, Boolean> checkCredits(
        @RequestHeader("X-Username") String username,
        @RequestParam(defaultValue = "1") int required
    );

    @PostMapping("/api/payment/credits/use")
    Map<String, Object> useCredits(
        @RequestHeader("X-Username") String username,
        @RequestHeader(value = "X-User-Email", required = false) String userEmail,
        @RequestParam(defaultValue = "1") int credits,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) String referenceId
    );
}
