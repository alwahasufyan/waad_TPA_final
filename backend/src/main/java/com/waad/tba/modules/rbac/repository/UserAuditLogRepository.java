package com.waad.tba.modules.rbac.repository;

import com.waad.tba.modules.rbac.entity.UserAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, Long>, JpaSpecificationExecutor<UserAuditLog> {

    List<UserAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserAuditLog> findByActionOrderByCreatedAtDesc(String action);

    @Query("SELECT a FROM UserAuditLog a WHERE a.userId = :userId AND a.action = :action ORDER BY a.createdAt DESC")
    List<UserAuditLog> findByUserIdAndAction(Long userId, String action);

    @Query("SELECT a FROM UserAuditLog a WHERE a.createdAt > :since ORDER BY a.createdAt DESC")
    List<UserAuditLog> findRecentAuditLogs(LocalDateTime since);

    @Query("SELECT a FROM UserAuditLog a WHERE a.userId = :userId AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<UserAuditLog> findByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end);

    // WAAD-RBAC-USERS-ROLES-PERMISSIONS-COMPLETION-1: the filtered/paginated
    // search backing the admin "سجل تغييرات الصلاحيات" screen is built via
    // JpaSpecificationExecutor in RolePermissionAdminService instead of a
    // single JPQL query with "(:param IS NULL OR ...)" — the Postgres JDBC
    // driver cannot infer a bind parameter's type from a bare null check
    // like that (confirmed via a live "could not determine data type of
    // parameter" / "cannot cast type bytea to ..." 500 while building this).
}
