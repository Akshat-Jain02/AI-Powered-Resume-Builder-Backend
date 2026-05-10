package com.resumeai.auth.service;

import com.resumeai.auth.entity.PasswordResetToken;
import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.exception.TokenInvalidException;
import com.resumeai.auth.repository.PasswordResetTokenRepository;
import com.resumeai.auth.repository.UserAuthRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private UserAuthRepository userAuthRepository;
    @Mock private PasswordResetTokenRepository tokenRepo;

    @InjectMocks private PasswordResetService passwordResetService;

    @Test
    void forgotPassword_userFound_savesTokenAndSendsEmail() {
        UserAuthEntity user = new UserAuthEntity();
        user.setEmail("alice@example.com");

        when(userAuthRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepo.findByUserAuthEntity(user)).thenReturn(Optional.empty());

        passwordResetService.forgotPassword("alice@example.com");

        verify(tokenRepo).save(any(PasswordResetToken.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void forgotPassword_userNotFound_throws() {
        when(userAuthRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> passwordResetService.forgotPassword("unknown@example.com"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void forgotPassword_deletesExistingToken() {
        UserAuthEntity user = new UserAuthEntity();
        user.setEmail("alice@example.com");
        PasswordResetToken existingToken = new PasswordResetToken();

        when(userAuthRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepo.findByUserAuthEntity(user)).thenReturn(Optional.of(existingToken));

        passwordResetService.forgotPassword("alice@example.com");

        verify(tokenRepo).delete(existingToken);
        verify(tokenRepo).save(any(PasswordResetToken.class));
    }

    @Test
    void findByToken_found() {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("valid-token");
        when(tokenRepo.findByToken("valid-token")).thenReturn(Optional.of(token));

        PasswordResetToken result = passwordResetService.findByToken("valid-token");
        assertThat(result).isEqualTo(token);
    }

    @Test
    void findByToken_notFound_throws() {
        when(tokenRepo.findByToken("invalid")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> passwordResetService.findByToken("invalid"))
                .isInstanceOf(TokenInvalidException.class);
    }

    @Test
    void deleteToken_callsRepository() {
        PasswordResetToken token = new PasswordResetToken();
        passwordResetService.deleteToken(token);
        verify(tokenRepo).delete(token);
    }
}
