package com.resumeai.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    
    @NotBlank(message = "Plan type is required")
    @Pattern(regexp = "^(BASIC|PRO|PREMIUM)$", message = "Plan type must be BASIC, PRO, or PREMIUM")
    private String planType; // BASIC, PRO, PREMIUM
}
