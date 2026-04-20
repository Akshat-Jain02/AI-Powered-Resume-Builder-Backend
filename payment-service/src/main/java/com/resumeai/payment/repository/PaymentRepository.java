package com.resumeai.payment.repository;

import com.resumeai.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    Optional<Payment> findByOrderId(String orderId);
    
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);
    
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);
    
    List<Payment> findByUsernameOrderByCreatedAtDesc(String username);
    
    List<Payment> findByUsernameAndStatusOrderByCreatedAtDesc(String username, Payment.PaymentStatus status);
}
