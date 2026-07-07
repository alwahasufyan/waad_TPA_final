package com.waad.tba.modules.rbac.exception;

/**
 * Exception thrown when email verification is required but not completed
 */
public class EmailNotVerifiedException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    private final String username;
    private final String email;
    private final String messageAr;
    
    public EmailNotVerifiedException(String email) {
        super("Email verification required. Please check your email for verification link.");
        this.username = null;
        this.email = email;
        this.messageAr = "التحقق من البريد الإلكتروني مطلوب. يرجى التحقق من بريدك الإلكتروني للحصول على رابط التحقق.";
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public String getMessageAr() {
        return messageAr;
    }
}
