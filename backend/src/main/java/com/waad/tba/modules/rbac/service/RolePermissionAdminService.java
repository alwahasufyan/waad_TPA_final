package com.waad.tba.modules.rbac.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.rbac.dto.PermissionDto;
import com.waad.tba.modules.rbac.dto.PermissionGroupDto;
import com.waad.tba.modules.rbac.dto.RoleSummaryDto;
import com.waad.tba.modules.rbac.entity.Permission;
import com.waad.tba.modules.rbac.entity.RolePermission;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.entity.UserAuditLog;
import com.waad.tba.modules.rbac.repository.PermissionRepository;
import com.waad.tba.modules.rbac.repository.RolePermissionRepository;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.security.rbac.SystemRole;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1: backend for the Roles & Permissions
 * matrix UI — reads the Phase 1 permission catalog / role_permissions tables
 * and allows editing a role's permission set.
 *
 * This is a read-model / admin-configuration surface, same as
 * EffectivePermissionService — it does NOT change how @PreAuthorize/hasRole(...)
 * enforcement works anywhere else. Changing a role's row here changes what
 * EffectivePermissionService reports as that role's base permission set (and,
 * transitively, what a future permission-aware UI/API might show or gate),
 * not what any existing endpoint currently accepts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolePermissionAdminService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String WAAD_ADMIN = "WAAD_ADMIN";

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final UserSecurityService securityService;

    @Transactional(readOnly = true)
    public List<PermissionGroupDto> getGroupedPermissions() {
        List<Permission> all = permissionRepository.findAll();
        Map<String, List<PermissionDto>> byGroup = new LinkedHashMap<>();
        for (Permission p : all) {
            byGroup.computeIfAbsent(p.getGroupName(), g -> new java.util.ArrayList<>())
                    .add(toDto(p));
        }
        return byGroup.entrySet().stream()
                .map(e -> PermissionGroupDto.builder().groupName(e.getKey()).permissions(e.getValue()).build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoleSummaryDto> getRoles() {
        int catalogSize = (int) permissionRepository.count();
        return java.util.Arrays.stream(SystemRole.values())
                .map(role -> {
                    String name = role.name();
                    boolean isSuperAdmin = SUPER_ADMIN.equals(name);
                    boolean isReserved = "BENEFICIARY".equals(name);
                    int permissionCount = isSuperAdmin
                            ? catalogSize
                            : rolePermissionRepository.findByRole(name).size();
                    return RoleSummaryDto.builder()
                            .role(name)
                            .displayNameAr(role.getDisplayNameAr())
                            .displayNameEn(role.getDisplayNameEn())
                            .permissionCount(permissionCount)
                            .editable(!isSuperAdmin)
                            .reserved(isReserved)
                            .userCount(userRepository.countByUserType(name))
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Set<String> getRolePermissionCodes(String role) {
        if (SUPER_ADMIN.equals(role)) {
            return permissionRepository.findAll().stream().map(Permission::getCode).collect(Collectors.toSet());
        }
        return rolePermissionRepository.findByRole(role).stream()
                .map(RolePermission::getPermissionCode)
                .collect(Collectors.toSet());
    }

    /**
     * Replace a role's complete permission set.
     *
     * Protections (ticket rule): SUPER_ADMIN's permission set is hardcoded
     * ("all") in EffectivePermissionService, not stored — it cannot be edited
     * through this API at all, by anyone. A WAAD_ADMIN actor additionally
     * cannot add or remove any permission flagged critical_security (e.g.
     * settings.manage) for ANY role, including WAAD_ADMIN's own row — mirrors
     * the same restriction already enforced in
     * EffectivePermissionService.createOverride() for per-user overrides.
     */
    @Transactional
    public void updateRolePermissions(String role, List<String> requestedCodes, User actor) {
        if (SUPER_ADMIN.equals(role)) {
            throw new AccessDeniedException("SUPER_ADMIN's permission set is fixed and cannot be edited");
        }
        if (SystemRole.fromString(role) == null) {
            throw new IllegalArgumentException("Unknown role: " + role);
        }

        Set<String> requested = Set.copyOf(requestedCodes);
        Set<String> validCodes = permissionRepository.findAll().stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        if (!validCodes.containsAll(requested)) {
            throw new IllegalArgumentException("Unknown permission code(s) requested");
        }

        boolean actorIsWaadAdmin = WAAD_ADMIN.equals(actor.getUserType());
        if (actorIsWaadAdmin) {
            Set<String> criticalCodes = permissionRepository.findByCriticalSecurityTrue().stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toSet());
            Set<String> current = getRolePermissionCodes(role);
            boolean touchesCritical = criticalCodes.stream()
                    .anyMatch(code -> requested.contains(code) != current.contains(code));
            if (touchesCritical) {
                log.error("⛔ WAAD_ADMIN {} attempted to change a critical-security permission for role {}",
                        actor.getUsername(), role);
                throw new AccessDeniedException(
                        "WAAD_ADMIN cannot grant or revoke critical-security permissions for any role");
            }
        }

        List<RolePermission> existing = rolePermissionRepository.findByRole(role);
        rolePermissionRepository.deleteAll(existing);
        List<RolePermission> toSave = requested.stream()
                .map(code -> RolePermission.builder().role(role).permissionCode(code).build())
                .collect(Collectors.toList());
        rolePermissionRepository.saveAll(toSave);

        securityService.auditLog(null, UserAuditLog.ACTION_ROLE_PERMISSIONS_UPDATED,
                String.format("Role %s permissions updated by %s (%s): %s", role, actor.getUsername(),
                        actor.getUserType(), String.join(", ", requested)),
                null, null, actor.getId());

        log.info("Role {} permissions updated by {} — {} permission(s)", role, actor.getUsername(), requested.size());
    }

    private PermissionDto toDto(Permission p) {
        return PermissionDto.builder()
                .code(p.getCode())
                .groupName(p.getGroupName())
                .labelAr(p.getLabelAr())
                .labelEn(p.getLabelEn())
                .sensitive(p.getSensitive())
                .criticalSecurity(p.getCriticalSecurity())
                .build();
    }
}
