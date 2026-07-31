package com.waad.tba.modules.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1: read-model view of a single permission
 * catalog entry, for the Roles & Permissions matrix UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDto {
    private String code;
    private String groupName;
    private String labelAr;
    private String labelEn;
    private Boolean sensitive;
    private Boolean criticalSecurity;
}
