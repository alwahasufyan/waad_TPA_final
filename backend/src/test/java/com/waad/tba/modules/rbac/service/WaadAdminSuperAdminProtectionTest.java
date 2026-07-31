package com.waad.tba.modules.rbac.service;

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WAAD-RBAC-PHASE-3A-WAAD-ADMIN-FULL-ACCESS, ticket-required test names
 * (rule: "WAAD_ADMIN is a full operational admin except SUPER_ADMIN
 * protection"). These exercise the exact protections in UserService — they
 * overlap in spirit with UserServiceTest's Phase 1 coverage but are kept
 * under the names this ticket asks for as the acceptance-criteria record for
 * this specific decision ("full access except these five things").
 */
@ExtendWith(MockitoExtension.class)
class WaadAdminSuperAdminProtectionTest {

    @Mock private UserRepository userRepository;
    @Mock private com.waad.tba.modules.rbac.mapper.UserMapper userMapper;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private UserSecurityService securityService;
    @Mock private AuthorizationService authorizationService;

    @InjectMocks
    private UserService userService;

    private User superAdmin;
    private User waadAdminActor;
    private User ordinaryUser;

    @BeforeEach
    void setUp() {
        superAdmin = User.builder().id(1L).username("root").userType("SUPER_ADMIN").active(true).build();
        waadAdminActor = User.builder().id(2L).username("waadadmin").userType("WAAD_ADMIN").build();
        ordinaryUser = User.builder().id(3L).username("reviewer").email("reviewer@waad.ly")
                .userType("MEDICAL_REVIEWER").active(true).build();
        lenient().when(authorizationService.getCurrentUser()).thenReturn(waadAdminActor);
    }

    @Test
    void waadAdmin_cannotDeleteSuperAdmin() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(superAdmin));

        assertThrows(IllegalArgumentException.class, () -> userService.delete(1L));
        verify(userRepository, never()).deleteById(anyLong());
    }

    @Test
    void waadAdmin_cannotDeactivateSuperAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(superAdmin));

        assertThrows(IllegalArgumentException.class, () -> userService.toggleStatus(1L));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void waadAdmin_cannotDemoteSuperAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(superAdmin));

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Root").email("root@waad.ly").userType("WAAD_ADMIN")
                .build();

        // Blocked twice over: (a) SUPER_ADMIN can never be demoted by anyone,
        // and (b) WAAD_ADMIN cannot touch a SUPER_ADMIN account at all.
        assertThrows(RuntimeException.class, () -> userService.update(1L, dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void waadAdmin_cannotAssignSuperAdminRole() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(ordinaryUser));

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Reviewer").email("reviewer@waad.ly").userType("SUPER_ADMIN")
                .build();

        assertThrows(AccessDeniedException.class, () -> userService.update(3L, dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void waadAdmin_canManageNormalUser() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(ordinaryUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponseDto(any(User.class))).thenReturn(UserResponseDto.builder().build());

        UserUpdateDto dto = UserUpdateDto.builder()
                .fullName("Reviewer Updated").email("reviewer@waad.ly").userType("MEDICAL_REVIEWER").active(true)
                .build();

        assertDoesNotThrow(() -> userService.update(3L, dto));
        verify(userRepository, times(1)).save(any(User.class));
    }
}
