package com.resumeai.notification.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private MimeMessage mimeMessage;

    @InjectMocks private NotificationEmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@resumeai.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void sendWelcomeEmail_sendsWithoutException() {
        assertThatCode(() ->
            emailService.sendWelcomeEmail("alice@example.com", "alice")
        ).doesNotThrowAnyException();
    }

    @Test
    void sendPaymentSuccessEmail_invokesMailSender() {
        emailService.sendPaymentSuccessEmail(
            "alice@example.com", "alice", "Pro Plan",
            25, 30, 599.0, "ORDER_123", "pay_abc"
        );
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPaymentFailedEmail_invokesMailSender() {
        emailService.sendPaymentFailedEmail(
            "alice@example.com", "alice", "Pro Plan",
            "ORDER_123", "Signature mismatch"
        );
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendAtsScoreEmail_highScore_sendsOk() {
        emailService.sendAtsScoreEmail(
            "alice@example.com", "alice", "resume.pdf",
            "85", List.of("Strong skills"), List.of("Add certifications")
        );
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendAtsScoreEmail_lowScore_sendsOk() {
        emailService.sendAtsScoreEmail(
            "alice@example.com", "alice", "resume.pdf",
            "40", List.of("Weak formatting"), List.of("Use keywords")
        );
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendResumeAnalysisEmail_invokesMailSender() {
        emailService.sendResumeAnalysisEmail(
            "alice@example.com", "alice", "resume.pdf",
            "Strong candidate", List.of("Java"), List.of("Add projects"),
            List.of("Spring"), "85", "Software Engineer"
        );
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendCreditDeductedEmail_sufficientCredits_sendsOk() {
        emailService.sendCreditDeductedEmail(
            "alice@example.com", "alice", "AI Analysis", 1, 9
        );
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendCreditDeductedEmail_lowCredits_sendsWarning() {
        emailService.sendCreditDeductedEmail(
            "alice@example.com", "alice", "ATS Score", 1, 1
        );
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendLowCreditsAlert_invokesMailSender() {
        emailService.sendLowCreditsAlert("alice@example.com", "alice", 2);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPaymentSuccessEmail_mailSenderFails_doesNotPropagate() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP down"));

        assertThatCode(() ->
            emailService.sendPaymentSuccessEmail(
                "alice@example.com", "alice", "Pro", 10, 10, 299.0, "ORD", "PAY"
            )
        ).doesNotThrowAnyException();
    }

    @Test
    void sendAtsScoreEmail_emptyLists_doesNotThrow() {
        assertThatCode(() ->
            emailService.sendAtsScoreEmail(
                "alice@example.com", "alice", "resume.pdf",
                "60", null, null
            )
        ).doesNotThrowAnyException();
    }
}
