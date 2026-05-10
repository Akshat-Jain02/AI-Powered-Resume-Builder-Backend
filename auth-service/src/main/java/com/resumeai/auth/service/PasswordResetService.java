package com.resumeai.auth.service;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.resumeai.auth.entity.PasswordResetToken;
import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.exception.TokenInvalidException;
import com.resumeai.auth.repository.PasswordResetTokenRepository;
import com.resumeai.auth.repository.UserAuthRepository;

import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final JavaMailSender mailSender;
    private final UserAuthRepository userAuthRepository;
    private final PasswordResetTokenRepository tokenRepo;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public void forgotPassword(String email) {
        UserAuthEntity user = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found for email: " + email));

        // Delete any existing token for this user
        tokenRepo.findByUserAuthEntity(user).ifPresent(tokenRepo::delete);

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUserAuthEntity(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(15));
        tokenRepo.save(resetToken);

        String resetLink = frontendUrl + "/reset-password?token=" + token;
        sendEmail(user.getEmail(), resetLink);
    }

    private void sendEmail(String to, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("ResumeAI — Reset Your Password");
        message.setText(
            "Hello,\n\n" +
            "You requested a password reset for your ResumeAI account.\n\n" +
            "Click the link below to set a new password (valid for 15 minutes):\n" +
            link + "\n\n" +
            "If you didn't request this, you can safely ignore this email.\n\n" +
            "— The ResumeAI Team"
        );
        mailSender.send(message);
    }

    public PasswordResetToken findByToken(String token) {
        return tokenRepo.findByToken(token)
                .orElseThrow(() -> new TokenInvalidException("Token is invalid or has expired"));
    }

    public void deleteToken(PasswordResetToken token) {
        tokenRepo.delete(token);
    }
}
