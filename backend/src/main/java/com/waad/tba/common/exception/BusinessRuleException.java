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

    /**
     * Optional Arabic-language message, set by the throwing service when it knows the
     * user-facing wording. GlobalExceptionHandler only ever *reads* this (falling back to a
     * generic message when null) — it never authors business-specific Arabic text itself.
     */
    private String messageAr;

    public BusinessRuleException(String message) {
        super(message);
        this.errorCode = ErrorCode.BUSINESS_RULE_VIOLATION;
    }

    public BusinessRuleException(String message, String messageAr) {
        super(message);
        this.errorCode = ErrorCode.BUSINESS_RULE_VIOLATION;
        this.messageAr = messageAr;
    }

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessRuleException(ErrorCode errorCode, String message, String messageAr) {
        super(message);
        this.errorCode = errorCode;
        this.messageAr = messageAr;
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.BUSINESS_RULE_VIOLATION;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getMessageAr() {
        return messageAr;
    }

    /** Lets subclass constructors set the Arabic message after calling super(...). */
    protected void setMessageAr(String messageAr) {
        this.messageAr = messageAr;
    }
}
