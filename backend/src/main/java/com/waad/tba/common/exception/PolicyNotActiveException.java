package com.waad.tba.common.exception;

import java.time.LocalDate;

import com.waad.tba.common.error.ErrorCode;

/**
 * Exception thrown when an operation requires an active policy but none is found.
 * 
 * BUSINESS SCENARIOS:
 * - Member tries to create claim but policy is expired
 * - Member tries to create visit but policy start date is in the future
 * - Policy status is SUSPENDED or CANCELLED
 * 
 * SMOKE TEST:
 * Given: Member "Ali" has Policy P001 valid from 2024-01-01 to 2024-12-31
 * When: Ali tries to create a claim on 2025-01-15
 * Then: PolicyNotActiveException with message "Policy P001 is not active on 2025-01-15"
 */
public class PolicyNotActiveException extends BusinessRuleException {
    private static final long serialVersionUID = 1L;
    
    private final Long policyId;
    private final String policyNumber;
    private final LocalDate requestedDate;

    public PolicyNotActiveException(String message) {
        super(ErrorCode.POLICY_NOT_ACTIVE, message);
        this.policyId = null;
        this.policyNumber = null;
        this.requestedDate = null;
    }

    public PolicyNotActiveException(Long policyId, String policyNumber, LocalDate requestedDate) {
        super(ErrorCode.POLICY_NOT_ACTIVE, buildMessage(policyNumber, requestedDate));
        this.policyId = policyId;
        this.policyNumber = policyNumber;
        this.requestedDate = requestedDate;
    }

    private static String buildMessage(String policyNumber, LocalDate requestedDate) {
        return String.format(
            "Policy %s is not active on %s. Please verify policy start/end dates and status.",
            policyNumber != null ? policyNumber : "Unknown",
            requestedDate != null ? requestedDate.toString() : "the requested date"
        );
    }

    public Long getPolicyId() {
        return policyId;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }
}
