package com.waad.tba.modules.rbac.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1: request body for
 * PUT /api/v1/admin/rbac/roles/{role}/permissions — replaces the role's
 * complete permission set with the given list of permission codes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolePermissionsRequestDto {

    @NotNull(message = "permissionCodes is required")
    private List<String> permissionCodes;
}
