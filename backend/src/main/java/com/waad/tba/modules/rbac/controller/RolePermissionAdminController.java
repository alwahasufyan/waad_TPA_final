package com.waad.tba.modules.rbac.controller;

import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.rbac.dto.PermissionGroupDto;
import com.waad.tba.modules.rbac.dto.RoleSummaryDto;
import com.waad.tba.modules.rbac.dto.UpdateRolePermissionsRequestDto;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.service.RolePermissionAdminService;
import com.waad.tba.security.AuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1: backend for the Roles & Permissions
 * matrix UI. Same access as user management (SUPER_ADMIN, WAAD_ADMIN) — the
 * WAAD_ADMIN-vs-critical-security restriction is enforced in
 * {@link RolePermissionAdminService}, not here.
 */
@RestController
@RequestMapping("/api/v1/admin/rbac")
@RequiredArgsConstructor
@Tag(name = "RBAC - Role Permissions", description = "Permission catalog and role permission matrix (WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1)")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN')")
public class RolePermissionAdminController {

    private final RolePermissionAdminService adminService;
    private final AuthorizationService authorizationService;

    @GetMapping("/permissions/grouped")
    @Operation(summary = "Get permission catalog grouped by group_name")
    public ResponseEntity<ApiResponse<List<PermissionGroupDto>>> getGroupedPermissions() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getGroupedPermissions()));
    }

    @GetMapping("/roles")
    @Operation(summary = "Get role summaries", description = "Role cards for the permissions matrix: display name, permission count, assigned user count, editability.")
    public ResponseEntity<ApiResponse<List<RoleSummaryDto>>> getRoles() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getRoles()));
    }

    @GetMapping("/roles/{role}/permissions")
    @Operation(summary = "Get a role's permission codes")
    public ResponseEntity<ApiResponse<Set<String>>> getRolePermissions(
            @Parameter(name = "role", description = "Role name, e.g. MEDICAL_REVIEWER", required = true) @PathVariable("role") String role) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getRolePermissionCodes(role)));
    }

    @PutMapping("/roles/{role}/permissions")
    @Operation(summary = "Replace a role's permission set", description = "SUPER_ADMIN's permission set cannot be edited (hardcoded to all). WAAD_ADMIN cannot change critical-security permissions for any role.")
    public ResponseEntity<ApiResponse<Void>> updateRolePermissions(
            @PathVariable("role") String role,
            @Valid @RequestBody UpdateRolePermissionsRequestDto request) {
        User actor = authorizationService.requireCurrentUser();
        adminService.updateRolePermissions(role, request.getPermissionCodes(), actor);
        return ResponseEntity.ok(ApiResponse.success("Role permissions updated successfully", null));
    }

    @GetMapping("/audit-log")
    @Operation(summary = "Search the RBAC audit trail", description = "Paginated, filterable log of role-permission changes, per-user overrides, and login/logout events (WAAD-RBAC-USERS-ROLES-PERMISSIONS-COMPLETION-1).")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.waad.tba.modules.rbac.dto.AuditLogEntryDto>>> getAuditLog(
            @Parameter(description = "Filter by action code, e.g. ROLE_PERMISSIONS_UPDATED") @org.springframework.web.bind.annotation.RequestParam(name = "action", required = false) String action,
            @Parameter(description = "Filter by target user ID") @org.springframework.web.bind.annotation.RequestParam(name = "userId", required = false) Long userId,
            @Parameter(description = "From timestamp (ISO, inclusive)") @org.springframework.web.bind.annotation.RequestParam(name = "from", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime from,
            @Parameter(description = "To timestamp (ISO, inclusive)") @org.springframework.web.bind.annotation.RequestParam(name = "to", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime to,
            @org.springframework.data.web.PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAuditLog(action, userId, from, to, pageable)));
    }
}
