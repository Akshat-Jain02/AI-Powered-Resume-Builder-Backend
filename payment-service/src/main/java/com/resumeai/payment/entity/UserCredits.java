package com.resumeai.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_credits")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCredits {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private Integer totalCredits = 0;

    @Column(nullable = false)
    private Integer usedCredits = 0;

    @Column(nullable = false)
    private Integer remainingCredits = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Add credits to user account
     */
    public void addCredits(int credits) {
        this.totalCredits += credits;
        this.remainingCredits += credits;
    }

    /**
     * Use credits from user account
     */
    public boolean useCredits(int credits) {
        if (this.remainingCredits >= credits) {
            this.usedCredits += credits;
            this.remainingCredits -= credits;
            return true;
        }
        return false;
    }

    /**
     * Check if user has enough credits
     */
    public boolean hasCredits(int credits) {
        return this.remainingCredits >= credits;
    }
}
