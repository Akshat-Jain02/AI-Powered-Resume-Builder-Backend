package com.resumeai.auth.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks private AuthEventPublisher publisher;

    @Test
    void publishUserRegistered_validEmail_sendsEvent() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        publisher.publishUserRegistered("alice", "alice@test.com");

        verify(kafkaTemplate).send(eq(KafkaTopics.USER_REGISTERED), eq("alice"), any());
    }

    @Test
    void publishUserRegistered_nullEmail_skips() {
        publisher.publishUserRegistered("alice", null);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publishUserRegistered_blankEmail_skips() {
        publisher.publishUserRegistered("alice", "   ");
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publishUserRegistered_kafkaThrows_doesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));

        // Should not throw
        publisher.publishUserRegistered("alice", "alice@test.com");
    }
}
