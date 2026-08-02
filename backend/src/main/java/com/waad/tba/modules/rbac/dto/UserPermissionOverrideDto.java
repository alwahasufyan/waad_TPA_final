package com.waad.tba.modules.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI: one row of a user's permission
 * override history (Tab 3, "الصلاحيات الخاصة للمستخدمين").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionOverrideDto {
    private Long id;
    private String permissionCode;
    private String permissionLabelAr;
    private String effect;
    private String reason;
    private Long grantedByUserId;
    private String grantedByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long revokedByUserId;
    private String revokedByUsername;
    private boolean active;
}
