package com.resumeai.payment.controller;

import com.resumeai.payment.entity.CreditTransaction;
import com.resumeai.payment.entity.Payment;
import com.resumeai.payment.entity.UserCredits;
import com.resumeai.payment.repository.CreditTransactionRepository;
import com.resumeai.payment.repository.PaymentRepository;
import com.resumeai.payment.repository.UserCreditsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for payment and credit management.
 *
 * Security note: Payment-service has no Spring Security on the classpath.
 * Role enforcement is done at two levels:
 *   1. API Gateway: rejects requests to /api/payment/admin/** if X-Roles does not contain ADMIN.
 *   2. This controller reads X-Username and X-Roles headers (injected by the gateway)
 *      and validates the ADMIN role manually before processing each request.
 *
 * All routes: /api/payment/admin/**
 */
@Slf4j
@RestController
@RequestMapping("/api/payment/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Payments", description = "Endpoints for viewing system revenue and transactions")
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;
    private final UserCreditsRepository userCreditsRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    // ── Guard helper ──────────────────────────────────────────────────────────

    /**
     * Returns true when the caller has the ADMIN role (forwarded by the gateway).
     * This is a belt-and-suspenders check; the gateway already blocks non-admins.
     */
    private boolean isAdmin(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) return false;
        for (String r : rolesHeader.split(",")) {
            String trimmed = r.trim();
            if ("ADMIN".equalsIgnoreCase(trimmed) || "ROLE_ADMIN".equalsIgnoreCase(trimmed)) return true;
        }
        return false;
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("message", "Access denied: ADMIN role required"));
    }

    // ── Payment Endpoints ─────────────────────────────────────────────────────

    /**
     * GET /api/payment/admin/payments
     * List all payments across all users.
     */
    @Operation(summary = "List all payments", description = "Admin endpoint to list all system payments.")
    @ApiResponse(responseCode = "200", description = "Payments retrieved successfully")
    @GetMapping("/payments")
    public ResponseEntity<?> getAllPayments(
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        if (!isAdmin(roles)) return forbidden();
        List<Payment> all = paymentRepository.findAll();
        log.info("Admin fetched all {} payment records", all.size());
        return ResponseEntity.ok(all);
    }

    /**
     * GET /api/payment/admin/payments/{id}
     * Get a specific payment by its DB id.
     */
    @Operation(summary = "Get payment by ID", description = "Get a specific payment record.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping("/payments/{id}")
    public ResponseEntity<?> getPaymentById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        if (!isAdmin(roles)) return forbidden();
        return paymentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/payment/admin/payments/user/{username}
     * List all payments for a specific user.
     */
    @Operation(summary = "Get user payments", description = "List all payments for a specific user.")
    @ApiResponse(responseCode = "200", description = "Payments retrieved successfully")
    @GetMapping("/payments/user/{username}")
    public ResponseEntity<?> getPaymentsByUser(
            @PathVariable String username,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        if (!isAdmin(roles)) return forbidden();
        List<Payment> payments = paymentRepository.findByUsernameOrderByCreatedAtDesc(username);
        return ResponseEntity.ok(payments);
    }

    // ── Credit Endpoints ──────────────────────────────────────────────────────

    /**
     * GET /api/payment/admin/credits
     * List credits for all users.
     */
    @Operation(summary = "List all user credits", description = "List current credit balances for all users.")
    @ApiResponse(responseCode = "200", description = "Credits retrieved successfully")
    @GetMapping("/credits")
    public ResponseEntity<?> getAllCredits(
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        if (!isAdmin(roles)) return forbidden();
        List<UserCredits> all = userCreditsRepository.findAll();
        log.info("Admin fetched credits for {} users", all.size());
        return ResponseEntity.ok(all);
    }

    /**
     * GET /api/payment/admin/credits/{username}
     * Get credits for a specific user.
     */
    @Operation(summary = "Get user credits", description = "Get credits for a specific user.")
    @ApiResponse(responseCode = "200", description = "Credits retrieved successfully")
    @GetMapping("/credits/{username}")
    public ResponseEntity<?> getCreditsByUser(
            @PathVariable String username,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        if (!isAdmin(roles)) return forbidden();
        return userCreditsRepository.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/payment/admin/credits/{username}/adjust
     * Manually add or subtract credits from a user.
     * Body: { "amount": 10, "reason": "promotional grant" }
     * Use a negative amount to deduct credits.
     */
    @Operation(summary = "Adjust user credits", description = "Manually add or deduct credits from a user.")
    @ApiResponse(responseCode = "200", description = "Credits adjusted successfully")
    @PostMapping("/credits/{username}/adjust")
    public ResponseEntity<?> adjustCredits(
            @PathVariable String username,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Username", required = false) String adminUsername,
            @RequestHeader(value = "X-Roles", required = false) String roles) {

        if (!isAdmin(roles)) return forbidden();

        int amount;
        try {
            amount = Integer.parseInt(body.get("amount").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid 'amount' field"));
        }
        String reason = body.getOrDefault("reason", "Admin adjustment").toString();

        UserCredits credits = userCreditsRepository.findByUsername(username).orElseGet(() -> {
            UserCredits c = new UserCredits();
            c.setUsername(username);
            c.setTotalCredits(0);
            c.setUsedCredits(0);
            c.setRemainingCredits(0);
            return c;
        });

        if (amount > 0) {
            credits.addCredits(amount);
        } else {
            // Deduction — clamp remaining to 0
            int deduct = Math.abs(amount);
            int newRemaining = Math.max(0, credits.getRemainingCredits() - deduct);
            credits.setUsedCredits(credits.getUsedCredits() + (credits.getRemainingCredits() - newRemaining));
            credits.setRemainingCredits(newRemaining);
        }

        userCreditsRepository.save(credits);

        // Record admin transaction for audit trail
        CreditTransaction tx = new CreditTransaction();
        tx.setUsername(username);
        tx.setCredits(Math.abs(amount));
        tx.setType(CreditTransaction.TransactionType.ADJUSTMENT);
        tx.setDescription("Admin adjustment by " + adminUsername + ": " + reason);
        tx.setReferenceId("admin-" + adminUsername);
        tx.setBalanceBefore(credits.getRemainingCredits() + (amount < 0 ? Math.abs(amount) : 0) - (amount > 0 ? amount : 0));
        tx.setBalanceAfter(credits.getRemainingCredits());
        creditTransactionRepository.save(tx);

        log.info("Admin '{}' adjusted credits for '{}' by {} ({})", adminUsername, username, amount, reason);

        return ResponseEntity.ok(Map.of(
                "username", username,
                "adjustment", amount,
                "remainingCredits", credits.getRemainingCredits(),
                "reason", reason
        ));
    }

    // ── Stats Endpoint ────────────────────────────────────────────────────────

    /**
     * GET /api/payment/admin/stats
     * High-level payment statistics.
     */
    @Operation(summary = "Get payment statistics", description = "High-level overall payment statistics and totals.")
    @ApiResponse(responseCode = "200", description = "Stats retrieved successfully")
    @GetMapping("/stats")
    public ResponseEntity<?> getPaymentStats(
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        if (!isAdmin(roles)) return forbidden();

        List<Payment> allPayments = paymentRepository.findAll();
        long totalPayments = allPayments.size();
        long successfulPayments = allPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED).count();
        long pendingPayments = allPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING).count();
        long failedPayments = allPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.FAILED).count();
        long totalRevenuePaise = allPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .mapToLong(Payment::getAmount).sum();

        long totalUsers = userCreditsRepository.count();

        return ResponseEntity.ok(Map.of(
                "totalPayments", totalPayments,
                "successfulPayments", successfulPayments,
                "pendingPayments", pendingPayments,
                "failedPayments", failedPayments,
                "totalRevenueINR", totalRevenuePaise / 100.0,
                "totalUsersWithCredits", totalUsers
        ));
    }
}
