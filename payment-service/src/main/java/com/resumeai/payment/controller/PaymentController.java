package com.resumeai.payment.controller;

import com.razorpay.RazorpayException;
import com.resumeai.payment.dto.*;
import com.resumeai.payment.entity.CreditTransaction;
import com.resumeai.payment.entity.Payment;
import com.resumeai.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Endpoints for creating and verifying Razorpay payment links")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Get all available plans
     */
    @Operation(summary = "Get All Plans", description = "Returns all available payment/credit plans.")
    @ApiResponse(responseCode = "200", description = "Plans retrieved successfully")
    @GetMapping("/plans")
    public ResponseEntity<List<PlanDetails>> getAllPlans() {
        return ResponseEntity.ok(paymentService.getAllPlans());
    }

    /**
     * Create payment order
     */
    @Operation(summary = "Create Payment Order", description = "Creates a new Razorpay order for a selected plan.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid plan type"),
            @ApiResponse(responseCode = "500", description = "Failed to create order")
    })
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @RequestHeader("X-Username") String username,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @Valid @RequestBody CreateOrderRequest request) {
        try {
            OrderResponse response = paymentService.createOrder(username, userEmail, request);
            return ResponseEntity.ok(response);
        } catch (RazorpayException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create order: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create order"));
        }
    }

    /**
     * Verify payment and grant credits
     */
    @Operation(summary = "Verify Payment", description = "Verifies a Razorpay payment signature and grants credits to the user.")
    @ApiResponse(responseCode = "200", description = "Payment verified successfully (or failed verification object)")
    @PostMapping("/verify")
    public ResponseEntity<PaymentResponse> verifyPayment(
            @RequestHeader("X-Username") String username,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @Valid @RequestBody PaymentVerificationRequest request) {
        try {
            PaymentResponse response = paymentService.verifyAndProcessPayment(username, userEmail, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(PaymentResponse.builder()
                .success(false)
                .message("Payment verification failed: " + e.getMessage())
                .build());
        }
    }

    /**
     * Get user credits
     */
    @Operation(summary = "Get User Credits", description = "Returns the current credit balance of the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Credits retrieved successfully")
    @GetMapping("/credits")
    public ResponseEntity<UserCreditsResponse> getUserCredits(
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.ok(paymentService.getUserCredits(username));
    }

    /**
     * Check if user has sufficient credits (used by AI service)
     */
    @Operation(summary = "Check Sufficient Credits", description = "Checks if the user has enough credits to perform an action (Internal use by AI Service).")
    @ApiResponse(responseCode = "200", description = "Check executed successfully")
    @GetMapping("/credits/check")
    public ResponseEntity<Map<String, Boolean>> checkCredits(
            @RequestHeader("X-Username") String username,
            @RequestParam(defaultValue = "1") int required) {
        boolean hasCredits = paymentService.hasCredits(username, required);
        return ResponseEntity.ok(Map.of("hasCredits", hasCredits));
    }

    /**
     * Use credits (called by AI service)
     */
    @Operation(summary = "Use Credits", description = "Deducts credits from the user's balance (Internal use by AI Service).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credits used successfully"),
            @ApiResponse(responseCode = "402", description = "Insufficient credits")
    })
    @PostMapping("/credits/use")
    public ResponseEntity<Map<String, Object>> useCredits(
            @RequestHeader("X-Username") String username,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestParam(defaultValue = "1") int credits,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String referenceId) {
        
        boolean success = paymentService.useCredits(
            username, 
            userEmail,
            credits, 
            description != null ? description : "AI Service Usage",
            referenceId
        );

        if (success) {
            UserCreditsResponse creditsResponse = paymentService.getUserCredits(username);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Credits used successfully",
                "remainingCredits", creditsResponse.getRemainingCredits()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
                "success", false,
                "message", "Insufficient credits"
            ));
        }
    }

    /**
     * Get payment history
     */
    @Operation(summary = "Get Payment History", description = "Returns the user's past payment orders.")
    @ApiResponse(responseCode = "200", description = "History retrieved successfully")
    @GetMapping("/history")
    public ResponseEntity<List<Payment>> getPaymentHistory(
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(username));
    }

    /**
     * Get transaction history
     */
    @Operation(summary = "Get Transaction History", description = "Returns a detailed ledger of the user's credit usage/additions.")
    @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully")
    @GetMapping("/transactions")
    public ResponseEntity<List<CreditTransaction>> getTransactionHistory(
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.ok(paymentService.getTransactionHistory(username));
    }

    /**
     * Health check
     */
    @Operation(summary = "Health Check", description = "Returns the service status.")
    @ApiResponse(responseCode = "200", description = "Service is UP")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "payment-service"
        ));
    }
}
