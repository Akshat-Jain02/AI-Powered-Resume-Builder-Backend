package com.resumeai.payment.service;

import com.razorpay.RazorpayClient;
import com.resumeai.payment.dto.PaymentVerificationRequest;
import com.resumeai.payment.dto.UserCreditsResponse;
import com.resumeai.payment.entity.Payment;
import com.resumeai.payment.entity.UserCredits;
import com.resumeai.payment.kafka.PaymentEventPublisher;
import com.resumeai.payment.repository.CreditTransactionRepository;
import com.resumeai.payment.repository.PaymentRepository;
import com.resumeai.payment.repository.UserCreditsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceExtendedTest {

    @Mock private RazorpayClient razorpayClient;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserCreditsRepository userCreditsRepository;
    @Mock private CreditTransactionRepository creditTransactionRepository;
    @Mock private PaymentEventPublisher eventPublisher;

    @InjectMocks private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "key_test");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "secret_test_long_enough_32chars!!");
        ReflectionTestUtils.setField(paymentService, "currency", "INR");
        ReflectionTestUtils.setField(paymentService, "companyName", "ResumeAI");
        ReflectionTestUtils.setField(paymentService, "companyLogo", "");
        ReflectionTestUtils.setField(paymentService, "basicPrice", 299);
        ReflectionTestUtils.setField(paymentService, "basicCredits", 10);
        ReflectionTestUtils.setField(paymentService, "basicName", "Basic");
        ReflectionTestUtils.setField(paymentService, "proPrice", 599);
        ReflectionTestUtils.setField(paymentService, "proCredits", 25);
        ReflectionTestUtils.setField(paymentService, "proName", "Pro");
        ReflectionTestUtils.setField(paymentService, "premiumPrice", 999);
        ReflectionTestUtils.setField(paymentService, "premiumCredits", 50);
        ReflectionTestUtils.setField(paymentService, "premiumName", "Premium");
    }

    @Test
    void verifyAndProcessPayment_invalidSignature_returnsFailure() {
        Payment payment = new Payment();
        payment.setUsername("alice");
        payment.setOrderId("ORDER_123");
        payment.setRazorpayOrderId("rp_order_123");
        payment.setPlanType("PRO");
        payment.setCreditsGranted(25);
        payment.setAmount(59900);
        payment.setStatus(Payment.PaymentStatus.CREATED);

        PaymentVerificationRequest req = new PaymentVerificationRequest();
        req.setOrderId("ORDER_123");
        req.setRazorpayOrderId("rp_order_123");
        req.setRazorpayPaymentId("pay_123");
        req.setRazorpaySignature("totally_wrong_signature");

        when(paymentRepository.findByOrderId("ORDER_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenReturn(payment);

        var response = paymentService.verifyAndProcessPayment("alice", "alice@test.com", req);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("invalid signature");
    }

    @Test
    void verifyAndProcessPayment_backwardCompatOverload_delegates() {
        Payment payment = new Payment();
        payment.setUsername("alice");
        payment.setOrderId("ORDER_456");
        payment.setPlanType("BASIC");
        payment.setCreditsGranted(10);
        payment.setAmount(29900);
        payment.setStatus(Payment.PaymentStatus.CREATED);

        PaymentVerificationRequest req = new PaymentVerificationRequest();
        req.setOrderId("ORDER_456");
        req.setRazorpayOrderId("rp_456");
        req.setRazorpayPaymentId("pay_456");
        req.setRazorpaySignature("bad_sig");

        when(paymentRepository.findByOrderId("ORDER_456")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenReturn(payment);

        // backward-compat overload (no userEmail)
        var response = paymentService.verifyAndProcessPayment("alice", req);
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse(); // bad sig → failure
    }

    @Test
    void grantCredits_existingUser_addsToBalance() {
        UserCredits existing = new UserCredits();
        existing.setUsername("alice");
        existing.setTotalCredits(10);
        existing.setUsedCredits(2);
        existing.setRemainingCredits(8);

        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(userCreditsRepository.save(any())).thenReturn(existing);

        paymentService.grantCredits("alice", 15, "ORDER_PROMO");

        verify(creditTransactionRepository).save(any());
        verify(userCreditsRepository).save(any());
    }

    @Test
    void useCredits_withEmail_publishesKafkaEvent() {
        UserCredits uc = mock(UserCredits.class);
        when(uc.hasCredits(1)).thenReturn(true);
        when(uc.useCredits(1)).thenReturn(true);
        when(uc.getRemainingCredits()).thenReturn(9);
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(uc));
        when(userCreditsRepository.save(any())).thenReturn(uc);

        boolean result = paymentService.useCredits("alice", "alice@example.com", 1, "AI Analysis", "ref");

        assertThat(result).isTrue();
        verify(eventPublisher).publishCreditDeducted(eq("alice"), eq("alice@example.com"),
                eq("AI Analysis"), eq(1), eq(9));
    }

    @Test
    void useCredits_withoutEmail_noKafkaEvent() {
        UserCredits uc = mock(UserCredits.class);
        when(uc.hasCredits(1)).thenReturn(true);
        when(uc.useCredits(1)).thenReturn(true);
        when(uc.getRemainingCredits()).thenReturn(4);
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(uc));
        when(userCreditsRepository.save(any())).thenReturn(uc);

        boolean result = paymentService.useCredits("alice", null, 1, "desc", "ref");

        assertThat(result).isTrue();
        verify(eventPublisher, never()).publishCreditDeducted(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void useCredits_useCreditsReturnsFalse_returnsFalse() {
        UserCredits uc = mock(UserCredits.class);
        when(uc.hasCredits(1)).thenReturn(true);
        when(uc.useCredits(1)).thenReturn(false); // entity-level deduction failed
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(uc));

        boolean result = paymentService.useCredits("alice", 1, "desc", "ref");
        assertThat(result).isFalse();
    }

    @Test
    void getUserCredits_returnsCorrectFields() {
        UserCredits uc = new UserCredits();
        uc.setUsername("bob");
        uc.setTotalCredits(50);
        uc.setUsedCredits(10);
        uc.setRemainingCredits(40);
        when(userCreditsRepository.findByUsername("bob")).thenReturn(Optional.of(uc));

        UserCreditsResponse response = paymentService.getUserCredits("bob");

        assertThat(response.getUsername()).isEqualTo("bob");
        assertThat(response.getTotalCredits()).isEqualTo(50);
        assertThat(response.getUsedCredits()).isEqualTo(10);
        assertThat(response.getRemainingCredits()).isEqualTo(40);
    }

    @Test
    void getAllPlans_basicPlanHasCorrectPrice() {
        var plans = paymentService.getAllPlans();
        var basic = plans.stream().filter(p -> "BASIC".equals(p.getType())).findFirst();
        assertThat(basic).isPresent();
        assertThat(basic.get().getPrice()).isEqualTo(299);
        assertThat(basic.get().getCredits()).isEqualTo(10);
    }

    @Test
    void getAllPlans_proPlanHasCorrectCredits() {
        var plans = paymentService.getAllPlans();
        var pro = plans.stream().filter(p -> "PRO".equals(p.getType())).findFirst();
        assertThat(pro).isPresent();
        assertThat(pro.get().getCredits()).isEqualTo(25);
    }

    @Test
    void getAllPlans_premiumPlanIsPresent() {
        var plans = paymentService.getAllPlans();
        assertThat(plans.stream().anyMatch(p -> "PREMIUM".equals(p.getType()))).isTrue();
    }
}
