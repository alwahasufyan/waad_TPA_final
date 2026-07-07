package com.waad.tba.modules.claim.dto;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for REVIEWER actions on claims.
 * 
 * SECURITY: This DTO can ONLY be used by REVIEWER and INSURANCE_ADMIN roles.
 * PROVIDERS and EMPLOYER_ADMIN are NOT allowed to use this endpoint.
 * 
 * Allowed actions:
 * - Change status (via state machine)
 * - Add reviewer comment
 * - Set approved amount
 * 
 * NOT allowed:
 * - Modify claim data (doctorName, diagnosisCode, lines, etc.)
 * 
 * @since Provider Portal Security Fix (Phase 0)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimReviewDto {
    
    /**
     * New status for the claim
     * Must follow state machine transitions
     */
    @NotNull(message = "Status is required")
    private ClaimStatus status;
    
    /**
     * Reviewer comment
     * MANDATORY for REJECTED and NEEDS_CORRECTION statuses
     */
    private String reviewerComment;
    
    /**
     * Approved amount (for APPROVED status)
     * Must be > 0 and <= requestedAmount
     */
    @DecimalMin(value = "0.01", message = "Approved amount must be greater than 0")
    private BigDecimal approvedAmount;
}
