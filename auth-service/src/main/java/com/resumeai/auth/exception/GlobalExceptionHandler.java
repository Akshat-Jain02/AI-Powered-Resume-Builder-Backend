package com.resumeai.auth.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	  @ExceptionHandler(UserAlreadyExistsException.class)
	    public ResponseEntity<ErrorResponse> handleUserExists(UserAlreadyExistsException ex) {

	        ErrorResponse error = new ErrorResponse(
	                ex.getMessage(),
	                HttpStatus.CONFLICT.value(),
	                LocalDateTime.now()
	        );

	        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
	    }
	  
	  @ExceptionHandler(TokenInvalidException.class)
	  public ResponseEntity<ErrorResponse> handleWrongToken(TokenInvalidException ex) {
		  
		   ErrorResponse error = new ErrorResponse(
	                ex.getMessage(),
	                HttpStatus.CONFLICT.value(),
	                LocalDateTime.now()
	        );
		   
		   return new ResponseEntity<>(error, HttpStatus.CONFLICT);
	  }

	  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
	  public ResponseEntity<java.util.Map<String, Object>> handleValidationExceptions(
			  org.springframework.web.bind.MethodArgumentNotValidException ex) {
		  java.util.Map<String, Object> response = new java.util.HashMap<>();
		  response.put("status", HttpStatus.BAD_REQUEST.value());
		  response.put("message", "Validation Failed");

		  java.util.Map<String, String> errors = new java.util.HashMap<>();
		  ex.getBindingResult().getFieldErrors().forEach(error ->
				  errors.put(error.getField(), error.getDefaultMessage()));

		  response.put("errors", errors);
		  return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
	  }
}
