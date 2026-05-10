package com.resumeai.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.payment.dto.*;
import com.resumeai.payment.entity.CreditTransaction;
import com.resumeai.payment.entity.Payment;
import com.resumeai.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PaymentService paymentService;

    @Test
    void getAllPlans_returnsOk() throws Exception {
        when(paymentService.getAllPlans()).thenReturn(List.of());
        mockMvc.perform(get("/api/payment/plans"))
                .andExpect(status().isOk());
    }

    @Test
    void getUserCredits_returnsOk() throws Exception {
        UserCreditsResponse resp = UserCreditsResponse.builder()
                .username("alice").totalCredits(10).usedCredits(2).remainingCredits(8).build();
        when(paymentService.getUserCredits("alice")).thenReturn(resp);

        mockMvc.perform(get("/api/payment/credits").header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingCredits").value(8));
    }

    @Test
    void checkCredits_returnsOk() throws Exception {
        when(paymentService.hasCredits("alice", 1)).thenReturn(true);

        mockMvc.perform(get("/api/payment/credits/check")
                .header("X-Username", "alice")
                .param("required", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCredits").value(true));
    }

    @Test
    void useCredits_success() throws Exception {
        when(paymentService.useCredits(eq("alice"), any(), eq(1), anyString(), any())).thenReturn(true);
        UserCreditsResponse resp = UserCreditsResponse.builder()
                .username("alice").remainingCredits(9).build();
        when(paymentService.getUserCredits("alice")).thenReturn(resp);

        mockMvc.perform(post("/api/payment/credits/use")
                .header("X-Username", "alice")
                .header("X-User-Email", "a@b.com")
                .param("credits", "1")
                .param("description", "AI Analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void useCredits_insufficient() throws Exception {
        when(paymentService.useCredits(eq("alice"), any(), eq(5), anyString(), any())).thenReturn(false);

        mockMvc.perform(post("/api/payment/credits/use")
                .header("X-Username", "alice")
                .param("credits", "5"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getPaymentHistory_returnsOk() throws Exception {
        when(paymentService.getPaymentHistory("alice")).thenReturn(List.of());
        mockMvc.perform(get("/api/payment/history").header("X-Username", "alice"))
                .andExpect(status().isOk());
    }

    @Test
    void getTransactionHistory_returnsOk() throws Exception {
        when(paymentService.getTransactionHistory("alice")).thenReturn(List.of());
        mockMvc.perform(get("/api/payment/transactions").header("X-Username", "alice"))
                .andExpect(status().isOk());
    }

    @Test
    void health_returnsUp() throws Exception {
        mockMvc.perform(get("/api/payment/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void verifyPayment_success() throws Exception {
        PaymentVerificationRequest req = new PaymentVerificationRequest();
        req.setOrderId("ORDER_123");
        req.setRazorpayOrderId("rzp_123");
        req.setRazorpayPaymentId("pay_123");
        req.setRazorpaySignature("sig_123");

        PaymentResponse resp = PaymentResponse.builder().success(true).message("OK").build();
        when(paymentService.verifyAndProcessPayment(eq("alice"), eq("a@b.com"), any())).thenReturn(resp);

        mockMvc.perform(post("/api/payment/verify")
                .header("X-Username", "alice")
                .header("X-User-Email", "a@b.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void verifyPayment_exception_returnsFailed() throws Exception {
        PaymentVerificationRequest req = new PaymentVerificationRequest();
        req.setOrderId("ORDER_X");
        req.setRazorpayOrderId("rzp_x");
        req.setRazorpayPaymentId("pay_x");
        req.setRazorpaySignature("sig_x");

        when(paymentService.verifyAndProcessPayment(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Payment not found"));

        mockMvc.perform(post("/api/payment/verify")
                .header("X-Username", "alice")
                .header("X-User-Email", "a@b.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createOrder_invalidPlan_returnsBadRequest() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setPlanType("INVALID");

        when(paymentService.createOrder(anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Invalid plan type"));

        mockMvc.perform(post("/api/payment/create-order")
                .header("X-Username", "alice")
                .header("X-User-Email", "a@b.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
