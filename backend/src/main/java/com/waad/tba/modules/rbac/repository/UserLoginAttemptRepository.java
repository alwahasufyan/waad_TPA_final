package com.waad.tba.modules.rbac.repository;

import com.waad.tba.modules.rbac.entity.UserLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserLoginAttemptRepository extends JpaRepository<UserLoginAttempt, Long> {
    
    List<UserLoginAttempt> findByUserIdOrderByAttemptedAtDesc(Long userId);
    
    List<UserLoginAttempt> findByUsernameOrderByAttemptedAtDesc(String username);
    
    @Query("SELECT COUNT(a) FROM UserLoginAttempt a WHERE a.userId = :userId AND a.success = false AND a.attemptedAt > :since")
    long countFailedAttemptsSince(Long userId, LocalDateTime since);
    
    @Query("SELECT COUNT(a) FROM UserLoginAttempt a WHERE a.username = :username AND a.success = false AND a.attemptedAt > :since")
    long countFailedAttemptsByUsernameSince(String username, LocalDateTime since);
    
    @Query("SELECT a FROM UserLoginAttempt a WHERE a.success = false AND a.attemptedAt > :since ORDER BY a.attemptedAt DESC")
    List<UserLoginAttempt> findRecentFailedAttempts(LocalDateTime since);
}
