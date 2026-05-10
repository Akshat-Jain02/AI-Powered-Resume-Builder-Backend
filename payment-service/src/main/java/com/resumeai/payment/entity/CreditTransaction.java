package com.resumeai.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private Integer credits;

    private String description;

    private String referenceId; // Payment ID or Service Request ID

    @Column(nullable = false)
    private Integer balanceBefore;

    @Column(nullable = false)
    private Integer balanceAfter;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum TransactionType {
        CREDIT,      // Credits added (via payment)
        DEBIT,       // Credits used (via AI service)
        REFUND,      // Credits refunded
        ADJUSTMENT   // Manual adjustment by admin
    }
}
