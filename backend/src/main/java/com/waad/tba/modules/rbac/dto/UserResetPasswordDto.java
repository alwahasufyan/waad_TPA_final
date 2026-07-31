package com.waad.tba.modules.rbac.dto;

import com.waad.tba.common.validation.PasswordPolicy;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RBAC-LEGACY-USER-MANAGEMENT-CONTROLLER-CLEANUP-1: request body for the
 * consolidated admin password-reset endpoint (replaces the legacy
 * systemadmin.UserManagementController path, which had no SUPER_ADMIN
 * protection).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResetPasswordDto {

    @NotBlank(message = "New password is required")
    @PasswordPolicy
    private String newPassword;
}
