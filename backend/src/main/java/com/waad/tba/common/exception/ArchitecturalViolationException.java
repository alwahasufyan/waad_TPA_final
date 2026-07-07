package com.waad.tba.common.exception;

/**
 * Exception thrown when an architectural rule is violated.
 * 
 * ARCHITECTURAL RULES (NON-NEGOTIABLE):
 * - MedicalService must belong to a MedicalCategory
 * - Claim must be linked to a Visit
 * - Contract Pricing must reference a MedicalService
 * - BenefitPolicyRule must target either Service OR Category
 * - Price must come from ProviderContract only
 * - Coverage must come from BenefitPolicy only
 * 
 * These are system invariants that must NEVER be violated.
 * 
 * @version 1.0
 * @since 2026-01-22
 */
public class ArchitecturalViolationException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private final String violationType;
    private final String entityName;

    public ArchitecturalViolationException(String message) {
        super(message);
        this.violationType = "ARCHITECTURAL_VIOLATION";
        this.entityName = null;
    }

    public ArchitecturalViolationException(String entityName, String message) {
        super(String.format("[%s] %s", entityName, message));
        this.violationType = "ARCHITECTURAL_VIOLATION";
        this.entityName = entityName;
    }

    public ArchitecturalViolationException(String violationType, String entityName, String message) {
        super(String.format("[%s][%s] %s", violationType, entityName, message));
        this.violationType = violationType;
        this.entityName = entityName;
    }

    public String getViolationType() {
        return violationType;
    }

    public String getEntityName() {
        return entityName;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS FOR COMMON VIOLATIONS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Service without Category violation
     */
    public static ArchitecturalViolationException serviceWithoutCategory(String serviceCode) {
        return new ArchitecturalViolationException(
            "CATEGORY_REQUIRED",
            "MedicalService",
            String.format("MedicalService '%s' must belong to a MedicalCategory. " +
                "Category is required for coverage resolution.", serviceCode)
        );
    }

    /**
     * Claim without Visit violation
     */
    public static ArchitecturalViolationException claimWithoutVisit(Long claimId) {
        return new ArchitecturalViolationException(
            "VISIT_REQUIRED",
            "Claim",
            String.format("Claim %d must be linked to a Visit. " +
                "Visit-centric architecture is mandatory.", claimId)
        );
    }

    /**
     * Claim without MedicalService violation
     */
    public static ArchitecturalViolationException claimWithoutService(Long claimId) {
        return new ArchitecturalViolationException(
            "SERVICE_REQUIRED",
            "Claim",
            String.format("Claim %d must have at least one MedicalService. " +
                "Free-text services are not allowed.", claimId)
        );
    }

    /**
     * Contract Pricing without Service violation
     */
    public static ArchitecturalViolationException pricingWithoutService(Long contractId) {
        return new ArchitecturalViolationException(
            "SERVICE_REQUIRED",
            "ProviderContractPricingItem",
            String.format("Contract %d pricing item must reference a MedicalService. " +
                "Free-text pricing is not allowed.", contractId)
        );
    }

    /**
     * Rule without Service or Category violation
     */
    public static ArchitecturalViolationException ruleWithoutTarget(Long ruleId) {
        return new ArchitecturalViolationException(
            "TARGET_REQUIRED",
            "BenefitPolicyRule",
            String.format("BenefitPolicyRule %d must target either a Service OR a Category. " +
                "Rules without targets cannot be used for coverage calculation.", ruleId)
        );
    }

    /**
     * PreAuthorization without Visit violation
     */
    public static ArchitecturalViolationException preAuthWithoutVisit(String referenceNumber) {
        return new ArchitecturalViolationException(
            "VISIT_REQUIRED",
            "PreAuthorization",
            String.format("PreAuthorization '%s' must be linked to a Visit. " +
                "Standalone pre-authorizations are not allowed.", referenceNumber)
        );
    }

    /**
     * Price source violation
     */
    public static ArchitecturalViolationException invalidPriceSource(String context) {
        return new ArchitecturalViolationException(
            "PRICE_SOURCE_VIOLATION",
            "Pricing",
            String.format("Price must come from ProviderContract only. %s", context)
        );
    }

    /**
     * Coverage source violation
     */
    public static ArchitecturalViolationException invalidCoverageSource(String context) {
        return new ArchitecturalViolationException(
            "COVERAGE_SOURCE_VIOLATION",
            "Coverage",
            String.format("Coverage must come from BenefitPolicy only. %s", context)
        );
    }

    /**
     * Duplicate active rule violation
     */
    public static ArchitecturalViolationException duplicateActiveRule(Long policyId, Long serviceId) {
        return new ArchitecturalViolationException(
            "DUPLICATE_RULE",
            "BenefitPolicyRule",
            String.format("Multiple active rules found for Policy %d, Service %d. " +
                "Only one active rule per service is allowed.", policyId, serviceId)
        );
    }
}
