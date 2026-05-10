package com.resumeai.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreditsResponse {
    private String username;
    private Integer totalCredits;
    private Integer usedCredits;
    private Integer remainingCredits;
}
