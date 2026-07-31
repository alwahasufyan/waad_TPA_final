package com.waad.tba.modules.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1: role card summary for the Roles &
 * Permissions matrix UI's left-hand role list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleSummaryDto {
    private String role;
    private String displayNameAr;
    private String displayNameEn;
    private int permissionCount;
    /** True for SUPER_ADMIN: its permission set is hardcoded to "all" in
     *  EffectivePermissionService, not stored in role_permissions, so it
     *  cannot be edited through this API. */
    private boolean editable;
    /** True for BENEFICIARY: reserved for a future phase, not yet assignable. */
    private boolean reserved;
    private long userCount;
}
