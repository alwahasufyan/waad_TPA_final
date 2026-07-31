package com.waad.tba.modules.rbac.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WAAD-RBAC-PHASE-1-FOUNDATION audited exception for a specific user:
 * grants a permission their role doesn't normally carry, or revokes one it
 * does. Every row records who granted it, why, and (if revoked) who revoked
 * it and when — see the ticket's rule 8 ("all permission changes must be
 * audited").
 */
@Entity
@Table(name = "user_permission_overrides")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserPermissionOverride {

    public static final String EFFECT_GRANT = "GRANT";
    public static final String EFFECT_REVOKE = "REVOKE";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq_gen_override")
    @SequenceGenerator(name = "user_seq_gen_override", sequenceName = "user_seq", allocationSize = 50)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "permission_code", nullable = false)
    private String permissionCode;

    @Column(nullable = false)
    private String effect;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "granted_by", nullable = false)
    private Long grantedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        if (revokedAt != null) return false;
        if (expiresAt != null && expiresAt.isBefore(now)) return false;
        return true;
    }
}
