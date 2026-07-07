package com.waad.tba.modules.claim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for rejecting a claim.
 * 
 * Used by: POST /api/claims/{id}/reject
 * 
 * Business Rules:
 * - rejectionReason is MANDATORY
 * - Claim will be moved to REJECTED status (terminal state)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimRejectDto {
    
    /**
     * Mandatory rejection reason.
     * Must explain why the claim was rejected.
     */
    @NotBlank(message = "سبب الرفض مطلوب")
    @Size(min = 10, max = 2000, message = "سبب الرفض يجب أن يكون بين 10 و 2000 حرف")
    private String rejectionReason;
    
    /**
     * Rejection category code for reporting.
     */
    private String rejectionCode;
}
