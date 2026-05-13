package com.resumeai.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.resumeai.payment.dto.*;
import com.resumeai.payment.entity.CreditTransaction;
import com.resumeai.payment.entity.Payment;
import com.resumeai.payment.entity.UserCredits;
import com.resumeai.payment.kafka.PaymentEventPublisher;
import com.resumeai.payment.repository.CreditTransactionRepository;
import com.resumeai.payment.repository.PaymentRepository;
import com.resumeai.payment.repository.UserCreditsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final PaymentRepository paymentRepository;
    private final UserCreditsRepository userCreditsRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final PaymentEventPublisher eventPublisher;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.currency:INR}")
    private String currency;

    @Value("${razorpay.company.name:ResumeAI}")
    private String companyName;

    @Value("${razorpay.company.logo:}")
    private String companyLogo;

    @Value("${payment.plans.basic.price}")
    private Integer basicPrice;

    @Value("${payment.plans.basic.credits}")
    private Integer basicCredits;

    @Value("${payment.plans.basic.name}")
    private String basicName;

    @Value("${payment.plans.pro.price}")
    private Integer proPrice;

    @Value("${payment.plans.pro.credits}")
    private Integer proCredits;

    @Value("${payment.plans.pro.name}")
    private String proName;

    @Value("${payment.plans.premium.price}")
    private Integer premiumPrice;

    @Value("${payment.plans.premium.credits}")
    private Integer premiumCredits;

    @Value("${payment.plans.premium.name}")
    private String premiumName;

    public List<PlanDetails> getAllPlans() {
        return Arrays.asList(
            PlanDetails.builder()
                .type("BASIC")
                .name(basicName)
                .description("Perfect for getting started with AI-powered resume analysis")
                .price(basicPrice)
                .credits(basicCredits)
                .build(),
            PlanDetails.builder()
                .type("PRO")
                .name(proName)
                .description("Ideal for job seekers who want comprehensive analysis")
                .price(proPrice)
                .credits(proCredits)
                .build(),
            PlanDetails.builder()
                .type("PREMIUM")
                .name(premiumName)
                .description("Best value for power users and career professionals")
                .price(premiumPrice)
                .credits(premiumCredits)
                .build()
        );
    }

    @Transactional
    public OrderResponse createOrder(String username, String userEmail, CreateOrderRequest request)
            throws RazorpayException {

        PlanDetails plan = getPlanByType(request.getPlanType());
        if (plan == null) throw new IllegalArgumentException("Invalid plan type: " + request.getPlanType());

        int amountInPaise = plan.getPrice() * 100;

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", currency);
        orderRequest.put("receipt", "rcpt_" + UUID.randomUUID().toString().substring(0, 8));

        log.debug("Calling Razorpay API to create order for user: {} | Amount: {} {}", username, amountInPaise, currency);
        Order razorpayOrder = razorpayClient.orders.create(orderRequest);
        String razorpayOrderId = razorpayOrder.get("id");
        log.info("Razorpay order successfully created: {}", razorpayOrderId);

        Payment payment = new Payment();
        payment.setUsername(username);
        payment.setOrderId("ORDER_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setAmount(amountInPaise);
        payment.setCurrency(currency);
        payment.setStatus(Payment.PaymentStatus.CREATED);
        payment.setPlanType(plan.getType());
        payment.setCreditsGranted(plan.getCredits());

        payment = paymentRepository.save(payment);
        log.debug("Internal payment record persisted: ID={}, OrderId={}", payment.getId(), payment.getOrderId());

        return OrderResponse.builder()
            .orderId(payment.getOrderId())
            .razorpayOrderId(razorpayOrderId)
            .amount(amountInPaise)
            .currency(currency)
            .key(razorpayKeyId)
            .planType(plan.getType())
            .credits(plan.getCredits())
            .companyName(companyName)
            .companyLogo(companyLogo)
            .userName(username)
            .userEmail(userEmail)
            .build();
    }

    /**
     * Verify payment and process credits.
     * Fires Kafka PAYMENT_SUCCESS or PAYMENT_FAILED events.
     */
    @Transactional
    public PaymentResponse verifyAndProcessPayment(String username, String userEmail,
                                                   PaymentVerificationRequest request) {

        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + request.getOrderId()));

        if (!payment.getUsername().equals(username))
            throw new com.resumeai.payment.exception.PaymentSecurityException("Payment does not belong to user: " + username);

        PlanDetails plan = getPlanByType(payment.getPlanType());
        String planName = plan != null ? plan.getName() : payment.getPlanType();

        try {
            log.debug("Verifying HMAC-SHA256 signature for Razorpay Order: {}", request.getRazorpayOrderId());
            boolean isValid = verifySignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
            );

            if (!isValid) {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                payment.setFailureReason("Invalid signature");
                paymentRepository.save(payment);
                publishSafe(() -> eventPublisher.publishPaymentFailed(
                    username, userEmail, payment.getOrderId(), planName, "Invalid payment signature"));
                return PaymentResponse.builder().success(false)
                    .message("Payment verification failed - invalid signature")
                    .orderId(payment.getOrderId()).build();
            }

            payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
            payment.setRazorpaySignature(request.getRazorpaySignature());
            payment.setStatus(Payment.PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);
            log.info("Payment record updated to SUCCESS for Order: {}", payment.getOrderId());

            grantCredits(username, payment.getCreditsGranted(), payment.getOrderId());

            UserCredits uc = userCreditsRepository.findByUsername(username).orElse(null);
            int totalCredits = uc != null ? uc.getRemainingCredits() : payment.getCreditsGranted();

            publishSafe(() -> eventPublisher.publishPaymentSuccess(
                username, userEmail, payment.getOrderId(), planName, payment.getPlanType(),
                payment.getCreditsGranted(), totalCredits,
                payment.getAmount() / 100.0, request.getRazorpayPaymentId()
            ));

            return PaymentResponse.builder().success(true)
                .message("Payment successful! Credits added to your account.")
                .orderId(payment.getOrderId())
                .creditsGranted(payment.getCreditsGranted()).build();

        } catch (com.resumeai.payment.exception.PaymentProcessingException e) {
            log.error("Payment processing error: {}", e.getMessage());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            publishSafe(() -> eventPublisher.publishPaymentFailed(
                username, userEmail, payment.getOrderId(), planName, e.getMessage()));
            return PaymentResponse.builder().success(false)
                .message("Payment verification failed: " + e.getMessage())
                .orderId(payment.getOrderId()).build();
        } catch (Exception e) {
            log.error("Unexpected error during payment verification", e);
            return PaymentResponse.builder().success(false)
                .message("An internal error occurred during payment verification.")
                .orderId(payment.getOrderId()).build();
        }
    }

    /** Backward-compat overload used by PaymentController (no userEmail) */
    @Transactional
    public PaymentResponse verifyAndProcessPayment(String username, PaymentVerificationRequest request) {
        return verifyAndProcessPayment(username, null, request);
    }

    private boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes());
            return bytesToHex(hash).equals(signature);
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            log.error("Failed to initialize HMAC-SHA256 for signature verification", e);
            throw new com.resumeai.payment.exception.PaymentProcessingException("Signature verification algorithm failure", e);
        } catch (Exception e) {
            log.warn("Generic failure in signature verification: {}", e.getMessage());
            return false;
        }
    }


    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Transactional
    public void grantCredits(String username, int credits, String referenceId) {
        UserCredits userCredits = userCreditsRepository.findByUsername(username)
            .orElseGet(() -> {
                UserCredits nc = new UserCredits();
                nc.setUsername(username); nc.setTotalCredits(0);
                nc.setUsedCredits(0); nc.setRemainingCredits(0);
                return nc;
            });

        int balanceBefore = userCredits.getRemainingCredits();
        userCredits.addCredits(credits);
        userCreditsRepository.save(userCredits);

        CreditTransaction tx = new CreditTransaction();
        tx.setUsername(username); tx.setType(CreditTransaction.TransactionType.CREDIT);
        tx.setCredits(credits); tx.setDescription("Credits purchased via " + referenceId);
        tx.setReferenceId(referenceId); tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(userCredits.getRemainingCredits());
        creditTransactionRepository.save(tx);

        log.info("Granted {} credits to {}. New balance: {}", credits, username, userCredits.getRemainingCredits());
    }

    /**
     * Deduct credits and fire CREDIT_DEDUCTED Kafka event.
     * Also triggers LOW_CREDITS_ALERT when remaining <= 3.
     */
    @Transactional
    public boolean useCredits(String username, String userEmail, int credits,
                               String description, String referenceId) {
        UserCredits userCredits = userCreditsRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User credits not found: " + username));

        if (!userCredits.hasCredits(credits)) {
            log.warn("Insufficient credits for {}. Required: {}, Available: {}", username, credits, userCredits.getRemainingCredits());
            return false;
        }

        int balanceBefore = userCredits.getRemainingCredits();
        if (!userCredits.useCredits(credits)) return false;
        userCreditsRepository.save(userCredits);

        CreditTransaction tx = new CreditTransaction();
        tx.setUsername(username); tx.setType(CreditTransaction.TransactionType.DEBIT);
        tx.setCredits(credits); tx.setDescription(description);
        tx.setReferenceId(referenceId); tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(userCredits.getRemainingCredits());
        creditTransactionRepository.save(tx);

        log.info("Used {} credits for {}. Remaining: {}", credits, username, userCredits.getRemainingCredits());

        if (userEmail != null && !userEmail.isBlank()) {
            final int remaining = userCredits.getRemainingCredits();
            publishSafe(() -> eventPublisher.publishCreditDeducted(username, userEmail, description, credits, remaining));
        }
        return true;
    }

    /** Backward-compat: no userEmail — no Kafka notification */
    @Transactional
    public boolean useCredits(String username, int credits, String description, String referenceId) {
        return useCredits(username, null, credits, description, referenceId);
    }

    public UserCreditsResponse getUserCredits(String username) {
        UserCredits uc = userCreditsRepository.findByUsername(username)
            .orElseGet(() -> {
                UserCredits nc = new UserCredits();
                nc.setUsername(username); nc.setTotalCredits(0);
                nc.setUsedCredits(0); nc.setRemainingCredits(0);
                return userCreditsRepository.save(nc);
            });
        return UserCreditsResponse.builder()
            .username(uc.getUsername()).totalCredits(uc.getTotalCredits())
            .usedCredits(uc.getUsedCredits()).remainingCredits(uc.getRemainingCredits()).build();
    }

    public boolean hasCredits(String username, int requiredCredits) {
        UserCredits uc = userCreditsRepository.findByUsername(username).orElse(null);
        return uc != null && uc.hasCredits(requiredCredits);
    }

    public List<Payment> getPaymentHistory(String username) {
        return paymentRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public List<CreditTransaction> getTransactionHistory(String username) {
        return creditTransactionRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    private PlanDetails getPlanByType(String type) {
        return getAllPlans().stream()
            .filter(p -> p.getType().equalsIgnoreCase(type))
            .findFirst().orElse(null);
    }

    /** Safely run a Kafka publish; never propagates exceptions to the main flow. */
    private void publishSafe(Runnable publish) {
        try { publish.run(); }
        catch (Exception e) { log.warn("Kafka publish failed (non-critical): {}", e.getMessage()); }
    }
}
