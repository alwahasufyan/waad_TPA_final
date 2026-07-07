package com.waad.tba.modules.claim.api.request;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * API v1 Request: Review Claim
 * 
 * SECURITY: This endpoint is for REVIEWER and INSURANCE_ADMIN only.
 * Reviewers can ONLY change status, add comments, and set approved amount.
 * They CANNOT modify claim data fields.
 * 
 * @since Provider Portal Security Fix (Phase 0)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewClaimRequest {
    
    @NotNull(message = "Status is required")
    private ClaimStatus status;
    
    @Size(max = 1000, message = "Reviewer comment must not exceed 1000 characters")
    private String reviewerComment;
    
    @DecimalMin(value = "0.01", message = "Approved amount must be greater than 0")
    private BigDecimal approvedAmount;
}
