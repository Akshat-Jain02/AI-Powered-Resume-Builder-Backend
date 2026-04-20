package com.resumeai.auth.service;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.resumeai.auth.dto.request.UserRegistrationDTO;
import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.exception.UserAlreadyExistsException;
import com.resumeai.auth.kafka.AuthEventPublisher;
import com.resumeai.auth.repository.UserAuthRepository;

import lombok.RequiredArgsConstructor;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserAuthService implements UserDetailsService {

    private final UserAuthRepository userAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher eventPublisher;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userAuthRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public UserRegistrationDTO save(UserRegistrationDTO dto) {
        if (userAuthRepository.existsByUsername(dto.getUsername())) {
            throw new UserAlreadyExistsException("Username already taken: " + dto.getUsername());
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()
                && userAuthRepository.existsByEmail(dto.getEmail())) {
            throw new UserAlreadyExistsException("Email already registered: " + dto.getEmail());
        }

        List<String> roles = new ArrayList<>(List.of("USER"));

        UserAuthEntity entity = new UserAuthEntity();
        entity.setUsername(dto.getUsername());
        entity.setEmail(dto.getEmail());
        entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        entity.setRoles(roles);
        userAuthRepository.save(entity);

        log.info("Registered new user: {}", dto.getUsername());

        // Publish Kafka event — triggers welcome email in notification-service (best-effort)
        try {
            eventPublisher.publishUserRegistered(dto.getUsername(), dto.getEmail());
        } catch (Exception e) {
            log.warn("Kafka publish failed for USER_REGISTERED (non-critical): {}", e.getMessage());
        }

        return dto;
    }

    /**
     * Find existing OAuth2 user by email, or create one.
     * Always returns a non-null, persisted entity.
     */
    public UserAuthEntity findOrCreateUser(String email) {
        return userAuthRepository.findByEmail(email)
                .orElseGet(() -> userAuthRepository.findByUsername(email)
                        .orElseGet(() -> {
                            UserAuthEntity u = new UserAuthEntity();
                            u.setEmail(email);
                            u.setUsername(email);
                            u.setPassword("");
                            u.setRoles(new ArrayList<>(List.of("USER")));
                            UserAuthEntity saved = userAuthRepository.save(u);

                            // Publish Kafka event for OAuth2 registrations too
                            try {
                                eventPublisher.publishUserRegistered(email, email);
                            } catch (Exception e) {
                                log.warn("Kafka publish failed for OAuth2 USER_REGISTERED: {}", e.getMessage());
                            }

                            return saved;
                        }));
    }
}
