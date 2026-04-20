package com.resumeai.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shared Kafka event DTOs used across all services.
 * Producers serialize these to JSON; the notification-service consumer deserializes them.
 */
@Data
public class NotificationEvents {

    // ──────────────────────────────────────────────────────────────
    // PAYMENT EVENTS
    // ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentSuccessEvent {
        private String username;
        private String userEmail;
        private String orderId;
        private String planName;
        private String planType;
        private int creditsGranted;
        private int totalCredits;
        private double amountPaid;      // in INR (converted from paise)
        private String currency;
        private String razorpayPaymentId;
        private LocalDateTime paidAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentFailedEvent {
        private String username;
        private String userEmail;
        private String orderId;
        private String planName;
        private String failureReason;
        private LocalDateTime failedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreditDeductedEvent {
        private String username;
        private String userEmail;
        private String feature;          // e.g. "AI Resume Analysis", "ATS Score"
        private int creditsUsed;
        private int remainingCredits;
        private LocalDateTime usedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LowCreditsEvent {
        private String username;
        private String userEmail;
        private int remainingCredits;
        private LocalDateTime checkedAt;
    }

    // ──────────────────────────────────────────────────────────────
    // AI / RESUME ANALYSIS EVENTS
    // ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtsScoreEvent {
        private String username;
        private String userEmail;
        private String fileName;
        private String atsScore;
        private List<String> feedback;
        private List<String> suggestions;
        private LocalDateTime analyzedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResumeAnalysisEvent {
        private String username;
        private String userEmail;
        private String fileName;
        private String summary;
        private List<String> strengths;
        private List<String> improvements;
        private List<String> keywords;
        private String overallScore;
        private String targetRole;
        private LocalDateTime analyzedAt;
    }

    // ──────────────────────────────────────────────────────────────
    // AUTH EVENTS
    // ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserRegisteredEvent {
        private String username;
        private String userEmail;
        private String firstName;
        private LocalDateTime registeredAt;
    }
}
