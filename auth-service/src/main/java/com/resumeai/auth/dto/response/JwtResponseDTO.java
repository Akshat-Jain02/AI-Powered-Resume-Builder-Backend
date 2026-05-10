package com.resumeai.auth.dto.response;

import java.util.List;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class JwtResponseDTO {
    private String token;
    private String type = "Bearer";
    private HttpStatus httpStatus;

    // constructor, getters
}