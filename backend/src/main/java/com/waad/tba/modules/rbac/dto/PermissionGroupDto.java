package com.waad.tba.modules.rbac.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1: permissions grouped by group_name, for
 * GET /api/v1/admin/rbac/permissions/grouped.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionGroupDto {
    private String groupName;
    private List<PermissionDto> permissions;
}
