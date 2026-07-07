package com.waad.tba.modules.preauthorization.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Reject Pre-Authorization Request
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * BUSINESS RULES:
 * - Rejection reason is MANDATORY (must explain why pre-authorization was rejected)
 * - Pre-authorization will be moved to REJECTED status (terminal state)
 * - No claims can be processed for rejected pre-authorizations
 * 
 * WORKFLOW:
 * 1. Reviewer calls POST /api/v1/pre-authorizations/{id}/reject with this request
 * 2. Backend validates mandatory rejection reason
 * 3. Backend transitions pre-authorization to REJECTED status
 * 4. Backend records rejection in audit log
 * 5. Backend returns PreAuthorizationResponse with REJECTED status
 * 6. Future claims for this service will be blocked
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectPreAuthorizationRequest {
    
    /**
     * Mandatory rejection reason.
     * Must explain why the pre-authorization was rejected.
     * 
     * Examples:
     * - "الخدمة غير مشمولة بالبوليصة"
     * - "مستندات ناقصة"
     * - "التشخيص غير متطابق مع الخدمة المطلوبة"
     * - "Service not covered by policy"
     * - "Insufficient documentation"
     * - "Diagnosis does not match requested service"
     */
    @NotBlank(message = "Rejection reason is required")
    @Size(min = 10, max = 500, message = "Rejection reason must be between 10 and 500 characters")
    private String rejectionReason;
}
