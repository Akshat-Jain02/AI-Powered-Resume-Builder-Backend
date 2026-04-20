package com.resumeai.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private String razorpayOrderId;
    private Integer amount;
    private String currency;
    private String key;
    private String planType;
    private Integer credits;
    private String companyName;
    private String companyLogo;
    private String userName;
    private String userEmail;
}
