package com.resumeai.payment.kafka;

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
class PaymentEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks private PaymentEventPublisher publisher;

    private CompletableFuture<SendResult<String, Object>> mockFuture() {
        CompletableFuture<SendResult<String, Object>> f = new CompletableFuture<>();
        f.complete(null);
        return f;
    }

    @Test
    void publishPaymentSuccess_sendsEvent() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(mockFuture());

        publisher.publishPaymentSuccess("alice", "alice@test.com", "ORDER_1",
                "Pro", "PRO", 25, 30, 599.0, "pay_123");

        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_SUCCESS), eq("alice"), any());
    }

    @Test
    void publishPaymentFailed_sendsEvent() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(mockFuture());

        publisher.publishPaymentFailed("alice", "alice@test.com", "ORDER_2",
                "Basic", "Invalid signature");

        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_FAILED), eq("alice"), any());
    }

    @Test
    void publishCreditDeducted_noAlert_whenCreditsAboveThreshold() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(mockFuture());

        publisher.publishCreditDeducted("alice", "alice@test.com", "AI Analysis", 1, 10);

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    void publishCreditDeducted_triggersLowAlert_whenCreditsAtOrBelowThreshold() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(mockFuture());

        publisher.publishCreditDeducted("alice", "alice@test.com", "AI Analysis", 1, 3);

        // Should send both CREDIT_DEDUCTED and LOW_CREDITS_ALERT
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
    }

    @Test
    void publishCreditDeducted_triggersLowAlert_whenCreditsZero() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(mockFuture());

        publisher.publishCreditDeducted("alice", "alice@test.com", "AI Analysis", 1, 0);

        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
    }

    @Test
    void publishLowCreditsAlert_sendsEvent() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(mockFuture());

        publisher.publishLowCreditsAlert("alice", "alice@test.com", 2);

        verify(kafkaTemplate).send(eq(KafkaTopics.LOW_CREDITS_ALERT), eq("alice"), any());
    }

    @Test
    void publishPaymentSuccess_kafkaThrows_doesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));

        // Should not throw
        publisher.publishPaymentSuccess("a", "a@b.com", "O1", "P", "T", 1, 1, 1.0, "p1");
    }

    @Test
    void publishPaymentFailed_kafkaThrows_doesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));

        publisher.publishPaymentFailed("a", "a@b.com", "O1", "P", "reason");
    }
}
