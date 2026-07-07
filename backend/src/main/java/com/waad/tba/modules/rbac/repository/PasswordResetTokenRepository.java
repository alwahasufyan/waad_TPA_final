package com.waad.tba.modules.rbac.repository;

import com.waad.tba.modules.rbac.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository("rbacPasswordResetTokenRepository")
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findByEmail(String email);

    @Modifying
    @Query("DELETE FROM RbacPasswordResetToken t WHERE t.email = :email")
    void deleteByEmail(String email);

    @Query("SELECT t FROM RbacPasswordResetToken t WHERE t.userId = :userId AND t.used = false AND t.expiresAt > :now ORDER BY t.createdAt DESC")
    Optional<PasswordResetToken> findActiveTokenByUserId(Long userId, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RbacPasswordResetToken t WHERE t.expiresAt < :now OR t.used = true")
    int deleteExpiredOrUsedTokens(LocalDateTime now);

    @Modifying
    @Query("UPDATE RbacPasswordResetToken t SET t.used = true WHERE t.userId = :userId")
    void invalidateAllUserTokens(Long userId);
}
