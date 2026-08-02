package com.waad.tba.modules.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI: request body for granting or
 * revoking a permission for one specific user (an audited exception to
 * their role's normal permission set).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOverrideRequestDto {

    @NotBlank(message = "permissionCode is required")
    private String permissionCode;

    @NotBlank(message = "effect is required (GRANT or REVOKE)")
    private String effect;

    @NotBlank(message = "reason is required")
    private String reason;

    private LocalDateTime expiresAt;
}
