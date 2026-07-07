package com.waad.tba.modules.systemadmin.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Permission Matrix (Roles × Permissions)
 * Phase 2 - System Administration - Permission Matrix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionMatrixDto {
    
    private List<RolePermissionDto> roles;
    private List<String> allPermissions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RolePermissionDto {
        private Long roleId;
        private String roleName;
        private List<String> permissions;
        private Map<String, Boolean> permissionMap;
    }
}
