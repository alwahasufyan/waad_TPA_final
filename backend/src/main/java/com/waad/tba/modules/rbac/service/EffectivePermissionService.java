package com.waad.tba.modules.rbac.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.rbac.entity.Permission;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.entity.UserAuditLog;
import com.waad.tba.modules.rbac.entity.UserPermissionOverride;
import com.waad.tba.modules.rbac.repository.PermissionRepository;
import com.waad.tba.modules.rbac.repository.RolePermissionRepository;
import com.waad.tba.modules.rbac.repository.UserPermissionOverrideRepository;
import com.waad.tba.modules.rbac.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WAAD-RBAC-PHASE-1-FOUNDATION effective-permission calculation.
 *
 * effectivePermissions(user) = roleBaseSet(user.userType)
 *                               + active GRANT overrides
 *                               - active REVOKE overrides
 * (SUPER_ADMIN short-circuits to the full permission catalog.)
 *
 * This is a READ MODEL for reporting/auditing/future frontend consumption
 * (ticket rule 10). It is not yet consulted by @PreAuthorize — the existing
 * role-based checks remain the enforced security boundary (ticket rule 9).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectivePermissionService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final UserSecurityService securityService;

    @Transactional(readOnly = true)
    public Set<String> getEffectivePermissions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return getEffectivePermissions(user);
    }

    @Transactional(readOnly = true)
    public Set<String> getEffectivePermissions(User user) {
        if (user.isSuperAdmin()) {
            return permissionRepository.findAll().stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toSet());
        }

        Set<String> effective = rolePermissionRepository.findByRole(user.getUserType()).stream()
                .map(rp -> rp.getPermissionCode())
                .collect(Collectors.toSet());

        List<UserPermissionOverride> overrides = overrideRepository.findByUserIdAndRevokedAtIsNull(user.getId());
        LocalDateTime now = LocalDateTime.now();
        for (UserPermissionOverride override : overrides) {
            if (override.getExpiresAt() != null && override.getExpiresAt().isBefore(now)) {
                continue;
            }
            if (UserPermissionOverride.EFFECT_GRANT.equals(override.getEffect())) {
                effective.add(override.getPermissionCode());
            } else if (UserPermissionOverride.EFFECT_REVOKE.equals(override.getEffect())) {
                effective.remove(override.getPermissionCode());
            }
        }
        return effective;
    }

    /**
     * Grant or revoke a permission for a specific user, as an audited exception
     * to their role's normal permission set (ticket: "Override = audited
     * exception for a specific user").
     *
     * Enforcement (ticket rule 2 — "WAAD_ADMIN ... cannot manage ... critical
     * security permissions"): a WAAD_ADMIN actor may not create an override for
     * a permission flagged critical_security (e.g. settings.manage) for any
     * user, including themselves. Only SUPER_ADMIN may do so. Also, an override
     * can never target a SUPER_ADMIN account (their effective set is always
     * "everything", see above — an override would be a no-op at best and a
     * confusing audit entry at worst).
     */
    @Transactional
    public UserPermissionOverride createOverride(User actor, Long targetUserId, String permissionCode,
            String effect, String reason, LocalDateTime expiresAt) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", targetUserId));

        if (target.isSuperAdmin()) {
            throw new AccessDeniedException("Cannot create permission overrides for a SUPER_ADMIN user");
        }

        Permission permission = permissionRepository.findById(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));

        boolean actorIsWaadAdmin = "WAAD_ADMIN".equals(actor.getUserType());
        if (actorIsWaadAdmin && Boolean.TRUE.equals(permission.getCriticalSecurity())) {
            log.error("⛔ WAAD_ADMIN {} attempted to override critical-security permission '{}' for user {}",
                    actor.getUsername(), permissionCode, targetUserId);
            throw new AccessDeniedException(
                    "WAAD_ADMIN cannot grant or revoke critical-security permissions (" + permissionCode + ")");
        }

        if (!UserPermissionOverride.EFFECT_GRANT.equals(effect) && !UserPermissionOverride.EFFECT_REVOKE.equals(effect)) {
            throw new IllegalArgumentException("effect must be GRANT or REVOKE");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required for a permission override");
        }

        // A user can have at most one ACTIVE override per permission code at a
        // time. Without this, two concurrent GRANT (or GRANT+REVOKE) overrides
        // for the same permission could both stay active — harmless for the
        // Set-based effective-permission calculation above, but the admin UI
        // only ever tracks the most recent active override per permission
        // code (see UserOverridesTab.jsx computeState()), so an older
        // duplicate would become invisible and un-deactivatable from the UI
        // while still silently in effect. Auto-superseding here (rather than
        // rejecting) keeps "create" safe for every caller, including two
        // admins racing on the same toggle in different tabs.
        overrideRepository.findByUserIdAndRevokedAtIsNull(targetUserId).stream()
                .filter(existing -> existing.getPermissionCode().equals(permissionCode))
                .forEach(existing -> {
                    existing.setRevokedAt(LocalDateTime.now());
                    existing.setRevokedBy(actor.getId());
                    overrideRepository.save(existing);
                    log.info("♻️ Superseded existing active override id={} ({} {}) for user={} with a new one",
                            existing.getId(), existing.getEffect(), permissionCode, targetUserId);
                });

        UserPermissionOverride override = UserPermissionOverride.builder()
                .userId(targetUserId)
                .permissionCode(permissionCode)
                .effect(effect)
                .reason(reason)
                .grantedBy(actor.getId())
                .expiresAt(expiresAt)
                .build();
        override = overrideRepository.save(override);

        String action = UserPermissionOverride.EFFECT_GRANT.equals(effect)
                ? UserAuditLog.ACTION_PERMISSION_OVERRIDE_GRANTED
                : UserAuditLog.ACTION_PERMISSION_OVERRIDE_REVOKED;
        securityService.auditLog(targetUserId, action,
                String.format("%s '%s' by %s (%s): %s", effect, permissionCode, actor.getUsername(),
                        actor.getUserType(), reason),
                null, null, actor.getId());

        return override;
    }

    /**
     * List all permission overrides for a user (active and historical) —
     * WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI, backs Tab 3
     * ("الصلاحيات الخاصة للمستخدمين") of /admin/users.
     */
    @Transactional(readOnly = true)
    public List<UserPermissionOverride> getOverridesForUser(Long userId) {
        return overrideRepository.findByUserId(userId);
    }

    /**
     * Same as {@link #getOverridesForUser}, mapped to DTOs with resolved
     * usernames and permission labels for direct UI consumption.
     */
    @Transactional(readOnly = true)
    public List<com.waad.tba.modules.rbac.dto.UserPermissionOverrideDto> getOverrideDtosForUser(Long userId) {
        List<UserPermissionOverride> overrides = getOverridesForUser(userId);
        if (overrides.isEmpty()) {
            return List.of();
        }

        java.util.Set<Long> userIds = new java.util.HashSet<>();
        java.util.Set<String> permissionCodes = new java.util.HashSet<>();
        for (UserPermissionOverride o : overrides) {
            userIds.add(o.getGrantedBy());
            if (o.getRevokedBy() != null) userIds.add(o.getRevokedBy());
            permissionCodes.add(o.getPermissionCode());
        }
        java.util.Map<Long, String> usernames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        java.util.Map<String, String> permissionLabels = permissionRepository.findAllById(permissionCodes).stream()
                .collect(Collectors.toMap(Permission::getCode, Permission::getLabelAr));

        return overrides.stream()
                .map(o -> com.waad.tba.modules.rbac.dto.UserPermissionOverrideDto.builder()
                        .id(o.getId())
                        .permissionCode(o.getPermissionCode())
                        .permissionLabelAr(permissionLabels.get(o.getPermissionCode()))
                        .effect(o.getEffect())
                        .reason(o.getReason())
                        .grantedByUserId(o.getGrantedBy())
                        .grantedByUsername(usernames.get(o.getGrantedBy()))
                        .createdAt(o.getCreatedAt())
                        .expiresAt(o.getExpiresAt())
                        .revokedAt(o.getRevokedAt())
                        .revokedByUserId(o.getRevokedBy())
                        .revokedByUsername(o.getRevokedBy() != null ? usernames.get(o.getRevokedBy()) : null)
                        .active(o.isActive())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Deactivate an existing override (sets revokedAt/revokedBy) — this is
     * the "cancel this exception" operation, distinct from creating a new
     * REVOKE-effect override (which is itself a new audited exception, for
     * taking away a permission the role would otherwise grant). Same
     * critical-security/actor restrictions as {@link #createOverride} apply,
     * since deactivating a GRANT for a critical-security permission has the
     * same practical effect as revoking one.
     */
    @Transactional
    public void deactivateOverride(User actor, Long expectedUserId, Long overrideId, String reason) {
        UserPermissionOverride override = overrideRepository.findById(overrideId)
                .orElseThrow(() -> new ResourceNotFoundException("UserPermissionOverride", "id", overrideId));

        if (expectedUserId != null && !expectedUserId.equals(override.getUserId())) {
            throw new ResourceNotFoundException("UserPermissionOverride", "id", overrideId);
        }

        if (!override.isActive()) {
            throw new IllegalArgumentException("This override is already inactive");
        }

        Permission permission = permissionRepository.findById(override.getPermissionCode()).orElse(null);
        boolean actorIsWaadAdmin = "WAAD_ADMIN".equals(actor.getUserType());
        if (actorIsWaadAdmin && permission != null && Boolean.TRUE.equals(permission.getCriticalSecurity())) {
            throw new AccessDeniedException(
                    "WAAD_ADMIN cannot deactivate an override for a critical-security permission ("
                            + override.getPermissionCode() + ")");
        }

        override.setRevokedAt(LocalDateTime.now());
        override.setRevokedBy(actor.getId());
        overrideRepository.save(override);

        securityService.auditLog(override.getUserId(), UserAuditLog.ACTION_PERMISSION_OVERRIDE_REVOKED,
                String.format("Deactivated override '%s' (%s) by %s (%s)%s",
                        override.getPermissionCode(), override.getEffect(), actor.getUsername(), actor.getUserType(),
                        reason != null && !reason.isBlank() ? ": " + reason : ""),
                null, null, actor.getId());
    }
}
