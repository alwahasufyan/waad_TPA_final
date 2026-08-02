package com.waad.tba.modules.rbac.service;

import com.waad.tba.modules.rbac.entity.Permission;
import com.waad.tba.modules.rbac.entity.RolePermission;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.entity.UserPermissionOverride;
import com.waad.tba.modules.rbac.repository.PermissionRepository;
import com.waad.tba.modules.rbac.repository.RolePermissionRepository;
import com.waad.tba.modules.rbac.repository.UserPermissionOverrideRepository;
import com.waad.tba.modules.rbac.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WAAD-RBAC-PHASE-1-FOUNDATION: effective-permission calculation (role base
 * set + active GRANT/REVOKE overrides), and the WAAD_ADMIN critical-security
 * override restriction (ticket rule 2).
 */
@ExtendWith(MockitoExtension.class)
class EffectivePermissionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private UserPermissionOverrideRepository overrideRepository;
    @Mock private UserSecurityService securityService;

    @InjectMocks
    private EffectivePermissionService service;

    private User medicalReviewer;
    private User superAdmin;

    @BeforeEach
    void setUp() {
        medicalReviewer = User.builder().id(2L).username("reviewer").userType("MEDICAL_REVIEWER").build();
        superAdmin = User.builder().id(1L).username("root").userType("SUPER_ADMIN").build();
    }

    @Test
    void superAdmin_getsFullCatalog() {
        when(permissionRepository.findAll()).thenReturn(List.of(
                Permission.builder().code("claims.read").build(),
                Permission.builder().code("settings.manage").build()));

        Set<String> effective = service.getEffectivePermissions(superAdmin);

        assertEquals(Set.of("claims.read", "settings.manage"), effective);
    }

    @Test
    void ordinaryRole_getsRoleBaseSet_whenNoOverrides() {
        when(rolePermissionRepository.findByRole("MEDICAL_REVIEWER")).thenReturn(List.of(
                RolePermission.builder().role("MEDICAL_REVIEWER").permissionCode("claims.read").build(),
                RolePermission.builder().role("MEDICAL_REVIEWER").permissionCode("claims.review").build()));
        when(overrideRepository.findByUserIdAndRevokedAtIsNull(2L)).thenReturn(List.of());

        Set<String> effective = service.getEffectivePermissions(medicalReviewer);

        assertEquals(Set.of("claims.read", "claims.review"), effective);
    }

    @Test
    void grantOverride_addsPermissionNotInRoleBaseSet() {
        when(rolePermissionRepository.findByRole("MEDICAL_REVIEWER")).thenReturn(List.of(
                RolePermission.builder().role("MEDICAL_REVIEWER").permissionCode("claims.read").build()));
        when(overrideRepository.findByUserIdAndRevokedAtIsNull(2L)).thenReturn(List.of(
                UserPermissionOverride.builder().userId(2L).permissionCode("reports.financial_settlements")
                        .effect(UserPermissionOverride.EFFECT_GRANT).build()));

        Set<String> effective = service.getEffectivePermissions(medicalReviewer);

        assertTrue(effective.contains("reports.financial_settlements"));
        assertTrue(effective.contains("claims.read"));
    }

    @Test
    void revokeOverride_removesPermissionFromRoleBaseSet() {
        when(rolePermissionRepository.findByRole("MEDICAL_REVIEWER")).thenReturn(List.of(
                RolePermission.builder().role("MEDICAL_REVIEWER").permissionCode("claims.read").build(),
                RolePermission.builder().role("MEDICAL_REVIEWER").permissionCode("claims.review").build()));
        when(overrideRepository.findByUserIdAndRevokedAtIsNull(2L)).thenReturn(List.of(
                UserPermissionOverride.builder().userId(2L).permissionCode("claims.review")
                        .effect(UserPermissionOverride.EFFECT_REVOKE).build()));

        Set<String> effective = service.getEffectivePermissions(medicalReviewer);

        assertEquals(Set.of("claims.read"), effective);
    }

    @Test
    void expiredGrantOverride_isIgnored() {
        when(rolePermissionRepository.findByRole("MEDICAL_REVIEWER")).thenReturn(List.of());
        when(overrideRepository.findByUserIdAndRevokedAtIsNull(2L)).thenReturn(List.of(
                UserPermissionOverride.builder().userId(2L).permissionCode("settlements.approve")
                        .effect(UserPermissionOverride.EFFECT_GRANT)
                        .expiresAt(LocalDateTime.now().minusDays(1)).build()));

        Set<String> effective = service.getEffectivePermissions(medicalReviewer);

        assertTrue(effective.isEmpty());
    }

    // ============================================================
    // createOverride: WAAD_ADMIN cannot touch critical-security permissions
    // ============================================================

    @Test
    void createOverride_waadAdminActor_blockedForCriticalSecurityPermission() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(medicalReviewer));
        when(permissionRepository.findById("settings.manage")).thenReturn(Optional.of(
                Permission.builder().code("settings.manage").criticalSecurity(true).build()));

        assertThrows(AccessDeniedException.class, () -> service.createOverride(
                waadAdminActor, 2L, "settings.manage", UserPermissionOverride.EFFECT_GRANT, "test", null));
        verify(overrideRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createOverride_waadAdminActor_allowedForNonCriticalPermission() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(medicalReviewer));
        when(permissionRepository.findById("reports.financial_settlements")).thenReturn(Optional.of(
                Permission.builder().code("reports.financial_settlements").criticalSecurity(false).build()));
        when(overrideRepository.findByUserIdAndRevokedAtIsNull(2L)).thenReturn(List.of());
        when(overrideRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.createOverride(
                waadAdminActor, 2L, "reports.financial_settlements", UserPermissionOverride.EFFECT_GRANT, "test reason", null));
        verify(overrideRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createOverride_supersedesExistingActiveOverrideForSamePermission() {
        // WAAD-RBAC-OVERRIDE-DUPLICATE-GUARD-1: creating a new override for a
        // (user, permissionCode) pair that already has an active override must
        // deactivate the old one first, so at most one active override per
        // permission code ever exists — otherwise the admin UI (which tracks
        // only the latest active override per code) can no longer deactivate
        // an older, now-invisible duplicate that is still silently in effect.
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        UserPermissionOverride existingActive = UserPermissionOverride.builder()
                .id(50L).userId(2L).permissionCode("reports.financial_settlements")
                .effect(UserPermissionOverride.EFFECT_GRANT).grantedBy(1L).build();
        UserPermissionOverride unrelatedActive = UserPermissionOverride.builder()
                .id(51L).userId(2L).permissionCode("claims.read")
                .effect(UserPermissionOverride.EFFECT_GRANT).grantedBy(1L).build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(medicalReviewer));
        when(permissionRepository.findById("reports.financial_settlements")).thenReturn(Optional.of(
                Permission.builder().code("reports.financial_settlements").criticalSecurity(false).build()));
        when(overrideRepository.findByUserIdAndRevokedAtIsNull(2L)).thenReturn(List.of(existingActive, unrelatedActive));
        when(overrideRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        service.createOverride(waadAdminActor, 2L, "reports.financial_settlements",
                UserPermissionOverride.EFFECT_REVOKE, "second override, same permission", null);

        assertNotNull(existingActive.getRevokedAt());
        assertEquals(5L, existingActive.getRevokedBy());
        assertNull(unrelatedActive.getRevokedAt());
        // 1 save for superseding the old override + 1 save for the new one
        verify(overrideRepository, times(2)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createOverride_cannotTargetSuperAdmin() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(superAdmin));

        assertThrows(AccessDeniedException.class, () -> service.createOverride(
                waadAdminActor, 1L, "reports.financial_settlements", UserPermissionOverride.EFFECT_GRANT, "test", null));
    }

    // ============================================================
    // deactivateOverride: WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI
    // ============================================================

    @Test
    void deactivateOverride_setsRevokedAtAndBy() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        UserPermissionOverride override = UserPermissionOverride.builder()
                .id(10L).userId(2L).permissionCode("reports.financial_settlements")
                .effect(UserPermissionOverride.EFFECT_GRANT).grantedBy(1L).build();
        when(overrideRepository.findById(10L)).thenReturn(Optional.of(override));
        when(permissionRepository.findById("reports.financial_settlements")).thenReturn(Optional.of(
                Permission.builder().code("reports.financial_settlements").criticalSecurity(false).build()));
        when(overrideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deactivateOverride(waadAdminActor, 2L, 10L, "no longer needed");

        assertNotNull(override.getRevokedAt());
        assertEquals(5L, override.getRevokedBy());
        verify(overrideRepository).save(override);
    }

    @Test
    void deactivateOverride_waadAdminActor_blockedForCriticalSecurityPermission() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        UserPermissionOverride override = UserPermissionOverride.builder()
                .id(11L).userId(2L).permissionCode("settings.manage")
                .effect(UserPermissionOverride.EFFECT_GRANT).grantedBy(1L).build();
        when(overrideRepository.findById(11L)).thenReturn(Optional.of(override));
        when(permissionRepository.findById("settings.manage")).thenReturn(Optional.of(
                Permission.builder().code("settings.manage").criticalSecurity(true).build()));

        assertThrows(AccessDeniedException.class,
                () -> service.deactivateOverride(waadAdminActor, 2L, 11L, "test"));
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void deactivateOverride_alreadyInactive_throws() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        UserPermissionOverride override = UserPermissionOverride.builder()
                .id(12L).userId(2L).permissionCode("reports.claims")
                .effect(UserPermissionOverride.EFFECT_GRANT).grantedBy(1L)
                .revokedAt(LocalDateTime.now().minusDays(1)).revokedBy(1L).build();
        when(overrideRepository.findById(12L)).thenReturn(Optional.of(override));

        assertThrows(IllegalArgumentException.class,
                () -> service.deactivateOverride(waadAdminActor, 2L, 12L, "test"));
    }

    @Test
    void deactivateOverride_userIdMismatch_throwsNotFound() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        UserPermissionOverride override = UserPermissionOverride.builder()
                .id(13L).userId(2L).permissionCode("reports.claims")
                .effect(UserPermissionOverride.EFFECT_GRANT).grantedBy(1L).build();
        when(overrideRepository.findById(13L)).thenReturn(Optional.of(override));

        assertThrows(com.waad.tba.common.exception.ResourceNotFoundException.class,
                () -> service.deactivateOverride(waadAdminActor, 999L, 13L, "test"));
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void getOverrideDtosForUser_resolvesUsernamesAndLabels() {
        UserPermissionOverride override = UserPermissionOverride.builder()
                .id(20L).userId(2L).permissionCode("reports.claims")
                .effect(UserPermissionOverride.EFFECT_GRANT).reason("needs it").grantedBy(5L).build();
        when(overrideRepository.findByUserId(2L)).thenReturn(List.of(override));
        when(userRepository.findAllById(Set.of(5L))).thenReturn(List.of(
                User.builder().id(5L).username("waadadmin").build()));
        when(permissionRepository.findAllById(Set.of("reports.claims"))).thenReturn(List.of(
                Permission.builder().code("reports.claims").labelAr("تقارير المطالبات").build()));

        List<com.waad.tba.modules.rbac.dto.UserPermissionOverrideDto> dtos = service.getOverrideDtosForUser(2L);

        assertEquals(1, dtos.size());
        assertEquals("waadadmin", dtos.get(0).getGrantedByUsername());
        assertEquals("تقارير المطالبات", dtos.get(0).getPermissionLabelAr());
        assertTrue(dtos.get(0).isActive());
    }
}
