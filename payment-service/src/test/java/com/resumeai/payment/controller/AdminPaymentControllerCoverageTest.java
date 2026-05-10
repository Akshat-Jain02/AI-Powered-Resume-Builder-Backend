package com.resumeai.payment.controller;

import com.resumeai.payment.entity.CreditTransaction;
import com.resumeai.payment.entity.Payment;
import com.resumeai.payment.entity.UserCredits;
import com.resumeai.payment.repository.CreditTransactionRepository;
import com.resumeai.payment.repository.PaymentRepository;
import com.resumeai.payment.repository.UserCreditsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPaymentControllerCoverageTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private UserCreditsRepository userCreditsRepository;
    @Mock private CreditTransactionRepository creditTransactionRepository;

    @InjectMocks private AdminPaymentController controller;

    @Test
    void testIsAdminBranches() {
        assertThat(controller.getAllPayments(null).getStatusCodeValue()).isEqualTo(403);
        assertThat(controller.getAllPayments("   ").getStatusCodeValue()).isEqualTo(403);
        assertThat(controller.getAllPayments("USER, MOD").getStatusCodeValue()).isEqualTo(403);
        
        when(paymentRepository.findAll()).thenReturn(List.of());
        assertThat(controller.getAllPayments("USER, ADMIN").getStatusCodeValue()).isEqualTo(200);
        assertThat(controller.getAllPayments("ROLE_ADMIN").getStatusCodeValue()).isEqualTo(200);
    }

    @Test
    void getPaymentById_branches() {
        assertThat(controller.getPaymentById(1L, "USER").getStatusCodeValue()).isEqualTo(403);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(new Payment()));
        when(paymentRepository.findById(2L)).thenReturn(Optional.empty());
        
        assertThat(controller.getPaymentById(1L, "ADMIN").getStatusCodeValue()).isEqualTo(200);
        assertThat(controller.getPaymentById(2L, "ADMIN").getStatusCodeValue()).isEqualTo(404);
    }

    @Test
    void getPaymentsByUser_branches() {
        assertThat(controller.getPaymentsByUser("alice", "USER").getStatusCodeValue()).isEqualTo(403);
        when(paymentRepository.findByUsernameOrderByCreatedAtDesc("alice")).thenReturn(List.of());
        assertThat(controller.getPaymentsByUser("alice", "ADMIN").getStatusCodeValue()).isEqualTo(200);
    }

    @Test
    void getAllCredits_branches() {
        assertThat(controller.getAllCredits("USER").getStatusCodeValue()).isEqualTo(403);
        when(userCreditsRepository.findAll()).thenReturn(List.of());
        assertThat(controller.getAllCredits("ADMIN").getStatusCodeValue()).isEqualTo(200);
    }

    @Test
    void getCreditsByUser_branches() {
        assertThat(controller.getCreditsByUser("alice", "USER").getStatusCodeValue()).isEqualTo(403);
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(new UserCredits()));
        when(userCreditsRepository.findByUsername("bob")).thenReturn(Optional.empty());
        
        assertThat(controller.getCreditsByUser("alice", "ADMIN").getStatusCodeValue()).isEqualTo(200);
        assertThat(controller.getCreditsByUser("bob", "ADMIN").getStatusCodeValue()).isEqualTo(404);
    }

    @Test
    void adjustCredits_branches() {
        assertThat(controller.adjustCredits("alice", Map.of(), "admin", "USER").getStatusCodeValue()).isEqualTo(403);
        
        // Invalid amount parse
        assertThat(controller.adjustCredits("alice", Map.of("amount", "abc"), "admin", "ADMIN").getStatusCodeValue()).isEqualTo(400);

        // Deduct branch (-10) with empty credits
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.empty());
        ResponseEntity<?> res1 = controller.adjustCredits("alice", Map.of("amount", -10), "admin", "ADMIN");
        assertThat(res1.getStatusCodeValue()).isEqualTo(200);
        verify(creditTransactionRepository, times(1)).save(any());

        // Add branch (+10) with existing credits
        UserCredits c = new UserCredits(); c.setRemainingCredits(5);
        when(userCreditsRepository.findByUsername("bob")).thenReturn(Optional.of(c));
        ResponseEntity<?> res2 = controller.adjustCredits("bob", Map.of("amount", 10), "admin", "ADMIN");
        assertThat(res2.getStatusCodeValue()).isEqualTo(200);
        verify(creditTransactionRepository, times(2)).save(any());
    }

    @Test
    void getPaymentStats_branches() {
        assertThat(controller.getPaymentStats("USER").getStatusCodeValue()).isEqualTo(403);

        Payment p1 = new Payment(); p1.setStatus(Payment.PaymentStatus.COMPLETED); p1.setAmount(1000);
        Payment p2 = new Payment(); p2.setStatus(Payment.PaymentStatus.PENDING); p2.setAmount(100);
        Payment p3 = new Payment(); p3.setStatus(Payment.PaymentStatus.FAILED); p3.setAmount(0);

        when(paymentRepository.findAll()).thenReturn(List.of(p1, p2, p3));
        when(userCreditsRepository.count()).thenReturn(10L);

        ResponseEntity<?> res = controller.getPaymentStats("ADMIN");
        assertThat(res.getStatusCodeValue()).isEqualTo(200);
        Map<?, ?> body = (Map<?, ?>) res.getBody();
        assertThat(body.get("successfulPayments")).isEqualTo(1L);
    }
}
