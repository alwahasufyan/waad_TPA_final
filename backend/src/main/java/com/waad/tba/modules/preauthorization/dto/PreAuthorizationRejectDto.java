package com.waad.tba.modules.preauthorization.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for rejecting a PreAuthorization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationRejectDto {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
    private String rejectionReason;
}
