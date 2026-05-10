package com.resumeai.payment.repository;

import com.resumeai.payment.entity.UserCredits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserCreditsRepository extends JpaRepository<UserCredits, Long> {
    
    Optional<UserCredits> findByUsername(String username);
    
    boolean existsByUsername(String username);
}
