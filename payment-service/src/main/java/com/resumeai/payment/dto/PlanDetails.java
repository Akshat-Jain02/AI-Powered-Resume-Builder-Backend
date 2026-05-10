package com.resumeai.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanDetails {
    private String type;
    private String name;
    private String description;
    private Integer price;
    private Integer credits;
}
