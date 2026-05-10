package com.resumeai.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Safe projection of UserAuthEntity for admin API responses.
 * Never exposes the password hash.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserDTO {

    private int id;
    private String username;
    private String email;
    private List<String> roles;
    private boolean active;
}
