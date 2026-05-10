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
 * Service to manage Bloom Filters for quick username and email uniqueness checks.
 * Industry standard for optimizing high-throughput registration endpoints.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserBloomFilterService {

    private final UserAuthRepository userAuthRepository;

    // Configured for up to 1 million users with a 0.01% false positive rate
    private BloomFilter<String> usernameFilter;
    private BloomFilter<String> emailFilter;

    @PostConstruct
    public void init() {
        log.info("Initializing User Bloom Filters...");
        usernameFilter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 1_000_000, 0.0001);
        emailFilter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 1_000_000, 0.0001);

        long start = System.currentTimeMillis();

        // Load all existing usernames
        List<String> usernames = userAuthRepository.findAllUsernames();
        usernames.forEach(usernameFilter::put);

        // Load all existing emails
        List<String> emails = userAuthRepository.findAllEmails();
        emails.forEach(emailFilter::put);

        log.info("Bloom Filters initialized in {} ms. Loaded {} usernames and {} emails.",
                (System.currentTimeMillis() - start), usernames.size(), emails.size());
    }

    /**
     * Checks if a username might already exist.
     * @return false if DEFINITELY unique, true if it MIGHT exist (requires DB fallback check).
     */
    public boolean mightContainUsername(String username) {
        if (username == null || username.isBlank()) return false;
        return usernameFilter.mightContain(username.toLowerCase());
    }

    /**
     * Checks if an email might already exist.
     * @return false if DEFINITELY unique, true if it MIGHT exist (requires DB fallback check).
     */
    public boolean mightContainEmail(String email) {
        if (email == null || email.isBlank()) return false;
        return emailFilter.mightContain(email.toLowerCase());
    }

    /**
     * Add a newly registered user to the Bloom Filters.
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
