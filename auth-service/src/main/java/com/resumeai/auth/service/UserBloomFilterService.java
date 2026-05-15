package com.resumeai.auth.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.resumeai.auth.repository.UserAuthRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Service to manage In-Memory Bloom Filters for quick username and email uniqueness checks.
 * Uses Google Guava implementation for fast, probabilistic lookups.
 * Note: This is instance-local. In a distributed environment, use Redis Bloom.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserBloomFilterService {

    private final UserAuthRepository userAuthRepository;

    // Expected insertions: 1,000,000 users. False positive probability: 1%.
    private BloomFilter<String> usernameFilter;
    private BloomFilter<String> emailFilter;

    @PostConstruct
    public void init() {
        log.info("Initializing In-Memory Bloom Filters...");

        // 1. Create filters
        usernameFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                1000000,
                0.01);

        emailFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                1000000,
                0.01);

        // 2. Hydrate from database
        long start = System.currentTimeMillis();
        List<String> usernames = userAuthRepository.findAllUsernames();
        usernames.forEach(u -> usernameFilter.put(u.toLowerCase()));

        List<String> emails = userAuthRepository.findAllEmails();
        emails.forEach(e -> emailFilter.put(e.toLowerCase()));

        log.info("In-Memory Bloom Filters initialized in {} ms. Loaded {} usernames and {} emails.",
                (System.currentTimeMillis() - start), usernames.size(), emails.size());
    }

    /**
     * Checks if the username might already exist.
     * @return true if it MIGHT exist (fast-fail), false if it definitely DOES NOT exist.
     */
    public boolean mightContainUsername(String username) {
        if (username == null || username.isBlank()) return false;
        return usernameFilter.mightContain(username.toLowerCase());
    }

    /**
     * Checks if the email might already exist.
     * @return true if it MIGHT exist (fast-fail), false if it definitely DOES NOT exist.
     */
    public boolean mightContainEmail(String email) {
        if (email == null || email.isBlank()) return false;
        return emailFilter.mightContain(email.toLowerCase());
    }

    /**
     * Adds a new username and email to the filters.
     */
    public void add(String username, String email) {
        if (username != null && !username.isBlank()) {
            usernameFilter.put(username.toLowerCase());
        }
        if (email != null && !email.isBlank()) {
            emailFilter.put(email.toLowerCase());
        }
    }
}
