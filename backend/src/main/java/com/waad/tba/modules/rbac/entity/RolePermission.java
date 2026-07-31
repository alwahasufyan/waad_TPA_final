package com.waad.tba.modules.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WAAD-RBAC-PHASE-1-FOUNDATION static role -> permission mapping.
 * Seeded in V101; the source of truth for {@link
 * com.waad.tba.modules.rbac.service.EffectivePermissionService}'s base set,
 * before overrides are applied.
 */
@Entity
@Table(name = "role_permissions")
@IdClass(RolePermissionId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    @Id
    @Column(name = "role", nullable = false)
    private String role;

    @Id
    @Column(name = "permission_code", nullable = false)
    private String permissionCode;
}
