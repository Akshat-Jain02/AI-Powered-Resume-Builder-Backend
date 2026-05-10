package com.resumeai.auth.service;

import com.resumeai.auth.repository.UserAuthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBloomFilterServiceTest {

    @Mock
    private UserAuthRepository userAuthRepository;

    @InjectMocks
    private UserBloomFilterService bloomFilterService;

    @BeforeEach
    void setUp() {
        when(userAuthRepository.findAllUsernames()).thenReturn(List.of("existinguser"));
        when(userAuthRepository.findAllEmails()).thenReturn(List.of("existing@example.com"));
        bloomFilterService.init();
    }

    @Test
    void mightContainUsername_found() {
        assertThat(bloomFilterService.mightContainUsername("existinguser")).isTrue();
    }

    @Test
    void mightContainUsername_notFound() {
        assertThat(bloomFilterService.mightContainUsername("newuser")).isFalse();
    }

    @Test
    void mightContainUsername_nullOrBlank() {
        assertThat(bloomFilterService.mightContainUsername(null)).isFalse();
        assertThat(bloomFilterService.mightContainUsername("  ")).isFalse();
    }

    @Test
    void mightContainEmail_found() {
        assertThat(bloomFilterService.mightContainEmail("existing@example.com")).isTrue();
    }

    @Test
    void mightContainEmail_notFound() {
        assertThat(bloomFilterService.mightContainEmail("new@example.com")).isFalse();
    }

    @Test
    void mightContainEmail_nullOrBlank() {
        assertThat(bloomFilterService.mightContainEmail(null)).isFalse();
        assertThat(bloomFilterService.mightContainEmail("  ")).isFalse();
    }

    @Test
    void add_newValues() {
        bloomFilterService.add("addeduser", "added@example.com");
        assertThat(bloomFilterService.mightContainUsername("addeduser")).isTrue();
        assertThat(bloomFilterService.mightContainEmail("added@example.com")).isTrue();
    }
}
