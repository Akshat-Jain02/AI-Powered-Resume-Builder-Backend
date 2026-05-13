package com.resumeai.payment.exception;

/**
 * Custom exception for payment security violations.
 */
public class PaymentSecurityException extends RuntimeException {
    public PaymentSecurityException(String message) {
        super(message);
    }
}
