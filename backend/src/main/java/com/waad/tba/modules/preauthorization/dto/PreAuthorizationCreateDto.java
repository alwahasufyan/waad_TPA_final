package com.waad.tba.modules.preauthorization.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for creating a new PreAuthorization (CANONICAL REBUILD 2026-01-16)
 * 
 * ARCHITECTURAL LAW:
 * - visitId is MANDATORY - no standalone pre-auth creation
 * - medicalServiceId is MANDATORY - no free-text services
 * - providerId is AUTO-FILLED from JWT security context
 * - Price is AUTO-RESOLVED from Provider Contract (not in this DTO)
 * 
 * Data Flow:
 * Visit → ProviderContractPricingItem → ContractPrice (auto)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationCreateDto {

    // ==================== MANDATORY FIELDS ====================

    /**
     * ARCHITECTURAL LAW: Pre-authorizations can ONLY be created from an existing
     * Visit.
     */
    @NotNull(message = "Visit ID is required - Pre-authorization must originate from a Visit")
    @Positive(message = "Visit ID must be positive")
    private Long visitId;

    /**
     * Pricing Item ID (from Provider Contract) — the legacy single-service
     * shape. ARCHITECTURAL LAW: Service MUST be selected from Provider
     * Contract - NO free-text allowed.
     *
     * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): no longer {@code @NotNull} —
     * either this field OR a non-empty {@link #lines} must be present (see
     * {@link #isServiceSelectionValid()}). Both shapes remain accepted
     * indefinitely for backward compatibility: a request with only this
     * field is wrapped into a single-element {@code lines} list internally
     * by {@code PreAuthorizationService.createPreAuthorization}, producing
     * an identical result to before this feature existed.
     */
    @Positive(message = "Pricing Item ID must be positive")
    private Long pricingItemId;

    /**
     * @deprecated Use pricingItemId instead. Kept for backward compatibility.
     */
    @Deprecated
    @Positive(message = "Medical Service ID must be positive")
    private Long medicalServiceId;

    /**
     * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): one or more service lines for a
     * multi-service pre-authorization request. When present and non-empty,
     * this is authoritative and the legacy flat pricingItemId/
     * serviceCategoryId fields above are ignored by the service layer. When
     * absent, the service layer wraps the legacy flat fields into a single
     * line automatically.
     */
    @Valid
    private List<PreAuthorizationLineDto> lines;

    /**
     * Cross-field validation: exactly one of the two request shapes must be
     * usable — either the legacy pricingItemId, or a non-empty lines list.
     * A request with neither (or with a lines entry missing its own
     * pricingItemId) fails validation before ever reaching the service
     * layer, matching this DTO's existing all-fail-fast style.
     */
    @AssertTrue(message = "Either pricingItemId (legacy) or a non-empty lines list is required")
    private boolean isServiceSelectionValid() {
        if (pricingItemId != null) {
            return true;
        }
        return lines != null && !lines.isEmpty();
    }

    // ==================== AUTO-DERIVED FIELDS (from system) ====================

    /**
     * Member ID - AUTO-DERIVED from Visit
     * Can be provided for validation, but will be overridden from Visit
     */
    private Long memberId;

    /**
     * Provider ID - AUTO-FILLED from JWT security context
     * ARCHITECTURAL LAW: Provider CANNOT override their identity
     */
    private Long providerId;

    // ==================== DIAGNOSIS (SELECTED, NOT TYPED) ====================

    /**
     * Diagnosis ICD-10 code (selected from dropdown)
     * Will be FK to Diagnosis table when available
     */
    @Size(max = 20, message = "Diagnosis code must not exceed 20 characters")
    private String diagnosisCode;

    /**
     * Diagnosis description (auto-populated from code)
     */
    @Size(max = 500, message = "Diagnosis description must not exceed 500 characters")
    private String diagnosisDescription;

    // ==================== OPTIONAL FIELDS ====================

    @FutureOrPresent(message = "Request date must be today or in the future")
    private LocalDate requestDate;

    @Size(max = 3, message = "Currency must be 3 characters")
    @Builder.Default
    private String currency = "LYD";

    private String priority; // EMERGENCY, URGENT, NORMAL, LOW

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    // ==================== CANONICAL: Service Category ====================

    /**
     * CANONICAL: Medical Category ID
     * ARCHITECTURAL LAW: Category must be selected BEFORE service
     * This enables correct coverage resolution for services in multiple categories
     */
    @Positive(message = "Service Category ID must be positive")
    private Long serviceCategoryId;

    /**
     * Category name for display purposes (denormalized)
     */
    @Size(max = 255, message = "Service Category Name must not exceed 255 characters")
    private String serviceCategoryName;

    /**
     * Number of days until expiry (default 30)
     */
    @Positive(message = "Expiry days must be positive")
    @Builder.Default
    private Integer expiryDays = 30;
}
