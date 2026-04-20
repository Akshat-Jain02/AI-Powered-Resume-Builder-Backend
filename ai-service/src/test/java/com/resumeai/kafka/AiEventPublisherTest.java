package com.resumeai.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks private AiEventPublisher publisher;

    @Test
    void publishAtsScore_validEmail_sendsEvent() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        publisher.publishAtsScore("alice", "alice@test.com", "resume.pdf",
                "85", List.of("Good format"), List.of("Add more keywords"));

        verify(kafkaTemplate).send(eq(KafkaTopics.ATS_SCORE_RESULT), eq("alice"), any());
    }

    @Test
    void publishAtsScore_nullEmail_skips() {
        publisher.publishAtsScore("alice", null, "file.pdf", "80", List.of(), List.of());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publishAtsScore_blankEmail_skips() {
        publisher.publishAtsScore("alice", "   ", "file.pdf", "80", List.of(), List.of());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publishResumeAnalysis_validEmail_sendsEvent() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        publisher.publishResumeAnalysis("bob", "bob@test.com", "resume.pdf",
                "Good resume", List.of("Strong skills"), List.of("Add projects"),
                List.of("Java", "Spring"), "8/10", "Software Engineer");

        verify(kafkaTemplate).send(eq(KafkaTopics.RESUME_ANALYSIS_RESULT), eq("bob"), any());
    }

    @Test
    void publishResumeAnalysis_nullEmail_skips() {
        publisher.publishResumeAnalysis("bob", null, "file.pdf",
                "summary", List.of(), List.of(), List.of(), "7", "Dev");
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publishResumeAnalysis_blankEmail_skips() {
        publisher.publishResumeAnalysis("bob", "", "file.pdf",
                "summary", List.of(), List.of(), List.of(), "7", "Dev");
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publishAtsScore_kafkaThrows_doesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));

        // Should not throw
        publisher.publishAtsScore("alice", "a@b.com", "f.pdf", "90", List.of(), List.of());
    }

    @Test
    void publishResumeAnalysis_kafkaThrows_doesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));

        publisher.publishResumeAnalysis("bob", "b@c.com", "f.pdf",
                "s", List.of(), List.of(), List.of(), "7", "Dev");
    }
}
