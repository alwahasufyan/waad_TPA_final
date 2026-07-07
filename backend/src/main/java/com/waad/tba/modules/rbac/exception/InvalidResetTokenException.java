package com.waad.tba.modules.rbac.exception;

/**
 * Exception thrown when reset token is invalid or expired
 */
public class InvalidResetTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    private final String token;
    private final String messageAr;
    
    public InvalidResetTokenException(String message, String token) {
        super(message);
        this.token = token;
        this.messageAr = "رمز إعادة التعيين غير صالح أو منتهي الصلاحية";
    }
    
    public String getToken() {
        return token;
    }
    
    public String getMessageAr() {
        return messageAr;
    }
}
