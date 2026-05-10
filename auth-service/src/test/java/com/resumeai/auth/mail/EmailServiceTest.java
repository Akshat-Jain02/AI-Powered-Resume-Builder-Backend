package com.resumeai.auth.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@resumeai.com");
    }

    @Test
    void sendSimpleEmail_success_invokesMailSender() {
        emailService.sendSimpleEmail("alice@example.com", "Test Subject", "Test Body");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSimpleEmail_mailSenderFails_doesNotPropagate() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThatCode(() -> emailService.sendSimpleEmail("bad@fail.com", "Subject", "Body"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendPasswordResetEmail_invokesMailSender() {
        emailService.sendPasswordResetEmail("alice@example.com", "http://localhost:5173/reset?token=abc");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSimpleEmail_setsCorrectRecipient() {
        emailService.sendSimpleEmail("recipient@example.com", "Hello", "World");
        verify(mailSender).send(argThat((SimpleMailMessage msg) ->
                msg.getTo() != null && msg.getTo()[0].equals("recipient@example.com")
        ));
    }

    @Test
    void sendSimpleEmail_setsCorrectSubject() {
        emailService.sendSimpleEmail("alice@example.com", "Important Subject", "body text");
        verify(mailSender).send(argThat((SimpleMailMessage msg) ->
                "Important Subject".equals(msg.getSubject())
        ));
    }
}
