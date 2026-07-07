package com.waad.tba.modules.member.exception;

/**
 * Exception thrown when eligibility input is invalid
 * (neither valid card number nor valid barcode format)
 */
public class InvalidEligibilityInputException extends RuntimeException {
    
    private static final String ERROR_CODE = "INVALID_ELIGIBILITY_INPUT";
    private static final String DEFAULT_MESSAGE = "Invalid card number or barcode format";
    
    public InvalidEligibilityInputException() {
        super(DEFAULT_MESSAGE);
    }
    
    public InvalidEligibilityInputException(String message) {
        super(message != null ? message : DEFAULT_MESSAGE);
    }
    
    public String getErrorCode() {
        return ERROR_CODE;
    }
}
