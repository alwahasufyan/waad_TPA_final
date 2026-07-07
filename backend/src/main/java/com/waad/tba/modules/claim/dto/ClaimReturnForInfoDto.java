package com.waad.tba.modules.claim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for returning a claim for additional information.
 * 
 * Used by: POST /api/claims/{id}/return-for-info
 * 
 * Business Rules:
 * - reason is MANDATORY (explain what info is needed)
 * - Claim will be moved to RETURNED_FOR_INFO status
 * - Member can edit and resubmit
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimReturnForInfoDto {
    
    /**
     * Mandatory reason explaining what additional information is needed.
     */
    @NotBlank(message = "سبب طلب المعلومات الإضافية مطلوب")
    @Size(min = 10, max = 2000, message = "السبب يجب أن يكون بين 10 و 2000 حرف")
    private String reason;
    
    /**
     * Optional list of specific documents or information required.
     */
    private String requiredDocuments;
}
