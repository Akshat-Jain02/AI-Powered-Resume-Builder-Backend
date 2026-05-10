package com.resumeai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        log.warn("Validation failed: {}", errors);
        return new ResponseEntity<>(new ErrorResponse("Validation Failed", HttpStatus.BAD_REQUEST.value(), LocalDateTime.now(), errors), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse("Malformed JSON request body", HttpStatus.BAD_REQUEST.value(), LocalDateTime.now()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return new ResponseEntity<>(new ErrorResponse("HTTP method '" + ex.getMethod() + "' is not supported", HttpStatus.METHOD_NOT_ALLOWED.value(), LocalDateTime.now()), HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.warn("Max upload size exceeded: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse("File too large", HttpStatus.PAYLOAD_TOO_LARGE.value(), LocalDateTime.now()), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(org.springframework.web.reactive.function.client.WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientError(org.springframework.web.reactive.function.client.WebClientResponseException ex) {
        log.error("WebClient error: {}", ex.getMessage());
        String message = ex.getStatusCode().value() == 429 ? "Rate limit exceeded" : ex.getMessage();
        return new ResponseEntity<>(new ErrorResponse(message, ex.getStatusCode().value(), LocalDateTime.now()), ex.getStatusCode());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value(), LocalDateTime.now()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled runtime exception: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value(), LocalDateTime.now()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(new ErrorResponse("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR.value(), LocalDateTime.now()), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
