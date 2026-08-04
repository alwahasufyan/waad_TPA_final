package com.waad.tba.modules.preauthorization.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Create Pre-Authorization Request
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * DECISION SAFETY GUARANTEE:
 * This contract does NOT accept any decision-altering data from the frontend.
 * All approval decisions, amounts, and coverage limits are calculated by the backend.
 * 
 * ARCHITECTURAL LAWS:
 * 1. visitId is MANDATORY - pre-authorizations can only be created from existing visits
 * 2. medicalServiceId is MANDATORY - no free-text services
 * 3. providerId is AUTO-FILLED from JWT security context
 * 4. All prices are AUTO-RESOLVED from Provider Contract
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePreAuthorizationRequest {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MANDATORY FIELDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * REQUIRED: Visit ID that this pre-authorization is linked to.
     * ARCHITECTURAL LAW: Pre-authorizations can ONLY be created from an existing Visit.
     */
    @NotNull(message = "Visit ID is required - Pre-authorization must originate from a Visit")
    @Positive(message = "Visit ID must be positive")
    private Long visitId;
    
    /**
     * Medical Service ID (from Provider Contract) — the legacy single-service
     * shape. ARCHITECTURAL LAW: Service MUST be selected from Provider
     * Contract - NO free-text allowed.
     *
     * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): no longer {@code @NotNull} —
     * either this field OR a non-empty {@link #lines} must be present (see
     * {@link #isServiceSelectionValid()}).
     */
    @Positive(message = "Medical Service ID must be positive")
    private Long medicalServiceId;

    /**
     * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): one or more service lines for a
     * multi-service pre-authorization request. When present and non-empty,
     * authoritative over the legacy medicalServiceId/serviceCategoryId
     * fields above.
     */
    @Valid
    private List<PreAuthorizationLineRequest> lines;

    /**
     * Cross-field validation mirroring PreAuthorizationCreateDto's own —
     * either the legacy medicalServiceId, or a non-empty lines list.
     */
    @AssertTrue(message = "Either medicalServiceId (legacy) or a non-empty lines list is required")
    private boolean isServiceSelectionValid() {
        if (medicalServiceId != null) {
            return true;
        }
        return lines != null && !lines.isEmpty();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // OPTIONAL REFERENCE FIELDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Member ID - AUTO-DERIVED from Visit (can be provided for validation)
     */
    private Long memberId;
    
    /**
     * Provider ID - AUTO-FILLED from JWT security context
     * ARCHITECTURAL LAW: Provider CANNOT override their identity
     */
    private Long providerId;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DIAGNOSIS (Selected from dropdown, not free-text)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Diagnosis ICD-10 code (selected from dropdown)
     */
    @Size(max = 20, message = "Diagnosis code must not exceed 20 characters")
    private String diagnosisCode;
    
    /**
     * Diagnosis description (auto-populated from code)
     */
    @Size(max = 500, message = "Diagnosis description must not exceed 500 characters")
    private String diagnosisDescription;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // OPTIONAL METADATA
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Request date (defaults to today if not provided)
     */
    @FutureOrPresent(message = "Request date must be today or in the future")
    private LocalDate requestDate;
    
    /**
     * Currency (defaults to LYD)
     */
    @Size(max = 3, message = "Currency must be 3 characters")
    @Builder.Default
    private String currency = "LYD";
    
    /**
     * Priority level (EMERGENCY, URGENT, NORMAL, LOW)
     */
    @Size(max = 20, message = "Priority must not exceed 20 characters")
    private String priority;
    
    /**
     * Additional notes
     */
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
    
    /**
     * Medical Category ID
     * ARCHITECTURAL LAW: Category must be selected BEFORE service
     */
    @Positive(message = "Service Category ID must be positive")
    private Long serviceCategoryId;
    
    /**
     * Category name for display (denormalized)
     */
    @Size(max = 255, message = "Service Category Name must not exceed 255 characters")
    private String serviceCategoryName;

    // ⛔ FORBIDDEN FIELDS - DECISION SAFETY
    // ═══════════════════════════════════════════════════════════════════════════
    // The following fields are EXPLICITLY FORBIDDEN in API v1 contracts:
    // 
    // ❌ approvedAmount - Calculated during approval workflow
    // ❌ copayPercentage - Calculated from benefit policy
    // ❌ copayAmount - Calculated from benefit policy
    // ❌ contractPrice - Auto-resolved from Provider Contract
    // ❌ insuranceCoveredAmount - Calculated (approved - copay)
    // ❌ coverageLimits - Enforced by backend business rules
    // ❌ status - Managed by workflow state machine
    // 
    // Backend calculates ALL financial and decision values to ensure integrity.
    // ═══════════════════════════════════════════════════════════════════════════
}
