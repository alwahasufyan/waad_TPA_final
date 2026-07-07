package com.waad.tba.modules.rbac.repository;

import com.waad.tba.modules.rbac.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    
    Optional<EmailVerificationToken> findByToken(String token);
    
    Optional<EmailVerificationToken> findByUserId(Long userId);
    
    @Query("SELECT t FROM EmailVerificationToken t WHERE t.userId = :userId AND t.verified = false AND t.expiresAt > :now ORDER BY t.createdAt DESC")
    Optional<EmailVerificationToken> findActiveTokenByUserId(Long userId, LocalDateTime now);
    
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :now OR t.verified = true")
    int deleteExpiredOrVerifiedTokens(LocalDateTime now);
    
    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.verified = true WHERE t.userId = :userId")
    void markAllUserTokensAsVerified(Long userId);
}
