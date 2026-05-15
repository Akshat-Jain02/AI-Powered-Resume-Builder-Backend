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
    private final UserBloomFilterService bloomFilterService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userAuthRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public UserRegistrationDTO save(UserRegistrationDTO dto) {
        // 1. Fast-fail check with Bloom Filter
        if (bloomFilterService.mightContainUsername(dto.getUsername()) && 
            userAuthRepository.existsByUsername(dto.getUsername())) {
            throw new UserAlreadyExistsException("Username already taken: " + dto.getUsername());
        }
        
        if (dto.getEmail() != null && !dto.getEmail().isBlank() && 
            bloomFilterService.mightContainEmail(dto.getEmail()) && 
            userAuthRepository.existsByEmail(dto.getEmail())) {
            throw new UserAlreadyExistsException("Email already registered: " + dto.getEmail());
        }

        List<String> roles = new ArrayList<>(List.of("USER"));

        UserAuthEntity entity = new UserAuthEntity();
        entity.setUsername(dto.getUsername());
        entity.setEmail(dto.getEmail());
        entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        entity.setRoles(roles);
        log.info("Persisting new user record for: {}", dto.getUsername());
        userAuthRepository.save(entity);

        // Update Bloom Filter
        log.debug("Updating Bloom Filter with new user: {} / {}", dto.getUsername(), dto.getEmail());
        bloomFilterService.add(dto.getUsername(), dto.getEmail());

        // Publish Kafka event — triggers welcome email in notification-service (best-effort)
        try {
            eventPublisher.publishUserRegistered(dto.getUsername(), dto.getEmail());
        } catch (Exception _) {
            log.warn("Kafka publish failed for USER_REGISTERED (non-critical)");
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
                            log.info("Creating new OAuth2 user record for: {}", email);
                            UserAuthEntity saved = userAuthRepository.save(u);
                            bloomFilterService.add(email, email);

                            // Publish Kafka event for OAuth2 registrations too
                            try {
                                log.debug("Publishing USER_REGISTERED event to Kafka for OAuth2 user: {}", email);
                                eventPublisher.publishUserRegistered(email, email);
                                log.info("Successfully published USER_REGISTERED event for: {}", email);
                            } catch (Exception e) {
                                log.warn("Kafka publish failed for OAuth2 USER_REGISTERED (non-critical): {}", e.getMessage());
                            }

                            return saved;
                        }));
    }
}
