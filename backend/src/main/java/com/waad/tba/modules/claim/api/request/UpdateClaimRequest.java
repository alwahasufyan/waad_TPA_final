package com.waad.tba.modules.claim.api.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Update Claim Request
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * FINANCIAL SAFETY GUARANTEE:
 * This contract does NOT accept any monetary values from the frontend.
 * 
 * ALLOWED UPDATES:
 * - doctorName (correction)
 * - diagnosisCode/diagnosisDescription (correction)
 * - preAuthorizationId (linking)
 * - notes (additional information)
 * 
 * FORBIDDEN UPDATES:
 * - Status changes (use workflow endpoints: /submit, /approve, /reject)
 * - Monetary values (approvedAmount, requestedAmount, etc.)
 * - Provider information (derived from visit)
 * - Visit date (derived from visit)
 * - Claim lines (prices are contract-driven)
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClaimRequest {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ALLOWED UPDATES (Non-architectural corrections)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Doctor name correction
     */
    @Size(max = 255, message = "Doctor name must not exceed 255 characters")
    private String doctorName;
    
    /**
     * Diagnosis Code (ICD-10 or local code) - correction only
     */
    @Size(max = 20, message = "Diagnosis code must not exceed 20 characters")
    private String diagnosisCode;
    
    /**
     * Diagnosis Description - correction only
     */
    @Size(max = 500, message = "Diagnosis description must not exceed 500 characters")
    private String diagnosisDescription;
    
    /**
     * Link to PreAuthorization (if not set during creation)
     */
    private Long preAuthorizationId;
    
    /**
     * Additional notes or corrections
     */
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ⛔ FORBIDDEN FIELDS - ARCHITECTURAL VIOLATIONS
    // ═══════════════════════════════════════════════════════════════════════════
    // The following fields are EXPLICITLY FORBIDDEN:
    // 
    // ❌ status - Use workflow endpoints (/submit, /approve, /reject)
    // ❌ approvedAmount - Use /approve endpoint with backend calculation
    // ❌ requestedAmount - Calculated from contract pricing
    // ❌ providerName - Derived from Visit.Provider
    // ❌ visitDate - Derived from Visit.visitDate
    // ❌ lines - Prices from ProviderContract, cannot be changed
    // ❌ active - Use /delete endpoint for soft delete
    // ═══════════════════════════════════════════════════════════════════════════
}
