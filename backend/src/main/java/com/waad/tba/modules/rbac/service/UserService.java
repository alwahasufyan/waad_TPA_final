package com.waad.tba.modules.rbac.service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.rbac.dto.UserCreateDto;
import com.waad.tba.modules.rbac.dto.UserResponseDto;
import com.waad.tba.modules.rbac.dto.UserUpdateDto;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.entity.UserAuditLog;
import com.waad.tba.modules.rbac.exception.PasswordPolicyViolationException;
import com.waad.tba.modules.rbac.mapper.UserMapper;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * User Service - RBAC Hardened
 *
 * SECURITY HARDENING (2026-01-13):
 * - Role hierarchy enforcement on all write operations
 * - SUPER_ADMIN protection on delete/update
 * - Privilege escalation prevention
 *
 * WAAD-RBAC-PHASE-1-FOUNDATION (2026-07-31):
 * - SUPER_ADMIN can no longer be demoted (userType changed) by anyone, not
 *   just protected from delete/deactivate.
 * - WAAD_ADMIN actors are blocked from creating, updating, deleting, or
 *   toggling the status of a SUPER_ADMIN account, and from assigning the
 *   SUPER_ADMIN role to anyone (ticket rule 2).
 *
 * RBAC-LEGACY-USER-MANAGEMENT-CONTROLLER-CLEANUP-1 (2026-07-31):
 * - Added resetPasswordByAdmin(), the consolidated replacement for the
 *   legacy systemadmin.UserManagementController's reset-password endpoint,
 *   which had no SUPER_ADMIN-account protection at all. WAAD_ADMIN cannot
 *   reset a SUPER_ADMIN's credentials through this path.
 *
 * @version 3.1 - RBAC-LEGACY-USER-MANAGEMENT-CONTROLLER-CLEANUP-1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String WAAD_ADMIN = "WAAD_ADMIN";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserSecurityService securityService;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public List<UserResponseDto> findAll() {
        log.debug("Finding all users");
        return userRepository.findAll().stream()
                .map(userMapper::toResponseDto)
                .collect(Collectors.toList());
    }
    
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public UserResponseDto findById(Long id) {
        log.debug("Finding user by id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return userMapper.toResponseDto(user);
    }

    @Transactional
    public UserResponseDto create(UserCreateDto dto) {
        log.info("Creating new user: {}", dto.getUsername());
        
        // Uniqueness checks
        if (userRepository.existsByUsernameIgnoreCase(dto.getUsername())) {
            throw new IllegalArgumentException("اسم المستخدم '" + dto.getUsername() + "' موجود مسبقاً");
        }
        
        if (userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new IllegalArgumentException("البريد الإلكتروني '" + dto.getEmail() + "' مسجل مسبقاً");
        }

        // Password policy check (username match)
        if (dto.getPassword().equalsIgnoreCase(dto.getUsername())) {
            throw new PasswordPolicyViolationException("Password cannot be the same as username",
                    java.util.Collections.singletonList("PASSWORD_SAME_AS_USERNAME"));
        }

        String resolvedUserType = resolveUserType(dto.getUserType(), dto.getEmployerId(), dto.getProviderId());
        if (isActorWaadAdmin() && SUPER_ADMIN.equals(resolvedUserType)) {
            log.error("⛔ WAAD_ADMIN attempted to create a SUPER_ADMIN user: {}", dto.getUsername());
            throw new AccessDeniedException("WAAD_ADMIN cannot create SUPER_ADMIN users");
        }

        User user = userMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        applyRoleBindings(user, resolvedUserType, dto.getEmployerId(), dto.getProviderId());

        User savedUser = userRepository.save(user);
        
        // Send email verification
        securityService.sendEmailVerification(savedUser);
        
        // Audit log
        securityService.auditLog(savedUser.getId(), UserAuditLog.ACTION_USER_CREATED,
                "User created: " + dto.getUsername(), null, null, null);
        
        log.info("User created successfully with id: {}", savedUser.getId());
        
        return userMapper.toResponseDto(savedUser);
    }

    @Transactional
    public UserResponseDto update(Long id, UserUpdateDto dto) {
        log.info("Updating user with id: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (isActorWaadAdmin() && user.isSuperAdmin()) {
            log.error("⛔ WAAD_ADMIN attempted to update SUPER_ADMIN user: id={}, username={}", id, user.getUsername());
            throw new AccessDeniedException("WAAD_ADMIN cannot manage SUPER_ADMIN users");
        }

        // Check email uniqueness if changed
        if (!user.getEmail().equalsIgnoreCase(dto.getEmail()) && userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new IllegalArgumentException("البريد الإلكتروني '" + dto.getEmail() + "' مسجل مسبقاً");
        }

        String resolvedUserType = resolveUserType(dto.getUserType(), dto.getEmployerId(), dto.getProviderId());

        // PROTECTION: SUPER_ADMIN can never be demoted, by anyone (including
        // another SUPER_ADMIN acting by mistake) — only delete/deactivate were
        // previously guarded; a role change is an equally effective escalation
        // vector (e.g. silently dropping SUPER_ADMIN privileges).
        if (user.isSuperAdmin() && !SUPER_ADMIN.equals(resolvedUserType)) {
            log.error("⛔ Attempt to change role of SUPER_ADMIN user: id={}, username={}, requestedType={}",
                    id, user.getUsername(), resolvedUserType);
            throw new IllegalArgumentException("Cannot change the role of a SUPER_ADMIN user");
        }
        if (isActorWaadAdmin() && SUPER_ADMIN.equals(resolvedUserType)) {
            log.error("⛔ WAAD_ADMIN attempted to assign SUPER_ADMIN role: id={}", id);
            throw new AccessDeniedException("WAAD_ADMIN cannot assign the SUPER_ADMIN role");
        }

        String oldEmail = user.getEmail();
        userMapper.updateEntityFromDto(user, dto);
        applyRoleBindings(user, resolvedUserType, dto.getEmployerId(), dto.getProviderId());
        User updatedUser = userRepository.save(user);
        
        // Audit log
        securityService.auditLog(id, UserAuditLog.ACTION_USER_UPDATED,
                "User updated" + (oldEmail.equals(dto.getEmail()) ? "" : ", email changed"),
                null, null, null);
        
        log.info("User updated successfully: {}", id);
        return userMapper.toResponseDto(updatedUser);
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting user with id: {}", id);
        
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", "id", id);
        }
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        boolean isSuperAdmin = user.isSuperAdmin();

        if (isSuperAdmin) {
            log.error("⛔ Attempt to delete SUPER_ADMIN user: id={}, username={}", id, user.getUsername());
            throw new IllegalArgumentException("Cannot delete SUPER_ADMIN user");
        }
        if (isActorWaadAdmin() && isWaadAdmin(user)) {
            // WAAD_ADMIN manages "normal users and operational roles" — not peer
            // WAAD_ADMIN accounts either, to avoid one WAAD_ADMIN removing another.
            log.error("⛔ WAAD_ADMIN attempted to delete a WAAD_ADMIN user: id={}", id);
            throw new AccessDeniedException("WAAD_ADMIN cannot delete another WAAD_ADMIN user");
        }

        // Audit log before deletion
        securityService.auditLog(id, UserAuditLog.ACTION_USER_DELETED,
                "User deleted (soft delete)", null, null, null);
        
        userRepository.deleteById(id);
        log.info("User deleted successfully: {}", id);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> search(String query) {
        log.debug("Searching users with query: {}", query);
        return userRepository.searchUsers(query).stream()
                .map(userMapper::toResponseDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Find users not assigned to any provider
     * Used in provider management for linking users to providers
     */
    @Transactional(readOnly = true)
    public List<UserResponseDto> findUnassignedProviders() {
        log.debug("Finding users not assigned to any provider");
        return userRepository.findByProviderIdIsNull().stream()
                .map(userMapper::toResponseDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Find users assigned to a specific provider
     * Used in provider management to show account manager
     */
    @Transactional(readOnly = true)
    public List<UserResponseDto> findByProviderId(Long providerId) {
        log.debug("Finding users assigned to provider: {}", providerId);
        return userRepository.findByProviderId(providerId).stream()
                .map(userMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDto> findAllPaginated(Pageable pageable) {
        log.debug("Finding users with pagination");
        return userRepository.findAll(pageable)
                .map(userMapper::toResponseDto);
    }

    @Transactional(readOnly = true)
    public User findByUsernameOrEmail(String identifier) {
        return userRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with identifier: " + identifier));
    }

    /**
     * Toggle user active status (activate/deactivate)
     * SUPER_ADMIN users cannot be deactivated.
     */
    @Transactional
    public UserResponseDto toggleStatus(Long id) {
        log.info("Toggling status for user: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // PROTECTION: SUPER_ADMIN cannot be deactivated
        boolean isSuperAdmin = user.isSuperAdmin();

        if (isSuperAdmin && Boolean.TRUE.equals(user.getActive())) {
            log.error("⛔ Attempt to deactivate SUPER_ADMIN user: id={}, username={}", id, user.getUsername());
            throw new IllegalArgumentException("لا يمكن تعطيل مستخدم SUPER_ADMIN");
        }
        if (isActorWaadAdmin() && isSuperAdmin) {
            log.error("⛔ WAAD_ADMIN attempted to toggle status of SUPER_ADMIN user: id={}", id);
            throw new AccessDeniedException("WAAD_ADMIN cannot manage SUPER_ADMIN users");
        }

        // Toggle the status
        boolean newStatus = !Boolean.TRUE.equals(user.getActive());
        user.setActive(newStatus);
        User savedUser = userRepository.save(user);
        
        // Audit log
        String action = newStatus ? UserAuditLog.ACTION_USER_ACTIVATED : UserAuditLog.ACTION_USER_DEACTIVATED;
        String details = newStatus ? "User activated" : "User deactivated";
        securityService.auditLog(id, action, details, null, null, null);
        
        log.info("User {} status changed to: {}", id, newStatus ? "ACTIVE" : "INACTIVE");
        return userMapper.toResponseDto(savedUser);
    }

    /**
     * Reset a user's password as an admin action (SUPER_ADMIN or WAAD_ADMIN).
     *
     * RBAC-LEGACY-USER-MANAGEMENT-CONTROLLER-CLEANUP-1: this is the
     * consolidated replacement for the legacy
     * systemadmin.UserManagementController's reset-password endpoint, which
     * had no target-account protection at all — any SUPER_ADMIN-only caller
     * (originally) could reset ANY user's password including another
     * SUPER_ADMIN's, and once WAAD_ADMIN was introduced there was no
     * mechanism stopping it from resetting a SUPER_ADMIN's credentials
     * either (a full account-takeover vector). WAAD_ADMIN is blocked here;
     * SUPER_ADMIN remains unrestricted (it may reset its own or another
     * SUPER_ADMIN's password — resetting is not the same privilege-removal
     * concern as delete/deactivate/demote).
     */
    @Transactional
    public void resetPasswordByAdmin(Long id, String newPassword) {
        log.info("Admin password reset for user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (isActorWaadAdmin() && user.isSuperAdmin()) {
            log.error("⛔ WAAD_ADMIN attempted to reset password for SUPER_ADMIN user: id={}", id);
            throw new AccessDeniedException("WAAD_ADMIN cannot reset SUPER_ADMIN credentials");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        securityService.auditLog(id, UserAuditLog.ACTION_PASSWORD_RESET,
                "Password reset by admin", null, null, null);

        log.info("Password reset successfully for user: {}", id);
    }

    /**
     * True when the currently authenticated actor is a WAAD_ADMIN. Returns
     * false (unrestricted) when there is no authenticated actor at all — e.g.
     * a startup/system-initiated call — since those are trusted callers, not
     * a WAAD_ADMIN acting through the API.
     */
    private boolean isActorWaadAdmin() {
        User actor = authorizationService.getCurrentUser();
        return actor != null && WAAD_ADMIN.equals(actor.getUserType());
    }

    private boolean isWaadAdmin(User user) {
        return WAAD_ADMIN.equals(user.getUserType());
    }

    private String resolveUserType(String requestedUserType, Long employerId, Long providerId) {
        if (requestedUserType != null && !requestedUserType.isBlank()) {
            return requestedUserType.trim().toUpperCase(Locale.ROOT);
        }

        if (employerId != null && providerId != null) {
            throw new IllegalArgumentException("User cannot be linked to both employerId and providerId");
        }
        if (employerId != null) {
            return "EMPLOYER_ADMIN";
        }
        if (providerId != null) {
            return "PROVIDER_STAFF";
        }
        return "DATA_ENTRY";
    }

    private void applyRoleBindings(User user, String userType, Long employerId, Long providerId) {
        user.setUserType(userType);

        if ("EMPLOYER_ADMIN".equals(userType)) {
            if (employerId == null) {
                throw new IllegalArgumentException("employerId is required for EMPLOYER_ADMIN");
            }
            user.setEmployerId(employerId);
            user.setProviderId(null);
            return;
        }

        if ("PROVIDER_STAFF".equals(userType)) {
            if (providerId == null) {
                throw new IllegalArgumentException("providerId is required for PROVIDER_STAFF");
            }
            user.setProviderId(providerId);
            user.setEmployerId(null);
            return;
        }

        if (employerId != null || providerId != null) {
            throw new IllegalArgumentException("employerId/providerId are only allowed for EMPLOYER_ADMIN or PROVIDER_STAFF");
        }

        user.setEmployerId(null);
        user.setProviderId(null);
    }
}
