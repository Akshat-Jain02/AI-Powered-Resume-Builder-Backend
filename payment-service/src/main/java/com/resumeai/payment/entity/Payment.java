package com.resumeai.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String orderId;

    @Column(unique = true)
    private String razorpayOrderId;

    @Column(unique = true)
    private String razorpayPaymentId;

    private String razorpaySignature;

    @Column(nullable = false)
    private Integer amount; // Amount in paise (INR)

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private String planType; // BASIC, PRO, PREMIUM

    @Column(nullable = false)
    private Integer creditsGranted;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    private String failureReason;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = PaymentStatus.CREATED;
        }
    }

    public enum PaymentStatus {
        CREATED,       // Order created, awaiting payment
        COMPLETED,
        PENDING,       // Payment initiated
        SUCCESS,       // Payment successful
        FAILED,        // Payment failed
        REFUNDED       // Payment refunded
    }
}
