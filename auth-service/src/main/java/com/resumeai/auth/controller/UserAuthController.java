package com.resumeai.auth.controller;

import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import com.resumeai.auth.dto.request.UserLoginDTO;
import com.resumeai.auth.dto.request.UserRegistrationDTO;
import com.resumeai.auth.dto.response.JwtResponseDTO;
import com.resumeai.auth.entity.PasswordResetToken;
import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.repository.UserAuthRepository;
import com.resumeai.auth.service.PasswordResetService;
import com.resumeai.auth.service.UserAuthService;
import com.resumeai.auth.util.JwtUtil;

import lombok.AllArgsConstructor;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and password management")
public class UserAuthController {

    private final UserAuthRepository userAuthRepository;
    private UserAuthService userAuthService;
    private AuthenticationManager authenticationManager;
    private JwtUtil jwtUtil;
    private PasswordEncoder passwordEncoder;
    private PasswordResetService passwordResetService;

    @Operation(summary = "Register a new user", description = "Creates a new user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error or username/email already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserRegistrationDTO userRegistrationDTO) {
        log.info("Registering new user: {}", userRegistrationDTO.getUsername());
        userAuthService.save(userRegistrationDTO);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @Operation(summary = "User Login", description = "Authenticates a user and returns a JWT Bearer token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful, returns JWT token"),
            @ApiResponse(responseCode = "401", description = "Invalid username or password")
    })
    @PostMapping("/signin")
    public ResponseEntity<?> login(@Valid @RequestBody UserLoginDTO userLoginDTO) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    userLoginDTO.getUsername(),
                    userLoginDTO.getPassword()
                )
            );

            List<String> roles = authentication.getAuthorities().stream()
                    .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                    .toList();

            // Fetch email from DB so it can be embedded in the JWT claim
            String email = userAuthRepository.findByUsername(userLoginDTO.getUsername())
                    .map(u -> u.getEmail()).orElse(null);

            String token = jwtUtil.generateToken(userLoginDTO.getUsername(), email, roles);
            log.info("Login successful for user: {}", userLoginDTO.getUsername());

            JwtResponseDTO jwtResponseDTO = new JwtResponseDTO();
            jwtResponseDTO.setToken(token);
            jwtResponseDTO.setHttpStatus(HttpStatus.OK);
            return ResponseEntity.ok(jwtResponseDTO);

        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for user: {}", userLoginDTO.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid username or password"));
        }
    }

    /** Lightweight endpoint for frontend to verify the token is valid */
    @Operation(summary = "Get current user profile", description = "Returns the currently authenticated user's username.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved user profile")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated"));
        }
        return ResponseEntity.ok(Map.of("username", authentication.getName()));
    }

    @Operation(summary = "Forgot Password", description = "Generates a password reset token and emails it to the user.")
    @ApiResponse(responseCode = "200", description = "Reset link sent if the email exists")
    @ApiResponse(responseCode = "400", description = "Email is required")
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> map) {
        String email = map.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }
        try {
            passwordResetService.forgotPassword(email);
        } catch (Exception e) {
            // Don't reveal whether the email exists — always return 200
        }
        return ResponseEntity.ok(Map.of("message", "If that email exists, a reset link has been sent"));
    }

    @Operation(summary = "Reset Password", description = "Resets user password using a valid reset token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated successfully"),
            @ApiResponse(responseCode = "400", description = "Token is invalid, expired, or password is too short")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestParam String token,
            @RequestBody Map<String, String> request) {

        PasswordResetToken resetToken;
        try {
            resetToken = passwordResetService.findByToken(token);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token is invalid or expired"));
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token is invalid or expired"));
        }

        String newPassword = request.get("password");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 6 characters"));
        }

        UserAuthEntity user = resetToken.getUserAuthEntity();
        user.setPassword(passwordEncoder.encode(newPassword));
        userAuthRepository.save(user);
        passwordResetService.deleteToken(resetToken);

        log.info("Password reset successfully for user: {}", user.getUsername());
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }
}
