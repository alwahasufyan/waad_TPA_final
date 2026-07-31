package com.waad.tba.modules.rbac.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WAAD-RBAC-PHASE-1-FOUNDATION permission catalog entry.
 *
 * Static, code-defined actions (e.g. "claims.review"), grouped for UI display.
 * Not consulted by @PreAuthorize — see EffectivePermissionService for how
 * this feeds the (read-model) effective-permissions calculation.
 */
@Entity
@Table(name = "permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Permission {

    @Id
    private String code;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "label_ar", nullable = false)
    private String labelAr;

    @Column(name = "label_en", nullable = false)
    private String labelEn;

    @Column(nullable = false)
    @Builder.Default
    private Boolean sensitive = false;

    @Column(name = "critical_security", nullable = false)
    @Builder.Default
    private Boolean criticalSecurity = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
