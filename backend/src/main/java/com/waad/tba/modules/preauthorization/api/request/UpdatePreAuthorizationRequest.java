package com.waad.tba.modules.preauthorization.api.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Update Pre-Authorization Request
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * DECISION SAFETY GUARANTEE:
 * This contract does NOT accept any decision-altering data from the frontend.
 * 
 * ALLOWED UPDATES:
 * - priority (correction)
 * - diagnosisCode/diagnosisDescription (correction)
 * - notes (additional information)
 * - expiryDays (extension)
 * 
 * FORBIDDEN UPDATES:
 * - Status changes (use workflow endpoints: /approve, /reject, /cancel)
 * - Monetary values (approvedAmount, copayPercentage, etc.)
 * - Service information (derived from contract)
 * - Member/provider information (derived from visit)
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreAuthorizationRequest {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ALLOWED UPDATES (Non-decision metadata only)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Priority level (EMERGENCY, URGENT, NORMAL, LOW)
     */
    @Size(max = 20, message = "Priority must not exceed 20 characters")
    private String priority;
    
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
     * Additional notes or corrections
     */
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
    
    /**
     * Days until expiry (extension allowed)
     */
    @Positive(message = "Expiry days must be positive")
    private Integer expiryDays;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ⛔ FORBIDDEN FIELDS - DECISION VIOLATIONS
    // ═══════════════════════════════════════════════════════════════════════════
    // The following fields are EXPLICITLY FORBIDDEN:
    // 
    // ❌ status - Use workflow endpoints (/approve, /reject, /cancel)
    // ❌ approvedAmount - Use /approve endpoint with backend calculation
    // ❌ copayPercentage - Calculated from benefit policy
    // ❌ contractPrice - Auto-resolved from Provider Contract
    // ❌ medicalServiceId - Cannot be changed after creation
    // ❌ visitId - Cannot be changed after creation
    // ❌ memberId - Derived from Visit
    // ❌ providerId - Derived from Visit
    // ═══════════════════════════════════════════════════════════════════════════
}
