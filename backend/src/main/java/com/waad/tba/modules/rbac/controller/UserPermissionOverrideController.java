package com.waad.tba.modules.rbac.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.rbac.dto.CreateOverrideRequestDto;
import com.waad.tba.modules.rbac.dto.UserPermissionOverrideDto;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.service.EffectivePermissionService;
import com.waad.tba.security.AuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI: per-user permission overrides —
 * audited exceptions to a user's role-based permission set (grant something
 * their role doesn't have, or take away something it does). Backs Tab 3
 * ("الصلاحيات الخاصة للمستخدمين") of /admin/users.
 *
 * The business rules (cannot target SUPER_ADMIN, WAAD_ADMIN cannot touch
 * critical-security permissions, reason required) all live in
 * {@link EffectivePermissionService} — already built and tested in an
 * earlier ticket, this controller just exposes it.
 */
@RestController
@RequestMapping("/api/v1/admin/rbac/users/{userId}/overrides")
@RequiredArgsConstructor
@Tag(name = "RBAC - User Permission Overrides", description = "Per-user audited permission exceptions (WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI)")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN')")
public class UserPermissionOverrideController {

    private final EffectivePermissionService effectivePermissionService;
    private final AuthorizationService authorizationService;

    @GetMapping
    @Operation(summary = "List a user's permission overrides", description = "Active and historical, most recent first.")
    public ResponseEntity<ApiResponse<List<UserPermissionOverrideDto>>> list(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(effectivePermissionService.getOverrideDtosForUser(userId)));
    }

    @PostMapping
    @Operation(summary = "Create a permission override", description = "Grants or revokes one permission for this user specifically. SUPER_ADMIN targets are rejected. WAAD_ADMIN actors cannot touch critical-security permissions.")
    public ResponseEntity<ApiResponse<Void>> create(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody CreateOverrideRequestDto request) {
        User actor = authorizationService.requireCurrentUser();
        effectivePermissionService.createOverride(actor, userId, request.getPermissionCode(),
                request.getEffect(), request.getReason(), request.getExpiresAt());
        return ResponseEntity.ok(ApiResponse.success("Override created successfully", null));
    }

    @DeleteMapping("/{overrideId}")
    @Operation(summary = "Deactivate a permission override", description = "Cancels an existing override (sets revokedAt/revokedBy) — the user reverts to their role's normal permission for that code.")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable("userId") Long userId,
            @PathVariable("overrideId") Long overrideId,
            @RequestParam(name = "reason", required = false) String reason) {
        User actor = authorizationService.requireCurrentUser();
        effectivePermissionService.deactivateOverride(actor, userId, overrideId, reason);
        return ResponseEntity.ok(ApiResponse.success("Override deactivated successfully", null));
    }
}
