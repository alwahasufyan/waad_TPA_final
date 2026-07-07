package com.waad.tba.modules.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for email verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEmailDto {
    
    @NotBlank(message = "Verification token is required")
    private String token;
}
