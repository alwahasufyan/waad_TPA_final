package com.waad.tba.modules.rbac.repository;

import com.waad.tba.modules.rbac.entity.UserAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, Long> {
    
    List<UserAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<UserAuditLog> findByActionOrderByCreatedAtDesc(String action);
    
    @Query("SELECT a FROM UserAuditLog a WHERE a.userId = :userId AND a.action = :action ORDER BY a.createdAt DESC")
    List<UserAuditLog> findByUserIdAndAction(Long userId, String action);
    
    @Query("SELECT a FROM UserAuditLog a WHERE a.createdAt > :since ORDER BY a.createdAt DESC")
    List<UserAuditLog> findRecentAuditLogs(LocalDateTime since);
    
    @Query("SELECT a FROM UserAuditLog a WHERE a.userId = :userId AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<UserAuditLog> findByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end);
}
