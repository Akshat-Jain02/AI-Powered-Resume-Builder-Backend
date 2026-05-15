package com.resumeai.auth.service;

import com.resumeai.auth.dto.request.UserRegistrationDTO;
import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.exception.UserAlreadyExistsException;
import com.resumeai.auth.kafka.AuthEventPublisher;
import com.resumeai.auth.repository.UserAuthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceTest {

    @Mock private UserAuthRepository userAuthRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthEventPublisher eventPublisher;

    @InjectMocks private UserAuthService userAuthService;

    private UserRegistrationDTO dto;
    private UserAuthEntity entity;

    @BeforeEach
    void setUp() {
        dto = new UserRegistrationDTO("testuser", "password123", "test@example.com");

        entity = new UserAuthEntity();
        entity.setUsername("testuser");
        entity.setEmail("test@example.com");
        entity.setPassword("encoded_password");
        entity.setRoles(List.of("USER"));
    }

    @Test
    void loadUserByUsername_found() {
        when(userAuthRepository.findByUsername("testuser")).thenReturn(Optional.of(entity));
        assertThat(userAuthService.loadUserByUsername("testuser")).isEqualTo(entity);
    }

    @Test
    void loadUserByUsername_notFound_throws() {
        when(userAuthRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userAuthService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void save_success() {
        when(passwordEncoder.encode("password123")).thenReturn("encoded_password");

        UserRegistrationDTO result = userAuthService.save(dto);

        assertThat(result).isNotNull();
        verify(userAuthRepository).save(any(UserAuthEntity.class));
        verify(eventPublisher).publishUserRegistered("testuser", "test@example.com");
    }

    @Test
    void save_duplicateUsername_throws() {
        when(userAuthRepository.existsByUsername("testuser")).thenReturn(true);
        assertThatThrownBy(() -> userAuthService.save(dto))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Username already taken");
    }

    @Test
    void save_duplicateEmail_throws() {
        when(userAuthRepository.existsByUsername("testuser")).thenReturn(false);
        when(userAuthRepository.existsByEmail("test@example.com")).thenReturn(true);
        assertThatThrownBy(() -> userAuthService.save(dto))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void save_nullEmail_success() {
        dto.setEmail(null);
        when(userAuthRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        
        userAuthService.save(dto);
        verify(userAuthRepository).save(any());
    }

    @Test
    void save_blankEmail_success() {
        dto.setEmail("  ");
        when(userAuthRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        
        userAuthService.save(dto);
        verify(userAuthRepository).save(any());
    }

    @Test
    void save_alwaysAssignsRoleUser() {
        // Roles field is no longer in DTO, service should assign USER automatically
        when(userAuthRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        userAuthService.save(dto);
        verify(userAuthRepository).save(argThat(e -> e.getRoles().contains("USER")));
    }

    @Test
    void save_kafkaFailure_doesNotPropagateException() {
        when(userAuthRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        doThrow(new RuntimeException("Kafka down")).when(eventPublisher)
                .publishUserRegistered(anyString(), anyString());

        assertThatCode(() -> userAuthService.save(dto)).doesNotThrowAnyException();
    }

    @Test
    void findOrCreateUser_existsByEmail() {
        when(userAuthRepository.findByEmail("test@example.com")).thenReturn(Optional.of(entity));
        UserAuthEntity result = userAuthService.findOrCreateUser("test@example.com");
        assertThat(result).isEqualTo(entity);
        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void findOrCreateUser_existsByUsername() {
        when(userAuthRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userAuthRepository.findByUsername("test@example.com")).thenReturn(Optional.of(entity));
        UserAuthEntity result = userAuthService.findOrCreateUser("test@example.com");
        assertThat(result).isEqualTo(entity);
    }

    @Test
    void findOrCreateUser_createsNew_whenNotFound() {
        when(userAuthRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userAuthRepository.findByUsername("new@example.com")).thenReturn(Optional.empty());
        when(userAuthRepository.save(any(UserAuthEntity.class))).thenReturn(entity);

        UserAuthEntity result = userAuthService.findOrCreateUser("new@example.com");
        assertThat(result).isNotNull();
        verify(userAuthRepository).save(any(UserAuthEntity.class));
    }

    @Test
    void findOrCreateUser_kafkaFailure_doesNotPropagate() {
        when(userAuthRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userAuthRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userAuthRepository.save(any())).thenReturn(entity);
        doThrow(new RuntimeException("Kafka down")).when(eventPublisher).publishUserRegistered(anyString(), anyString());

        assertThatCode(() -> userAuthService.findOrCreateUser("new@example.com")).doesNotThrowAnyException();
    }
}
