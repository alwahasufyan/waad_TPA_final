package com.waad.tba.modules.member.exception;

/**
 * Exception thrown when member is not found during eligibility check
 */
public class MemberNotFoundException extends RuntimeException {
    
    private static final String ERROR_CODE = "MEMBER_NOT_FOUND";
    private static final String DEFAULT_MESSAGE = "Member not found";
    
    public MemberNotFoundException() {
        super(DEFAULT_MESSAGE);
    }
    
    public MemberNotFoundException(String message) {
        super(message != null ? message : DEFAULT_MESSAGE);
    }
    
    public String getErrorCode() {
        return ERROR_CODE;
    }
}
