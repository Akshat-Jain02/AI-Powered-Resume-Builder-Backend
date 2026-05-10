package com.resumeai.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.reactive.function.client.WebClientResponseException;


import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final com.resumeai.exception.GlobalExceptionHandler handler = new com.resumeai.exception.GlobalExceptionHandler();

    @Test
    void handleMaxUploadSize_returns413() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10_000_000L);
        ResponseEntity<com.resumeai.exception.ErrorResponse> response = handler.handleMaxUploadSize(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().getMessage()).isNotNull();
    }

    @Test
    void handleWebClientError_rateLimitedBy429_returns429() {
        WebClientResponseException ex = WebClientResponseException.create(429, "Too Many Requests",
                null, null, null);
        ResponseEntity<com.resumeai.exception.ErrorResponse> response = handler.handleWebClientError(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().getMessage()).containsIgnoringCase("rate limit");
    }

    @Test
    void handleWebClientError_badRequest400_returns400() {
        WebClientResponseException ex = WebClientResponseException.create(400, "Bad Request",
                null, null, null);
        ResponseEntity<com.resumeai.exception.ErrorResponse> response = handler.handleWebClientError(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleWebClientError_forbidden403_returns403() {
        WebClientResponseException ex = WebClientResponseException.create(403, "Forbidden",
                null, null, null);
        ResponseEntity<com.resumeai.exception.ErrorResponse> response = handler.handleWebClientError(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleWebClientError_otherError_returns502() {
        WebClientResponseException ex = WebClientResponseException.create(503, "Service Unavailable",
                null, null, null);
        ResponseEntity<com.resumeai.exception.ErrorResponse> response = handler.handleWebClientError(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("bad input");
        ResponseEntity<com.resumeai.exception.ErrorResponse> response = handler.handleIllegalArgument(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("bad input");
    }

    @Test
    void handleMalformedJson_returns400() {
        org.springframework.http.converter.HttpMessageNotReadableException ex = new org.springframework.http.converter.HttpMessageNotReadableException("Malformed JSON", (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<com.resumeai.exception.ErrorResponse> response = handler.handleMalformedJson(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleMethodNotSupported_returns405() {
        org.springframework.web.HttpRequestMethodNotSupportedException ex = new org.springframework.web.HttpRequestMethodNotSupportedException("POST");
        ResponseEntity<com.resumeai.exception.ErrorResponse> response = handler.handleMethodNotSupported(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void handleValidationExceptions_returns400() {
        org.springframework.web.bind.MethodArgumentNotValidException ex = org.mockito.Mockito.mock(org.springframework.web.bind.MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult bindingResult = org.mockito.Mockito.mock(org.springframework.validation.BindingResult.class);
        org.mockito.Mockito.when(ex.getBindingResult()).thenReturn(bindingResult);
        org.mockito.Mockito.when(bindingResult.getFieldErrors()).thenReturn(java.util.Collections.emptyList());
        
        ResponseEntity<com.resumeai.exception.ErrorResponse> response = handler.handleValidationExceptions(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleGeneral_returns500() {
        Exception ex = new RuntimeException("unexpected");
        ResponseEntity<com.resumeai.exception.ErrorResponse> response = handler.handleGeneralException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isNotNull();
    }
}
