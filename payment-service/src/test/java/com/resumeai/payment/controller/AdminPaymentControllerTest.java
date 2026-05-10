package com.resumeai.payment.controller;

import com.resumeai.payment.entity.CreditTransaction;
import com.resumeai.payment.entity.Payment;
import com.resumeai.payment.entity.UserCredits;
import com.resumeai.payment.repository.CreditTransactionRepository;
import com.resumeai.payment.repository.PaymentRepository;
import com.resumeai.payment.repository.UserCreditsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminPaymentController.class)
@WithMockUser(roles = "ADMIN")
class AdminPaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PaymentRepository paymentRepository;
    @MockBean UserCreditsRepository userCreditsRepository;
    @MockBean CreditTransactionRepository creditTransactionRepository;

    @Test
    void getAllPayments_withAdminRole_returnsOk() throws Exception {
        when(paymentRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/payment/admin/payments")
                .header("X-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllPayments_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/payment/admin/payments")
                .header("X-Roles", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserCreditsAdmin_withAdminRole_returnsOk() throws Exception {
        UserCredits uc = new UserCredits();
        uc.setUsername("alice");
        uc.setRemainingCredits(10);
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(uc));

        mockMvc.perform(get("/api/payment/admin/credits/alice")
                .header("X-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getUserCreditsAdmin_userNotFound_returns404() throws Exception {
        when(userCreditsRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/payment/admin/credits/ghost")
                .header("X-Roles", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllCredits_withAdminRole_returnsOk() throws Exception {
        when(userCreditsRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/payment/admin/credits")
                .header("X-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void adjustCreditsManually_withAdminRole_returnsOk() throws Exception {
        UserCredits uc = new UserCredits();
        uc.setUsername("alice");
        uc.setRemainingCredits(5);
        when(userCreditsRepository.findByUsername("alice")).thenReturn(Optional.of(uc));
        when(userCreditsRepository.save(any())).thenReturn(uc);

        mockMvc.perform(post("/api/payment/admin/credits/alice/adjust").with(csrf())
                .header("X-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 10, \"reason\": \"Promo\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void getStats_withAdminRole_returnsOk() throws Exception {
        when(paymentRepository.count()).thenReturn(5L);
        when(userCreditsRepository.count()).thenReturn(3L);

        mockMvc.perform(get("/api/payment/admin/stats")
                .header("X-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getPaymentsByUser_withAdminRole_returnsOk() throws Exception {
        when(paymentRepository.findByUsernameOrderByCreatedAtDesc("alice")).thenReturn(List.of());

        mockMvc.perform(get("/api/payment/admin/payments/user/alice")
                .header("X-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }
}
