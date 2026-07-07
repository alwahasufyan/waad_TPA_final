package com.waad.tba.modules.rbac.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * User Audit Log Entity
 * 
 * Comprehensive audit trail for all user security events:
 * - LOGIN_SUCCESS, LOGIN_FAILED
 * - PASSWORD_CHANGE, PASSWORD_RESET
 * - ROLE_CHANGE, ROLE_ASSIGNED, ROLE_REMOVED
 * - ACCOUNT_LOCKED, ACCOUNT_UNLOCKED
 * - EMAIL_VERIFIED
 * - USER_CREATED, USER_UPDATED, USER_DELETED
 */
@Entity
@Table(name = "user_audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "performed_by")
    private Long performedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Common audit action types
     */
    public static final String ACTION_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String ACTION_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACTION_PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String ACTION_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String ACTION_ROLE_CHANGE = "ROLE_CHANGE";
    public static final String ACTION_ROLE_ASSIGNED = "ROLE_ASSIGNED";
    public static final String ACTION_ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACTION_ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED";
    public static final String ACTION_EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String ACTION_USER_CREATED = "USER_CREATED";
    public static final String ACTION_USER_UPDATED = "USER_UPDATED";
    public static final String ACTION_USER_DELETED = "USER_DELETED";
    public static final String ACTION_USER_ACTIVATED = "USER_ACTIVATED";
    public static final String ACTION_USER_DEACTIVATED = "USER_DEACTIVATED";
}
