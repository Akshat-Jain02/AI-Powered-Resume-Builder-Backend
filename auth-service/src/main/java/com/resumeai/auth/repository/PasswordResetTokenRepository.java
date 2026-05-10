package com.resumeai.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.resumeai.auth.entity.PasswordResetToken;
import com.resumeai.auth.entity.UserAuthEntity;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findByUserAuthEntity(UserAuthEntity user);
}
