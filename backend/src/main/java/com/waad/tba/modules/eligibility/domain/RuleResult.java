package com.waad.tba.modules.eligibility.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Rule Evaluation Result
 * Phase E1 - Eligibility Engine
 * 
 * Encapsulates the result of a single rule evaluation.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Data
@Builder
public class RuleResult {

    /**
     * Whether the rule passed
     */
    private final boolean passed;

    /**
     * The reason code (machine-readable)
     */
    private final EligibilityReason reason;

    /**
     * Custom message (optional, for dynamic messages)
     */
    private final String customMessage;

    /**
     * Additional details (optional)
     */
    private final String details;

    /**
     * Create a passed result
     */
    public static RuleResult pass() {
        return RuleResult.builder()
                .passed(true)
                .build();
    }

    /**
     * Create a passed result with a note
     */
    public static RuleResult pass(String details) {
        return RuleResult.builder()
                .passed(true)
                .details(details)
                .build();
    }

    /**
     * Create a failed result with a reason
     */
    public static RuleResult fail(EligibilityReason reason) {
        return RuleResult.builder()
                .passed(false)
                .reason(reason)
                .build();
    }

    /**
     * Create a failed result with a reason and details
     */
    public static RuleResult fail(EligibilityReason reason, String details) {
        return RuleResult.builder()
                .passed(false)
                .reason(reason)
                .details(details)
                .build();
    }

    /**
     * Create a failed result with a custom message
     */
    public static RuleResult fail(EligibilityReason reason, String customMessage, String details) {
        return RuleResult.builder()
                .passed(false)
                .reason(reason)
                .customMessage(customMessage)
                .details(details)
                .build();
    }

    /**
     * Get the display message (custom or from reason)
     */
    public String getMessage() {
        if (customMessage != null && !customMessage.isBlank()) {
            return customMessage;
        }
        return reason != null ? reason.getMessageAr() : null;
    }

    /**
     * Get the reason code
     */
    public String getReasonCode() {
        return reason != null ? reason.getCode() : null;
    }
}
