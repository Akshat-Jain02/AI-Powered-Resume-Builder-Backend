package com.resumeai.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.resumeai.notification.dto.NotificationEvents.*;

import com.resumeai.notification.email.NotificationEmailService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock private NotificationEmailService emailService;
    @InjectMocks private NotificationConsumer consumer;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private Acknowledgment ack;

    @BeforeEach
    void setUp() {
        ack = mock(Acknowledgment.class);
    }

    private ConsumerRecord<String, Object> record(Object value) {
        // Simulate Kafka deserializing as a LinkedHashMap (default JSON deserialization)
        try {
            String json = mapper.writeValueAsString(value);
            Object mapValue = mapper.readValue(json, Map.class);
            return new ConsumerRecord<>("test-topic", 0, 0L, "key", mapValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handlePaymentSuccess_withEmail_sendsEmail() {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .username("alice").userEmail("alice@example.com")
                .planName("Pro").creditsGranted(25).totalCredits(30)
                .amountPaid(599.0).orderId("ORD_123").razorpayPaymentId("pay_abc")
                .build();

        consumer.handlePaymentSuccess(record(event), ack);

        verify(emailService).sendPaymentSuccessEmail(
                eq("alice@example.com"), eq("alice"), eq("Pro"),
                eq(25), eq(30), eq(599.0), eq("ORD_123"), eq("pay_abc")
        );
        verify(ack).acknowledge();
    }

    @Test
    void handlePaymentSuccess_noEmail_skipsAndAcknowledges() {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .username("alice").userEmail(null).planName("Pro").build();

        consumer.handlePaymentSuccess(record(event), ack);

        verify(emailService, never()).sendPaymentSuccessEmail(any(), any(), any(), anyInt(), anyInt(), anyDouble(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void handlePaymentSuccess_blankEmail_skips() {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .username("alice").userEmail("  ").planName("Pro").build();

        consumer.handlePaymentSuccess(record(event), ack);

        verify(emailService, never()).sendPaymentSuccessEmail(any(), any(), any(), anyInt(), anyInt(), anyDouble(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void handlePaymentFailed_withEmail_sendsEmail() {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .username("alice").userEmail("alice@example.com")
                .planName("Pro").orderId("ORD_123").failureReason("Invalid sig")
                .build();

        consumer.handlePaymentFailed(record(event), ack);

        verify(emailService).sendPaymentFailedEmail(
                eq("alice@example.com"), eq("alice"), eq("Pro"),
                eq("ORD_123"), eq("Invalid sig")
        );
        verify(ack).acknowledge();
    }

    @Test
    void handlePaymentFailed_noEmail_skips() {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .username("alice").userEmail("").planName("Pro").build();

        consumer.handlePaymentFailed(record(event), ack);

        verify(emailService, never()).sendPaymentFailedEmail(any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void handleCreditDeducted_withEmail_sendsEmail() {
        CreditDeductedEvent event = CreditDeductedEvent.builder()
                .username("alice").userEmail("alice@example.com")
                .feature("AI Analysis").creditsUsed(1).remainingCredits(9)
                .build();

        consumer.handleCreditDeducted(record(event), ack);

        verify(emailService).sendCreditDeductedEmail(
                eq("alice@example.com"), eq("alice"), eq("AI Analysis"), eq(1), eq(9)
        );
        verify(ack).acknowledge();
    }

    @Test
    void handleCreditDeducted_noEmail_skips() {
        CreditDeductedEvent event = CreditDeductedEvent.builder()
                .username("alice").userEmail(null).feature("AI Analysis").build();

        consumer.handleCreditDeducted(record(event), ack);

        verify(emailService, never()).sendCreditDeductedEmail(any(), any(), any(), anyInt(), anyInt());
        verify(ack).acknowledge();
    }

    @Test
    void handleLowCredits_withEmail_sendsAlert() {
        LowCreditsEvent event = LowCreditsEvent.builder()
                .username("alice").userEmail("alice@example.com").remainingCredits(2)
                .build();

        consumer.handleLowCredits(record(event), ack);

        verify(emailService).sendLowCreditsAlert("alice@example.com", "alice", 2);
        verify(ack).acknowledge();
    }

    @Test
    void handleLowCredits_noEmail_skips() {
        LowCreditsEvent event = LowCreditsEvent.builder()
                .username("alice").userEmail("").remainingCredits(2).build();

        consumer.handleLowCredits(record(event), ack);

        verify(emailService, never()).sendLowCreditsAlert(any(), any(), anyInt());
        verify(ack).acknowledge();
    }

    @Test
    void handleUserRegistered_withEmail_sendsWelcome() {
        var event = com.resumeai.notification.dto.NotificationEvents.UserRegisteredEvent.builder()
                .username("alice").userEmail("alice@example.com")
                .build();

        consumer.handleUserRegistered(record(event), ack);

        verify(emailService).sendWelcomeEmail("alice@example.com", "alice");
        verify(ack).acknowledge();
    }

    @Test
    void handleUserRegistered_noEmail_skips() {
        var event = com.resumeai.notification.dto.NotificationEvents.UserRegisteredEvent.builder()
                .username("alice").userEmail(null).build();

        consumer.handleUserRegistered(record(event), ack);

        verify(emailService, never()).sendWelcomeEmail(any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void handleAtsScore_withEmail_sendsEmail() {
        var event = com.resumeai.notification.dto.NotificationEvents.AtsScoreEvent.builder()
                .username("alice").userEmail("alice@example.com")
                .fileName("resume.pdf").atsScore("85")
                .feedback(List.of("Good")).suggestions(List.of("Add certs"))
                .build();

        consumer.handleAtsScore(record(event), ack);

        verify(emailService).sendAtsScoreEmail(
                eq("alice@example.com"), eq("alice"), eq("resume.pdf"),
                eq("85"), anyList(), anyList()
        );
        verify(ack).acknowledge();
    }

    @Test
    void handleResumeAnalysis_withEmail_sendsEmail() {
        var event = com.resumeai.notification.dto.NotificationEvents.ResumeAnalysisEvent.builder()
                .username("alice").userEmail("alice@example.com")
                .fileName("resume.pdf").summary("Strong candidate")
                .strengths(List.of("Java")).improvements(List.of("Add ML"))
                .keywords(List.of("Spring")).overallScore("90").targetRole("Engineer")
                .build();

        consumer.handleResumeAnalysis(record(event), ack);

        verify(emailService).sendResumeAnalysisEmail(
                eq("alice@example.com"), eq("alice"), eq("resume.pdf"),
                any(), anyList(), anyList(), anyList(), eq("90"), eq("Engineer")
        );
        verify(ack).acknowledge();
    }

    @Test
    void handlePaymentSuccess_emailServiceThrows_stillAcknowledges() {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .username("alice").userEmail("alice@example.com")
                .planName("Pro").creditsGranted(25).totalCredits(30)
                .amountPaid(599.0).orderId("ORD_123").razorpayPaymentId("pay_abc")
                .build();

        doThrow(new RuntimeException("SMTP down")).when(emailService)
                .sendPaymentSuccessEmail(any(), any(), any(), anyInt(), anyInt(), anyDouble(), any(), any());

        consumer.handlePaymentSuccess(record(event), ack);

        verify(ack).acknowledge(); // Must still ack even on failure
    }
}
