package com.resumeai.templateservice.exception;

public class TemplateServiceException extends RuntimeException {
    public TemplateServiceException(String message) {
        super(message);
    }

    public TemplateServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
