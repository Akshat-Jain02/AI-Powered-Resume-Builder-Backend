package com.resumeai.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes AI analysis results (ATS score, resume feedback) to Kafka.
 * All sends are best-effort — never throw to callers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── ATS Score Result ───────────────────────────────────────────

    public void publishAtsScore(String username, String userEmail, String fileName,
                                String atsScore, List<String> feedback, List<String> suggestions) {
        if (userEmail == null || userEmail.isBlank()) return;

        Map<String, Object> event = new HashMap<>();
        event.put("username",    username);
        event.put("userEmail",   userEmail);
        event.put("fileName",    fileName);
        event.put("atsScore",    atsScore);
        event.put("feedback",    feedback);
        event.put("suggestions", suggestions);
        event.put("analyzedAt",  LocalDateTime.now().toString());

        send(KafkaTopics.ATS_SCORE_RESULT, username, event);
        log.info("Published ATS_SCORE_RESULT for user={} score={}", username, atsScore);
    }

    // ── Resume Analysis Result ─────────────────────────────────────

    public void publishResumeAnalysis(String username, String userEmail, String fileName,
                                      String summary, List<String> strengths,
                                      List<String> improvements, List<String> keywords,
                                      String overallScore, String targetRole) {
        if (userEmail == null || userEmail.isBlank()) return;

        Map<String, Object> event = new HashMap<>();
        event.put("username",     username);
        event.put("userEmail",    userEmail);
        event.put("fileName",     fileName);
        event.put("summary",      summary);
        event.put("strengths",    strengths);
        event.put("improvements", improvements);
        event.put("keywords",     keywords);
        event.put("overallScore", overallScore);
        event.put("targetRole",   targetRole);
        event.put("analyzedAt",   LocalDateTime.now().toString());

        send(KafkaTopics.RESUME_ANALYSIS_RESULT, username, event);
        log.info("Published RESUME_ANALYSIS_RESULT for user={} score={}", username, overallScore);
    }

    // ── Internal send ──────────────────────────────────────────────

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
            log.error("Kafka send threw exception for topic={}: {}", topic, e.getMessage());
        }
    }
}
