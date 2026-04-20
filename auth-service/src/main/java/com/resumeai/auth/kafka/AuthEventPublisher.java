package com.resumeai.auth.kafka;

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
 * Publishes auth events to Kafka.
 * Currently: USER_REGISTERED → triggers welcome email in notification-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserRegistered(String username, String email) {
        if (email == null || email.isBlank()) {
            log.debug("Skipping USER_REGISTERED event — no email for user={}", username);
            return;
        }

        Map<String, Object> event = new HashMap<>();
        event.put("username",       username);
        event.put("userEmail",      email);
        event.put("firstName",      username);   // use username as display name fallback
        event.put("registeredAt",   LocalDateTime.now().toString());

        send(KafkaTopics.USER_REGISTERED, username, event);
        log.info("Published USER_REGISTERED for user={} email={}", username, email);
    }

    private void send(String topic, String key, Object payload) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, key, payload);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Kafka send failed topic={} key={}: {}", topic, key, ex.getMessage());
                } else {
                    log.debug("Kafka sent topic={} partition={} offset={}",
                              topic,
                              result.getRecordMetadata().partition(),
                              result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            // Never let Kafka failure break the registration flow
            log.error("Kafka send threw exception for topic={}: {}", topic, e.getMessage());
        }
    }
}
