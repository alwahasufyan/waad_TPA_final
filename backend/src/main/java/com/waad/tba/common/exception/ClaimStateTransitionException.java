package com.waad.tba.common.exception;

import com.waad.tba.common.error.ErrorCode;

/**
 * Exception thrown when an illegal claim state transition is attempted.
 * 
 * VALID TRANSITIONS:
 * DRAFT          → SUBMITTED (by EMPLOYER, INSURANCE)
 * SUBMITTED      → UNDER_REVIEW (by INSURANCE, REVIEWER)
 * UNDER_REVIEW   → APPROVED (by INSURANCE, REVIEWER)
 * UNDER_REVIEW   → REJECTED (by INSURANCE, REVIEWER)
 * UNDER_REVIEW   → RETURNED_FOR_INFO (by REVIEWER)
 * RETURNED_FOR_INFO → SUBMITTED (by EMPLOYER, INSURANCE)
 * APPROVED       → SETTLED (by INSURANCE)
 * 
 * INVALID TRANSITIONS (examples):
 * DRAFT → APPROVED (must go through SUBMITTED and UNDER_REVIEW)
 * SETTLED → DRAFT (terminal state)
 * REJECTED → APPROVED (terminal state)
 * 
 * SMOKE TEST:
 * Given: Claim C001 is in DRAFT status
 * When: User tries to change status to APPROVED
 * Then: ClaimStateTransitionException with message 
 *       "Invalid state transition: DRAFT → APPROVED. Must submit claim first."
 */
public class ClaimStateTransitionException extends BusinessRuleException {
    private static final long serialVersionUID = 1L;

    private final String fromStatus;
    private final String toStatus;
    private final String requiredRole;

    public ClaimStateTransitionException(String message) {
        super(ErrorCode.INVALID_CLAIM_TRANSITION, message);
        this.fromStatus = null;
        this.toStatus = null;
        this.requiredRole = null;
    }

    public ClaimStateTransitionException(String fromStatus, String toStatus) {
        super(ErrorCode.INVALID_CLAIM_TRANSITION, buildMessage(fromStatus, toStatus, null));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.requiredRole = null;
    }

    public ClaimStateTransitionException(String fromStatus, String toStatus, String requiredRole) {
        super(ErrorCode.INVALID_CLAIM_TRANSITION, buildMessage(fromStatus, toStatus, requiredRole));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.requiredRole = requiredRole;
    }

    private static String buildMessage(String fromStatus, String toStatus, String requiredRole) {
        StringBuilder sb = new StringBuilder();
        sb.append("Invalid state transition: ").append(fromStatus).append(" → ").append(toStatus).append(".");
        
        if (requiredRole != null) {
            sb.append(" Required role: ").append(requiredRole).append(".");
        }
        
        // Add helpful hints based on transition
        if ("DRAFT".equals(fromStatus) && !"SUBMITTED".equals(toStatus)) {
            sb.append(" Claims must be submitted before review.");
        } else if ("SUBMITTED".equals(fromStatus) && !"UNDER_REVIEW".equals(toStatus)) {
            sb.append(" Submitted claims must be taken under review.");
        } else if (("APPROVED".equals(fromStatus) || "REJECTED".equals(fromStatus) || "SETTLED".equals(fromStatus))) {
            sb.append(" Terminal states cannot be changed.");
        }
        
        return sb.toString();
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getRequiredRole() {
        return requiredRole;
    }
}
