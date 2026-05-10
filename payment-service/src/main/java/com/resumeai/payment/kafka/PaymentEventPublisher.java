package com.resumeai.payment.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes payment and credit events to Kafka topics.
 * All sends are fire-and-forget with async callbacks for logging.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Payment Success ────────────────────────────────────────────

    public void publishPaymentSuccess(String username, String userEmail,
                                      String orderId, String planName, String planType,
                                      int creditsGranted, int totalCredits,
                                      double amountPaid, String razorpayPaymentId) {
        Map<String, Object> event = new HashMap<>();
        event.put("username",          username);
        event.put("userEmail",         userEmail);
        event.put("orderId",           orderId);
        event.put("planName",          planName);
        event.put("planType",          planType);
        event.put("creditsGranted",    creditsGranted);
        event.put("totalCredits",      totalCredits);
        event.put("amountPaid",        amountPaid);
        event.put("currency",          "INR");
        event.put("razorpayPaymentId", razorpayPaymentId);
        event.put("paidAt",            LocalDateTime.now().toString());

        send(KafkaTopics.PAYMENT_SUCCESS, username, event);
        log.info("Published PAYMENT_SUCCESS for user={} orderId={}", username, orderId);
    }

    // ── Payment Failed ─────────────────────────────────────────────

    public void publishPaymentFailed(String username, String userEmail,
                                     String orderId, String planName, String failureReason) {
        Map<String, Object> event = new HashMap<>();
        event.put("username",      username);
        event.put("userEmail",     userEmail);
        event.put("orderId",       orderId);
        event.put("planName",      planName);
        event.put("failureReason", failureReason);
        event.put("failedAt",      LocalDateTime.now().toString());

        send(KafkaTopics.PAYMENT_FAILED, username, event);
        log.info("Published PAYMENT_FAILED for user={} orderId={}", username, orderId);
    }

    // ── Credit Deducted ────────────────────────────────────────────

    public void publishCreditDeducted(String username, String userEmail,
                                      String feature, int creditsUsed, int remainingCredits) {
        Map<String, Object> event = new HashMap<>();
        event.put("username",         username);
        event.put("userEmail",        userEmail);
        event.put("feature",          feature);
        event.put("creditsUsed",      creditsUsed);
        event.put("remainingCredits", remainingCredits);
        event.put("usedAt",           LocalDateTime.now().toString());

        send(KafkaTopics.CREDIT_DEDUCTED, username, event);

        // Also fire a low-credits alert if threshold is reached
        if (remainingCredits <= 3) {
            publishLowCreditsAlert(username, userEmail, remainingCredits);
        }
    }

    // ── Low Credits Alert ──────────────────────────────────────────

    public void publishLowCreditsAlert(String username, String userEmail, int remainingCredits) {
        Map<String, Object> event = new HashMap<>();
        event.put("username",         username);
        event.put("userEmail",        userEmail);
        event.put("remainingCredits", remainingCredits);
        event.put("checkedAt",        LocalDateTime.now().toString());

        send(KafkaTopics.LOW_CREDITS_ALERT, username, event);
        log.info("Published LOW_CREDITS_ALERT for user={} remaining={}", username, remainingCredits);
    }

    // ── Internal send with callback ────────────────────────────────

    private void send(String topic, String key, Object payload) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, key, payload);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send Kafka event to topic={} key={}: {}", topic, key, ex.getMessage());
                } else {
                    log.debug("Kafka event sent topic={} partition={} offset={}",
                              topic,
                              result.getRecordMetadata().partition(),
                              result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            // Never let Kafka failures break the payment flow
            log.error("Kafka send threw exception for topic={}: {}", topic, e.getMessage());
        }
    }
}
