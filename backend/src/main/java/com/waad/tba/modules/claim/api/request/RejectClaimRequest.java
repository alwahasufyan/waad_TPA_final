package com.waad.tba.modules.claim.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Reject Claim Request
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * BUSINESS RULES:
 * - Rejection reason is MANDATORY (must explain why claim was rejected)
 * - Claim will be moved to REJECTED status (terminal state)
 * - No financial impact (claim is not paid)
 * 
 * WORKFLOW:
 * 1. Reviewer calls POST /api/v1/claims/{id}/reject with this request
 * 2. Backend validates mandatory rejection reason
 * 3. Backend transitions claim to REJECTED status
 * 4. Backend records rejection in audit log
 * 5. Backend returns ClaimResponse with REJECTED status
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectClaimRequest {
    
    /**
     * Mandatory rejection reason.
     * Must explain why the claim was rejected.
     * 
     * Examples:
     * - "خدمة غير مشمولة بالبوليصة"
     * - "مستندات ناقصة"
     * - "خطأ في المعلومات الطبية"
     * - "Service not covered by policy"
     * - "Insufficient documentation"
     */
    @NotBlank(message = "Rejection reason is required")
    @Size(min = 10, max = 2000, message = "Rejection reason must be between 10 and 2000 characters")
    private String rejectionReason;
    
    /**
     * Optional rejection category code for reporting.
     * 
     * Examples:
     * - "NOT_COVERED" - Service not covered by policy
     * - "INSUFFICIENT_DOCS" - Missing required documents
     * - "MEDICAL_ERROR" - Medical information error
     * - "DUPLICATE" - Duplicate claim
     * - "EXPIRED_PA" - Pre-authorization expired
     */
    @Size(max = 50, message = "Rejection code must not exceed 50 characters")
    private String rejectionCode;
}
