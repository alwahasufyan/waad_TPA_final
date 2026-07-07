package com.waad.tba.common.exception;

import com.waad.tba.common.error.ErrorCode;

/**
 * Exception thrown when a business rule is violated.
 * 
 * BUSINESS RULE EXAMPLES:
 * - Member cannot create claim without active policy
 * - Claim cannot transition from DRAFT directly to SETTLED
 * - Coverage limit exceeded
 * 
 * @see ErrorCode for standard error codes
 */
public class BusinessRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    private final ErrorCode errorCode;

    public BusinessRuleException(String message) {
        super(message);
        this.errorCode = ErrorCode.BUSINESS_RULE_VIOLATION;
    }

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.BUSINESS_RULE_VIOLATION;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
