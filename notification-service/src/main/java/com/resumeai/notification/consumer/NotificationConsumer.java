package com.resumeai.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.resumeai.notification.config.KafkaTopics;
import com.resumeai.notification.dto.NotificationEvents.*;
import com.resumeai.notification.email.NotificationEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationEmailService emailService;
    private final ObjectMapper objectMapper = buildObjectMapper();

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }

    // ──────────────────────────────────────────────────────────────
    // PAYMENT SUCCESS
    // ──────────────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentSuccess(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            log.info("Received PAYMENT_SUCCESS event from partition {} offset {}", record.partition(), record.offset());
            PaymentSuccessEvent event = convert(record.value(), PaymentSuccessEvent.class);

            if (event.getUserEmail() == null || event.getUserEmail().isBlank()) {
                log.warn("PAYMENT_SUCCESS event has no email for user={}; skipping notification", event.getUsername());
                ack.acknowledge();
                return;
            }

            emailService.sendPaymentSuccessEmail(
                event.getUserEmail(),
                event.getUsername(),
                event.getPlanName(),
                event.getCreditsGranted(),
                event.getTotalCredits(),
                event.getAmountPaid(),
                event.getOrderId(),
                event.getRazorpayPaymentId()
            );
            ack.acknowledge();
            log.info("Payment success email sent to {}", event.getUserEmail());
        } catch (Exception e) {
            log.error("Failed to process PAYMENT_SUCCESS event: {}", e.getMessage(), e);
            ack.acknowledge(); // Acknowledge to avoid infinite retry on bad message
        }
    }

    // ──────────────────────────────────────────────────────────────
    // PAYMENT FAILED
    // ──────────────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentFailed(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            log.info("Received PAYMENT_FAILED event");
            PaymentFailedEvent event = convert(record.value(), PaymentFailedEvent.class);

            if (event.getUserEmail() == null || event.getUserEmail().isBlank()) {
                log.warn("PAYMENT_FAILED event has no email for user={}; skipping notification", event.getUsername());
                ack.acknowledge();
                return;
            }

            emailService.sendPaymentFailedEmail(
                event.getUserEmail(),
                event.getUsername(),
                event.getPlanName(),
                event.getOrderId(),
                event.getFailureReason()
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process PAYMENT_FAILED event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CREDIT DEDUCTED
    // ──────────────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.CREDIT_DEDUCTED, groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handleCreditDeducted(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            log.info("Received CREDIT_DEDUCTED event");
            CreditDeductedEvent event = convert(record.value(), CreditDeductedEvent.class);

            if (event.getUserEmail() == null || event.getUserEmail().isBlank()) {
                log.warn("CREDIT_DEDUCTED event has no email for user={}; skipping notification", event.getUsername());
                ack.acknowledge();
                return;
            }

            emailService.sendCreditDeductedEmail(
                event.getUserEmail(),
                event.getUsername(),
                event.getFeature(),
                event.getCreditsUsed(),
                event.getRemainingCredits()
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process CREDIT_DEDUCTED event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // LOW CREDITS ALERT
    // ──────────────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.LOW_CREDITS_ALERT, groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handleLowCredits(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            log.info("Received LOW_CREDITS_ALERT event");
            LowCreditsEvent event = convert(record.value(), LowCreditsEvent.class);

            if (event.getUserEmail() == null || event.getUserEmail().isBlank()) {
                log.warn("LOW_CREDITS_ALERT event has no email for user={}; skipping notification", event.getUsername());
                ack.acknowledge();
                return;
            }

            emailService.sendLowCreditsAlert(
                event.getUserEmail(),
                event.getUsername(),
                event.getRemainingCredits()
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process LOW_CREDITS_ALERT event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // ATS SCORE RESULT
    // ──────────────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.ATS_SCORE_RESULT, groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handleAtsScore(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            log.info("Received ATS_SCORE_RESULT event");
            AtsScoreEvent event = convert(record.value(), AtsScoreEvent.class);

            if (event.getUserEmail() == null || event.getUserEmail().isBlank()) {
                log.warn("ATS_SCORE_RESULT event has no email for user={}; skipping notification", event.getUsername());
                ack.acknowledge();
                return;
            }

            emailService.sendAtsScoreEmail(
                event.getUserEmail(),
                event.getUsername(),
                event.getFileName(),
                event.getAtsScore(),
                event.getFeedback(),
                event.getSuggestions()
            );
            ack.acknowledge();
            log.info("ATS score email sent to {}", event.getUserEmail());
        } catch (Exception e) {
            log.error("Failed to process ATS_SCORE_RESULT event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // RESUME ANALYSIS RESULT
    // ──────────────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.RESUME_ANALYSIS_RESULT, groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handleResumeAnalysis(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            log.info("Received RESUME_ANALYSIS_RESULT event");
            ResumeAnalysisEvent event = convert(record.value(), ResumeAnalysisEvent.class);

            if (event.getUserEmail() == null || event.getUserEmail().isBlank()) {
                log.warn("RESUME_ANALYSIS_RESULT event has no email for user={}; skipping notification", event.getUsername());
                ack.acknowledge();
                return;
            }

            emailService.sendResumeAnalysisEmail(
                event.getUserEmail(),
                event.getUsername(),
                event.getFileName(),
                event.getSummary(),
                event.getStrengths(),
                event.getImprovements(),
                event.getKeywords(),
                event.getOverallScore(),
                event.getTargetRole()
            );
            ack.acknowledge();
            log.info("Resume analysis email sent to {}", event.getUserEmail());
        } catch (Exception e) {
            log.error("Failed to process RESUME_ANALYSIS_RESULT event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // USER REGISTERED
    // ──────────────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.USER_REGISTERED, groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handleUserRegistered(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            log.info("Received USER_REGISTERED event");
            UserRegisteredEvent event = convert(record.value(), UserRegisteredEvent.class);

            if (event.getUserEmail() == null || event.getUserEmail().isBlank()) {
                log.warn("USER_REGISTERED event has no email for user={}; skipping welcome email", event.getUsername());
                ack.acknowledge();
                return;
            }

            emailService.sendWelcomeEmail(event.getUserEmail(), event.getUsername());
            ack.acknowledge();
            log.info("Welcome email sent to {}", event.getUserEmail());
        } catch (Exception e) {
            log.error("Failed to process USER_REGISTERED event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Helper: convert Map/LinkedHashMap from Kafka → typed DTO
    // ──────────────────────────────────────────────────────────────

    private <T> T convert(Object value, Class<T> targetType) {
        return objectMapper.convertValue(value, targetType);
    }
}
