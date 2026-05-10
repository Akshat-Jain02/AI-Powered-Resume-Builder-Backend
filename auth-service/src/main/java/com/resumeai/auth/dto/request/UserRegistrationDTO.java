package com.resumeai.auth.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserRegistrationDTO {

	@NotBlank(message = "Username cannot be blank")
	@Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
	private String username;

	@NotBlank(message = "Password cannot be blank")
	@Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
	private String password;

	@NotBlank(message = "Email cannot be blank")
	@Email(message = "Email must be a valid email address")
	private String email;
	
}
