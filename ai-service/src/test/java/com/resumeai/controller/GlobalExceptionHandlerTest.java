package com.resumeai.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleMaxUploadSize_returns413() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10_000_000L);
        ResponseEntity<Map<String, String>> response = handler.handleMaxUploadSize(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void handleWebClientError_rateLimitedBy429_returns429() {
        WebClientResponseException ex = WebClientResponseException.create(429, "Too Many Requests",
                null, null, null);
        ResponseEntity<Map<String, String>> response = handler.handleWebClientError(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().get("error")).contains("rate limit");
    }

    @Test
    void handleWebClientError_badRequest400_returns400() {
        WebClientResponseException ex = WebClientResponseException.create(400, "Bad Request",
                null, null, null);
        ResponseEntity<Map<String, String>> response = handler.handleWebClientError(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleWebClientError_forbidden403_returns403() {
        WebClientResponseException ex = WebClientResponseException.create(403, "Forbidden",
                null, null, null);
        ResponseEntity<Map<String, String>> response = handler.handleWebClientError(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleWebClientError_otherError_returns502() {
        WebClientResponseException ex = WebClientResponseException.create(503, "Service Unavailable",
                null, null, null);
        ResponseEntity<Map<String, String>> response = handler.handleWebClientError(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("bad input");
        ResponseEntity<Map<String, String>> response = handler.handleIllegalArgument(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("bad input");
    }

    @Test
    void handleGeneral_returns500() {
        Exception ex = new RuntimeException("unexpected");
        ResponseEntity<Map<String, String>> response = handler.handleGeneral(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsKey("error");
    }
}
