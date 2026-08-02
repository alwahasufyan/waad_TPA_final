package com.waad.tba.modules.rbac.service;

import com.waad.tba.modules.rbac.dto.UserCreateDto;
import com.waad.tba.modules.rbac.dto.UserResponseDto;
import com.waad.tba.modules.rbac.dto.UserUpdateDto;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * WAAD-RBAC-PHASE-1-FOUNDATION: covers the SUPER_ADMIN protections that
 * predate this ticket (delete/deactivate) plus the new protections it adds
 * (role-change/demotion protection, WAAD_ADMIN's inability to manage
 * SUPER_ADMIN accounts or assign the SUPER_ADMIN role).
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.waad.tba.modules.rbac.mapper.UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserSecurityService securityService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private EffectivePermissionService effectivePermissionService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private User superAdminUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .userType("DATA_ENTRY")
                .active(true)
                .build();

        superAdminUser = User.builder()
                .id(99L)
                .username("root")
                .email("root@waad.ly")
                .userType("SUPER_ADMIN")
                .active(true)
                .build();
    }

    @Test
    void testFindUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userMapper.toResponseDto(testUser)).thenReturn(com.waad.tba.modules.rbac.dto.UserResponseDto.builder().username("testuser").build());

        var result = userService.findById(1L);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void testFindUserById_NotFound() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(com.waad.tba.common.exception.ResourceNotFoundException.class, () -> {
            userService.findById(2L);
        });
    }

    // ============================================================
    // SUPER_ADMIN protection (delete/deactivate — pre-existing)
    // ============================================================

    @Test
    void delete_superAdmin_isBlocked_regardlessOfActor() {
        when(userRepository.existsById(99L)).thenReturn(true);
        when(userRepository.findById(99L)).thenReturn(Optional.of(superAdminUser));

        assertThrows(IllegalArgumentException.class, () -> userService.delete(99L));
        verify(userRepository, never()).deleteById(anyLong());
    }

    @Test
    void toggleStatus_superAdmin_cannotBeDeactivated() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(superAdminUser));

        assertThrows(IllegalArgumentException.class, () -> userService.toggleStatus(99L));
        verify(userRepository, never()).save(any(User.class));
    }

    // ============================================================
    // NEW: SUPER_ADMIN role-change (demotion) protection
    // ============================================================

    @Test
    void update_superAdmin_roleChangeIsBlocked() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(superAdminUser));

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Root")
                .email("root@waad.ly")
                .userType("DATA_ENTRY")
                .build();

        assertThrows(IllegalArgumentException.class, () -> userService.update(99L, dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void update_superAdmin_keepingSuperAdminRole_isAllowed() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(superAdminUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponseDto(any(User.class))).thenReturn(UserResponseDto.builder().build());

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Root")
                .email("root@waad.ly")
                .userType("SUPER_ADMIN")
                .build();

        assertDoesNotThrow(() -> userService.update(99L, dto));
        verify(userRepository, times(1)).save(any(User.class));
    }

    // ============================================================
    // NEW: WAAD_ADMIN cannot manage SUPER_ADMIN accounts
    // ============================================================

    @Test
    void update_waadAdminActor_cannotUpdateSuperAdminTarget() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        when(authorizationService.getCurrentUser()).thenReturn(waadAdminActor);
        when(userRepository.findById(99L)).thenReturn(Optional.of(superAdminUser));

        UserUpdateDto dto = UserUpdateDto.builder().fullName("Root").email("root@waad.ly").userType("SUPER_ADMIN").build();

        assertThrows(AccessDeniedException.class, () -> userService.update(99L, dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void delete_waadAdminActor_cannotDeleteWaadAdminPeer() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        User peerWaadAdmin = User.builder().id(6L).username("peer").userType("WAAD_ADMIN").build();
        when(authorizationService.getCurrentUser()).thenReturn(waadAdminActor);
        when(userRepository.existsById(6L)).thenReturn(true);
        when(userRepository.findById(6L)).thenReturn(Optional.of(peerWaadAdmin));

        assertThrows(AccessDeniedException.class, () -> userService.delete(6L));
        verify(userRepository, never()).deleteById(anyLong());
    }

    @Test
    void toggleStatus_waadAdminActor_cannotToggleSuperAdmin() {
        // Inactive, so the pre-existing "cannot deactivate an active SUPER_ADMIN"
        // guard doesn't fire first — this isolates the new WAAD_ADMIN-specific check.
        User inactiveSuperAdmin = User.builder().id(99L).username("root").userType("SUPER_ADMIN").active(false).build();
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        when(authorizationService.getCurrentUser()).thenReturn(waadAdminActor);
        when(userRepository.findById(99L)).thenReturn(Optional.of(inactiveSuperAdmin));

        assertThrows(AccessDeniedException.class, () -> userService.toggleStatus(99L));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void create_waadAdminActor_cannotAssignSuperAdminRole() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        when(authorizationService.getCurrentUser()).thenReturn(waadAdminActor);

        UserCreateDto dto = UserCreateDto.builder()
                .username("newadmin")
                .password("Str0ng!Pass")
                .fullName("New Admin")
                .email("newadmin@waad.ly")
                .userType("SUPER_ADMIN")
                .build();

        assertThrows(AccessDeniedException.class, () -> userService.create(dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void update_waadAdminActor_cannotPromoteUserToSuperAdmin() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        when(authorizationService.getCurrentUser()).thenReturn(waadAdminActor);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Test User")
                .email("test@example.com")
                .userType("SUPER_ADMIN")
                .build();

        assertThrows(AccessDeniedException.class, () -> userService.update(1L, dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void update_waadAdminActor_canManageOrdinaryUser() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        when(authorizationService.getCurrentUser()).thenReturn(waadAdminActor);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponseDto(any(User.class))).thenReturn(UserResponseDto.builder().build());

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Test User Updated")
                .email("test@example.com")
                .userType("MEDICAL_REVIEWER")
                .build();

        assertDoesNotThrow(() -> userService.update(1L, dto));
        verify(userRepository, times(1)).save(any(User.class));
    }

    // ============================================================
    // RBAC-LEGACY-USER-MANAGEMENT-CONTROLLER-CLEANUP-1:
    // resetPasswordByAdmin() — consolidated replacement for the legacy
    // systemadmin.UserManagementController reset-password endpoint, which
    // had no SUPER_ADMIN-account protection at all.
    // ============================================================

    @Test
    void resetPasswordByAdmin_waadAdminActor_cannotResetSuperAdminPassword() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        when(authorizationService.getCurrentUser()).thenReturn(waadAdminActor);
        when(userRepository.findById(99L)).thenReturn(Optional.of(superAdminUser));

        assertThrows(AccessDeniedException.class, () -> userService.resetPasswordByAdmin(99L, "NewStr0ng!Pass"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordByAdmin_waadAdminActor_canResetOrdinaryUserPassword() {
        User waadAdminActor = User.builder().id(5L).username("waadadmin").userType("WAAD_ADMIN").build();
        when(authorizationService.getCurrentUser()).thenReturn(waadAdminActor);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> userService.resetPasswordByAdmin(1L, "NewStr0ng!Pass"));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void resetPasswordByAdmin_superAdminActor_canResetSuperAdminPassword() {
        when(authorizationService.getCurrentUser()).thenReturn(superAdminUser);
        when(userRepository.findById(99L)).thenReturn(Optional.of(superAdminUser));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> userService.resetPasswordByAdmin(99L, "NewStr0ng!Pass"));
        verify(userRepository, times(1)).save(any(User.class));
    }

    // ============================================================
    // SUPER_ADMIN actor: unrestricted (sanity check)
    // ============================================================

    @Test
    void create_superAdminActor_canCreateSuperAdmin() {
        when(authorizationService.getCurrentUser()).thenReturn(superAdminUser);
        when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userMapper.toEntity(any(UserCreateDto.class))).thenReturn(User.builder().username("newadmin2").build());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponseDto(any(User.class))).thenReturn(UserResponseDto.builder().build());

        UserCreateDto dto = UserCreateDto.builder()
                .username("newadmin2")
                .password("Str0ng!Pass")
                .fullName("New Admin 2")
                .email("newadmin2@waad.ly")
                .userType("SUPER_ADMIN")
                .build();

        assertDoesNotThrow(() -> userService.create(dto));
        verify(userRepository, times(1)).save(any(User.class));
    }

    // ============================================================
    // WAAD-RBAC-PER-USER-LANDING-PAGE-1: default landing page validation
    // ============================================================

    @Test
    void update_defaultLandingPage_savedWhenPermissionIsEffective() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(effectivePermissionService.getEffectivePermissions(testUser)).thenReturn(Set.of("dashboard.read", "claims.read"));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponseDto(any(User.class))).thenReturn(UserResponseDto.builder().build());

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Test User")
                .email("test@example.com")
                .userType("DATA_ENTRY")
                .defaultLandingPage("/dashboard")
                .defaultLandingPagePermission("dashboard.read")
                .build();

        assertDoesNotThrow(() -> userService.update(1L, dto));
        assertEquals("/dashboard", testUser.getDefaultLandingPage());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void update_defaultLandingPage_rejectedWhenPermissionNotEffective() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(effectivePermissionService.getEffectivePermissions(testUser)).thenReturn(Set.of("claims.read"));

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Test User")
                .email("test@example.com")
                .userType("DATA_ENTRY")
                .defaultLandingPage("/settings/system")
                .defaultLandingPagePermission("settings.manage")
                .build();

        assertThrows(IllegalArgumentException.class, () -> userService.update(1L, dto));
        assertNull(testUser.getDefaultLandingPage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void update_defaultLandingPage_rejectedWhenPermissionCodeMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Test User")
                .email("test@example.com")
                .userType("DATA_ENTRY")
                .defaultLandingPage("/dashboard")
                // defaultLandingPagePermission intentionally omitted
                .build();

        assertThrows(IllegalArgumentException.class, () -> userService.update(1L, dto));
        verify(userRepository, never()).save(any(User.class));
        verify(effectivePermissionService, never()).getEffectivePermissions(any(User.class));
    }

    @Test
    void update_defaultLandingPage_nullClearsExistingValue() {
        testUser.setDefaultLandingPage("/dashboard");
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponseDto(any(User.class))).thenReturn(UserResponseDto.builder().build());

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Test User")
                .email("test@example.com")
                .userType("DATA_ENTRY")
                .defaultLandingPage(null)
                .build();

        assertDoesNotThrow(() -> userService.update(1L, dto));
        assertNull(testUser.getDefaultLandingPage());
        verify(effectivePermissionService, never()).getEffectivePermissions(any(User.class));
    }
}
