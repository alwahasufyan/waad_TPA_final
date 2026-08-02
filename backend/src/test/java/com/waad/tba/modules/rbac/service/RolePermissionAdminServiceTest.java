package com.waad.tba.modules.rbac.service;

import com.waad.tba.modules.rbac.dto.AuditLogEntryDto;
import com.waad.tba.modules.rbac.dto.RoleSummaryDto;
import com.waad.tba.modules.rbac.entity.Permission;
import com.waad.tba.modules.rbac.entity.RolePermission;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.entity.UserAuditLog;
import com.waad.tba.modules.rbac.repository.PermissionRepository;
import com.waad.tba.modules.rbac.repository.RolePermissionRepository;
import com.waad.tba.modules.rbac.repository.UserAuditLogRepository;
import com.waad.tba.modules.rbac.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1: role permission matrix backend —
 * SUPER_ADMIN's fixed/uneditable permission set and WAAD_ADMIN's inability to
 * touch critical-security permissions for any role.
 */
@ExtendWith(MockitoExtension.class)
class RolePermissionAdminServiceTest {

    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserSecurityService securityService;
    @Mock private UserAuditLogRepository auditLogRepository;

    @InjectMocks
    private RolePermissionAdminService service;

    private User waadAdminActor;
    private User superAdminActor;

    @BeforeEach
    void setUp() {
        waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        superAdminActor = User.builder().id(1L).username("root").userType("SUPER_ADMIN").build();
    }

    @Test
    void getRoles_markSuperAdminNonEditable_andBeneficiaryReserved() {
        when(permissionRepository.count()).thenReturn(14L);
        when(rolePermissionRepository.findByRole(anyString())).thenReturn(List.of());
        when(userRepository.countByUserType(anyString())).thenReturn(0L);

        List<RoleSummaryDto> roles = service.getRoles();

        RoleSummaryDto superAdmin = roles.stream().filter(r -> r.getRole().equals("SUPER_ADMIN")).findFirst().orElseThrow();
        assertFalse(superAdmin.isEditable());
        assertEquals(14, superAdmin.getPermissionCount());

        RoleSummaryDto beneficiary = roles.stream().filter(r -> r.getRole().equals("BENEFICIARY")).findFirst().orElseThrow();
        assertTrue(beneficiary.isReserved());

        RoleSummaryDto waadAdmin = roles.stream().filter(r -> r.getRole().equals("WAAD_ADMIN")).findFirst().orElseThrow();
        assertTrue(waadAdmin.isEditable());
    }

    @Test
    void updateRolePermissions_cannotEditSuperAdmin() {
        assertThrows(AccessDeniedException.class,
                () -> service.updateRolePermissions("SUPER_ADMIN", List.of("claims.read"), waadAdminActor));
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void updateRolePermissions_rejectsUnknownRole() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateRolePermissions("NOT_A_ROLE", List.of("claims.read"), waadAdminActor));
    }

    @Test
    void updateRolePermissions_rejectsUnknownPermissionCode() {
        when(permissionRepository.findAll()).thenReturn(List.of(
                Permission.builder().code("claims.read").build()));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateRolePermissions("MEDICAL_REVIEWER", List.of("not.a.real.code"), waadAdminActor));
    }

    @Test
    void updateRolePermissions_waadAdminActor_cannotToggleCriticalPermission() {
        when(permissionRepository.findAll()).thenReturn(List.of(
                Permission.builder().code("claims.read").build(),
                Permission.builder().code("settings.manage").criticalSecurity(true).build()));
        when(permissionRepository.findByCriticalSecurityTrue()).thenReturn(List.of(
                Permission.builder().code("settings.manage").criticalSecurity(true).build()));
        when(rolePermissionRepository.findByRole("MEDICAL_REVIEWER")).thenReturn(List.of(
                RolePermission.builder().role("MEDICAL_REVIEWER").permissionCode("claims.read").build()));

        // Requesting settings.manage added, which the role doesn't currently have.
        assertThrows(AccessDeniedException.class, () -> service.updateRolePermissions(
                "MEDICAL_REVIEWER", List.of("claims.read", "settings.manage"), waadAdminActor));
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void updateRolePermissions_waadAdminActor_canChangeNonCriticalPermissions() {
        when(permissionRepository.findAll()).thenReturn(List.of(
                Permission.builder().code("claims.read").build(),
                Permission.builder().code("reports.claims").build(),
                Permission.builder().code("settings.manage").criticalSecurity(true).build()));
        when(permissionRepository.findByCriticalSecurityTrue()).thenReturn(List.of(
                Permission.builder().code("settings.manage").criticalSecurity(true).build()));
        when(rolePermissionRepository.findByRole("MEDICAL_REVIEWER")).thenReturn(List.of(
                RolePermission.builder().role("MEDICAL_REVIEWER").permissionCode("claims.read").build()));

        assertDoesNotThrow(() -> service.updateRolePermissions(
                "MEDICAL_REVIEWER", List.of("claims.read", "reports.claims"), waadAdminActor));
        verify(rolePermissionRepository, times(1)).saveAll(any());
    }

    @Test
    void updateRolePermissions_superAdminActor_canChangeCriticalPermission() {
        when(permissionRepository.findAll()).thenReturn(List.of(
                Permission.builder().code("settings.manage").criticalSecurity(true).build()));
        when(rolePermissionRepository.findByRole("WAAD_ADMIN")).thenReturn(List.of());

        assertDoesNotThrow(() -> service.updateRolePermissions(
                "WAAD_ADMIN", List.of("settings.manage"), superAdminActor));
        verify(rolePermissionRepository, times(1)).saveAll(any());
    }

    @Test
    void getRolePermissionCodes_superAdmin_returnsFullCatalog() {
        when(permissionRepository.findAll()).thenReturn(List.of(
                Permission.builder().code("claims.read").build(),
                Permission.builder().code("settings.manage").build()));

        Set<String> codes = service.getRolePermissionCodes("SUPER_ADMIN");

        assertEquals(Set.of("claims.read", "settings.manage"), codes);
    }

    @Test
    void getAuditLog_resolvesUsernamesForTargetAndActor() {
        Pageable pageable = PageRequest.of(0, 20);
        UserAuditLog entry = UserAuditLog.builder()
                .id(1L)
                .action(UserAuditLog.ACTION_ROLE_PERMISSIONS_UPDATED)
                .details("Role MEDICAL_REVIEWER permissions updated")
                .userId(2L)
                .performedBy(5L)
                .build();
        when(auditLogRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(entry), pageable, 1));
        when(userRepository.findAllById(Set.of(2L, 5L))).thenReturn(List.of(
                User.builder().id(2L).username("reviewer").build(),
                User.builder().id(5L).username("waadadmin").build()));

        Page<AuditLogEntryDto> result = service.getAuditLog(null, null, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        AuditLogEntryDto dto = result.getContent().get(0);
        assertEquals("reviewer", dto.getTargetUsername());
        assertEquals("waadadmin", dto.getPerformedByUsername());
    }
}
