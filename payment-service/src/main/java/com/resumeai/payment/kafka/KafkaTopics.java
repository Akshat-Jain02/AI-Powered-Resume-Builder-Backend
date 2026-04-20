package com.resumeai.payment.kafka;

/**
 * Central registry of all Kafka topic names used across ResumeAI microservices.
 */
public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String PAYMENT_SUCCESS        = "resumeai.payment.success";
    public static final String PAYMENT_FAILED         = "resumeai.payment.failed";
    public static final String CREDIT_DEDUCTED        = "resumeai.credit.deducted";
    public static final String LOW_CREDITS_ALERT      = "resumeai.credit.low";
    public static final String ATS_SCORE_RESULT       = "resumeai.ats.score.result";
    public static final String RESUME_ANALYSIS_RESULT = "resumeai.resume.analysis.result";
    public static final String USER_REGISTERED        = "resumeai.user.registered";
}
