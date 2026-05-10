package com.resumeai.payment.controller;

import com.resumeai.payment.dto.PaymentVerificationRequest;
import com.resumeai.payment.dto.PaymentResponse;
import com.resumeai.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerCoverageTest {

    @Mock private PaymentService paymentService;
    @InjectMocks private PaymentController paymentController;

    @Test
    void createOrder_success() throws Exception {
        com.resumeai.payment.dto.OrderResponse mockRes = com.resumeai.payment.dto.OrderResponse.builder().build();
        when(paymentService.createOrder(anyString(), any(), any())).thenReturn(mockRes);
        ResponseEntity<?> res = paymentController.createOrder("alice", "alice@test.com", new com.resumeai.payment.dto.CreateOrderRequest());
        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }
    
    @Test
    void createOrder_exception_returns500() throws Exception {
        when(paymentService.createOrder(anyString(), any(), any())).thenThrow(new RuntimeException("error"));
        ResponseEntity<?> res = paymentController.createOrder("alice", "alice@test.com", new com.resumeai.payment.dto.CreateOrderRequest());
        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void verifyPayment_success() throws Exception {
        PaymentResponse mockRes = PaymentResponse.builder().success(true).build();
        when(paymentService.verifyAndProcessPayment(anyString(), any(), any())).thenReturn(mockRes);
        ResponseEntity<?> res = paymentController.verifyPayment("alice", "alice@test.com", new PaymentVerificationRequest());
        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void useCredits_success() {
        when(paymentService.useCredits(anyString(), anyString(), anyInt(), anyString(), anyString())).thenReturn(true);
        com.resumeai.payment.dto.UserCreditsResponse mockCredits = new com.resumeai.payment.dto.UserCreditsResponse();
        mockCredits.setRemainingCredits(10);
        when(paymentService.getUserCredits(anyString())).thenReturn(mockCredits);
        ResponseEntity<?> res = paymentController.useCredits("alice", "alice@test.com", 1, "desc", "ref");
        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getUserCredits_success() {
        when(paymentService.getUserCredits(anyString())).thenReturn(new com.resumeai.payment.dto.UserCreditsResponse());
        ResponseEntity<?> res = paymentController.getUserCredits("alice");
        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }
}
