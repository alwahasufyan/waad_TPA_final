package com.waad.tba.modules.eligibility.domain;

/**
 * Eligibility Rule Interface
 * Phase E1 - Eligibility Engine
 * 
 * Each rule implements this interface to evaluate a specific aspect
 * of member eligibility. Rules are pluggable and can be added/removed
 * without modifying the engine core.
 * 
 * Rules are evaluated sequentially. A HARD failure stops evaluation.
 * SOFT failures (warnings) are collected but don't stop evaluation.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
public interface EligibilityRule {

    /**
     * Get the unique code for this rule.
     * Used for logging and debugging.
     * 
     * @return Rule code (e.g., "MEMBER_ACTIVE", "POLICY_COVERAGE")
     */
    String getRuleCode();

    /**
     * Get the display name of this rule in Arabic.
     * 
     * @return Arabic name for UI display
     */
    String getNameAr();

    /**
     * Get the priority/order of this rule.
     * Lower numbers are evaluated first.
     * 
     * @return Priority (default 100)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Evaluate this rule against the given context.
     * 
     * @param context The eligibility context containing all input data
     * @return RuleResult with pass/fail status and reason
     */
    RuleResult evaluate(EligibilityContext context);

    /**
     * Check if this rule is applicable to the given context.
     * Some rules may be optional or conditional.
     * 
     * @param context The eligibility context
     * @return true if this rule should be evaluated
     */
    default boolean isApplicable(EligibilityContext context) {
        return true;
    }

    /**
     * Check if failure of this rule is a hard failure (stops evaluation)
     * or a soft failure (warning, continue evaluation).
     * 
     * @return true if this is a hard rule (default), false for soft/warning rule
     */
    default boolean isHardRule() {
        return true;
    }
}
