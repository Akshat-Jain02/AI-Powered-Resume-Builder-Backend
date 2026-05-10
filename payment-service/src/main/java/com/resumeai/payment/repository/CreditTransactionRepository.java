package com.resumeai.payment.repository;

import com.resumeai.payment.entity.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {
    
    List<CreditTransaction> findByUsernameOrderByCreatedAtDesc(String username);
    
    List<CreditTransaction> findByUsernameAndTypeOrderByCreatedAtDesc(
        String username, 
        CreditTransaction.TransactionType type
    );
}
