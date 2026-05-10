package com.resumeai.auth.exception;

public class TokenInvalidException extends RuntimeException{

	public TokenInvalidException(String msg) {
		super(msg);
	}
}
