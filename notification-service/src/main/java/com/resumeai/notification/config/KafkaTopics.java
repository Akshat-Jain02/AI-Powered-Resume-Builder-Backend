package com.resumeai.notification.config;

/**
 * Central registry of all Kafka topic names used across ResumeAI microservices.
 * Import this class in both producers (ai-service, payment-service, auth-service)
 * and the notification-service consumer.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // Payment topics
    public static final String PAYMENT_SUCCESS        = "resumeai.payment.success";
    public static final String PAYMENT_FAILED         = "resumeai.payment.failed";
    public static final String CREDIT_DEDUCTED        = "resumeai.credit.deducted";
    public static final String LOW_CREDITS_ALERT      = "resumeai.credit.low";

    // AI / Analysis topics
    public static final String ATS_SCORE_RESULT       = "resumeai.ats.score.result";
    public static final String RESUME_ANALYSIS_RESULT = "resumeai.resume.analysis.result";

    // Auth topics
    public static final String USER_REGISTERED        = "resumeai.user.registered";
}
