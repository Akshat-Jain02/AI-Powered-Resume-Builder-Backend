package com.resumeai.payment.service;

import com.razorpay.RazorpayClient;
import com.resumeai.payment.dto.*;
import com.resumeai.payment.entity.CreditTransaction;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private RazorpayClient razorpayClient;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserCreditsRepository userCreditsRepository;
    @Mock private CreditTransactionRepository creditTransactionRepository;
    @Mock private PaymentEventPublisher eventPublisher;

    @InjectMocks private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "test_key");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "test_secret_at_least_32_chars_long");
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
    void getAllPlans_returnsThreePlans() {
        List<PlanDetails> plans = paymentService.getAllPlans();
        assertThat(plans).hasSize(3);
        assertThat(plans).extracting(PlanDetails::getType)
                .containsExactlyInAnyOrder("BASIC", "PRO", "PREMIUM");
    }

    @Test
    void getUserCredits_existingUser() {
        UserCredits uc = new UserCredits();
        uc.setUsername("alice");
        uc.setTotalCredits(10);
        uc.setUsedCredits(2);
        uc.setRemainingCredits(8);

        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(uc));

        UserCreditsResponse response = paymentService.getUserCredits("alice");
        assertThat(response.getRemainingCredits()).isEqualTo(8);
    }

    @Test
    void getUserCredits_newUser_createsRecord() {
        UserCredits saved = new UserCredits();
        saved.setUsername("newuser");
        saved.setRemainingCredits(0);

        when(userCreditsRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userCreditsRepository.save(any())).thenReturn(saved);

        UserCreditsResponse response = paymentService.getUserCredits("newuser");
        assertThat(response.getRemainingCredits()).isEqualTo(0);
    }

    @Test
    void hasCredits_sufficient() {
        UserCredits uc = new UserCredits();
        uc.setRemainingCredits(5);
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(uc));
        assertThat(paymentService.hasCredits("alice", 3)).isTrue();
    }

    @Test
    void hasCredits_insufficient() {
        UserCredits uc = new UserCredits();
        uc.setRemainingCredits(1);
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(uc));
        assertThat(paymentService.hasCredits("alice", 5)).isFalse();
    }

    @Test
    void hasCredits_userNotFound_returnsFalse() {
        when(userCreditsRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThat(paymentService.hasCredits("ghost", 1)).isFalse();
    }

    @Test
    void grantCredits_newUser_createsAndGrants() {
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userCreditsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        paymentService.grantCredits("alice", 10, "ORDER_123");

        verify(userCreditsRepository, times(1)).save(any()); // saves once after addCredits
        verify(creditTransactionRepository).save(any(CreditTransaction.class));
    }

    @Test
    void useCredits_success() {
        UserCredits uc = mock(UserCredits.class);
        when(uc.hasCredits(1)).thenReturn(true);
        when(uc.useCredits(1)).thenReturn(true);
        when(uc.getRemainingCredits()).thenReturn(4);
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(uc));
        when(userCreditsRepository.save(any())).thenReturn(uc);

        boolean result = paymentService.useCredits("alice", 1, "AI Analysis", "ref-123");
        assertThat(result).isTrue();
    }

    @Test
    void useCredits_insufficientCredits_returnsFalse() {
        UserCredits uc = mock(UserCredits.class);
        when(uc.hasCredits(5)).thenReturn(false);
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(uc));

        boolean result = paymentService.useCredits("alice", 5, "AI Analysis", "ref-123");
        assertThat(result).isFalse();
    }

    @Test
    void useCredits_userNotFound_throws() {
        when(userCreditsRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentService.useCredits("ghost", 1, "desc", "ref"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getPaymentHistory_delegatesToRepository() {
        when(paymentRepository.findByUsernameOrderByCreatedAtDesc("alice")).thenReturn(List.of());
        assertThat(paymentService.getPaymentHistory("alice")).isEmpty();
    }

    @Test
    void getTransactionHistory_delegatesToRepository() {
        when(creditTransactionRepository.findByUsernameOrderByCreatedAtDesc("alice")).thenReturn(List.of());
        assertThat(paymentService.getTransactionHistory("alice")).isEmpty();
    }

    @Test
    void verifyAndProcessPayment_paymentNotFound_throws() {
        PaymentVerificationRequest req = new PaymentVerificationRequest();
        req.setOrderId("ORDER_UNKNOWN");
        when(paymentRepository.findByOrderId("ORDER_UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.verifyAndProcessPayment("alice", req))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyAndProcessPayment_wrongUser_throws() {
        Payment payment = new Payment();
        payment.setUsername("bob");

        PaymentVerificationRequest req = new PaymentVerificationRequest();
        req.setOrderId("ORDER_123");
        when(paymentRepository.findByOrderId("ORDER_123")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.verifyAndProcessPayment("alice", req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("does not belong to user");
    }

    @Test
    void createOrder_success() throws Exception {
        // Need to mock razorpayClient.orders.create(JSONObject)
        // This is tricky because razorpayClient.orders is a public field
        com.razorpay.Order razorOrder = mock(com.razorpay.Order.class);
        when(razorOrder.get("id")).thenReturn("rzp_123");
        
        // Reflection to inject orders mock into razorpayClient
        com.razorpay.OrderClient orderClient = mock(com.razorpay.OrderClient.class);
        when(orderClient.create(any(org.json.JSONObject.class))).thenReturn(razorOrder);
        
        ReflectionTestUtils.setField(razorpayClient, "orders", orderClient);
        
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanType("BASIC");
        
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        
        OrderResponse resp = paymentService.createOrder("alice", "alice@example.com", req);
        
        assertThat(resp.getRazorpayOrderId()).isEqualTo("rzp_123");
        assertThat(resp.getPlanType()).isEqualTo("BASIC");
    }

    @Test
    void createOrder_invalidPlan_throws() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanType("GHOST_PLAN");
        assertThatThrownBy(() -> paymentService.createOrder("alice", "a@b.com", req))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

// Extra tests appended to reach >80% coverage on PaymentService
