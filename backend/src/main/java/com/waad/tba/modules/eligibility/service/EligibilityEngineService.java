package com.waad.tba.modules.eligibility.service;

import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest;

/**
 * Eligibility Engine Service Interface
 * Phase E1 - Eligibility Engine
 * 
 * Core service for performing eligibility checks.
 * Implementations should be stateless and thread-safe.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
public interface EligibilityEngineService {

    /**
     * Check eligibility for a member.
     * 
     * This method:
     * 1. Builds the eligibility context
     * 2. Evaluates all applicable rules
     * 3. Returns the final decision
     * 4. Logs the check for audit
     * 
     * @param request The eligibility check request
     * @return EligibilityResult with decision and details
     */
    EligibilityResult checkEligibility(EligibilityCheckRequest request);

    /**
     * Check eligibility with a pre-built context.
     * Used for internal calls from other modules.
     * 
     * @param context The pre-built eligibility context
     * @return EligibilityResult with decision and details
     */
    EligibilityResult checkEligibility(EligibilityContext context);

    /**
     * Get list of active rules for debugging/admin purposes.
     * 
     * @return List of rule codes in evaluation order
     */
    java.util.List<String> getActiveRules();
}
