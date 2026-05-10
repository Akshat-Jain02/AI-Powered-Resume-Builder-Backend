package com.resumeai.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.resumeai.payment.dto.PaymentVerificationRequest;
import com.resumeai.payment.entity.Payment;
import com.resumeai.payment.entity.UserCredits;
import com.resumeai.payment.kafka.PaymentEventPublisher;
import com.resumeai.payment.repository.PaymentRepository;
import com.resumeai.payment.repository.UserCreditsRepository;
import com.resumeai.payment.repository.CreditTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceCoverageTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private UserCreditsRepository userCreditsRepository;
    @Mock private CreditTransactionRepository creditTransactionRepository;
    @Mock private PaymentEventPublisher eventPublisher;
    
    @InjectMocks private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "123");
        ReflectionTestUtils.setField(paymentService, "basicPrice", 299);
        ReflectionTestUtils.setField(paymentService, "basicCredits", 10);
        ReflectionTestUtils.setField(paymentService, "basicName", "Basic");
    }

    @Test
    void verifyAndProcessPayment_paymentBelongsToDifferentUser_throwsException() {
        Payment payment = new Payment();
        payment.setUsername("bob");
        payment.setOrderId("ORDER_123");
        when(paymentRepository.findByOrderId("ORDER_123")).thenReturn(Optional.of(payment));

        PaymentVerificationRequest req = new PaymentVerificationRequest();
        req.setOrderId("ORDER_123");

        assertThatThrownBy(() -> paymentService.verifyAndProcessPayment("alice", "alice@test.com", req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Payment does not belong to user");
    }

    @Test
    void verifyAndProcessPayment_validSignature_setsSuccessAndGrantsCredits() throws Exception {
        Payment payment = new Payment();
        payment.setUsername("alice");
        payment.setOrderId("ORDER_123");
        payment.setPlanType("BASIC");
        payment.setCreditsGranted(10);
        payment.setAmount(29900);
        when(paymentRepository.findByOrderId("ORDER_123")).thenReturn(Optional.of(payment));

        String payload = "rp_123|pay_123";
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("123".getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        String validSig = sb.toString();

        PaymentVerificationRequest req = new PaymentVerificationRequest();
        req.setOrderId("ORDER_123");
        req.setRazorpayOrderId("rp_123");
        req.setRazorpayPaymentId("pay_123");
        req.setRazorpaySignature(validSig);

        UserCredits uc = new UserCredits();
        uc.setUsername("alice");
        uc.setTotalCredits(0);
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.empty());

        var res = paymentService.verifyAndProcessPayment("alice", "alice@test.com", req);

        assertThat(res.isSuccess()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
        verify(paymentRepository).save(payment);
        verify(userCreditsRepository).save(any(UserCredits.class));
        verify(eventPublisher).publishPaymentSuccess(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyDouble(), anyString()
        );
    }
}
