package com.resumeai.resumeservice.exception;

/**
 * Custom exception for Resume Service operations.
 */
public class ResumeServiceException extends RuntimeException {
    public ResumeServiceException(String message) {
        super(message);
    }

    public ResumeServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
