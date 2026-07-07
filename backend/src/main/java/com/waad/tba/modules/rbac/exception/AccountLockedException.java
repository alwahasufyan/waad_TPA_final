package com.waad.tba.modules.rbac.exception;

/**
 * Exception thrown when account is locked due to failed login attempts
 */
public class AccountLockedException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    private final String username;
    private final java.time.LocalDateTime lockedUntil;
    private final String messageAr;
    
    public AccountLockedException(String username, java.time.LocalDateTime lockedUntil) {
        super("Account locked due to multiple failed login attempts. Try again after " + lockedUntil);
        this.username = username;
        this.lockedUntil = lockedUntil;
        this.messageAr = "تم قفل الحساب بسبب محاولات تسجيل دخول فاشلة متعددة. حاول مرة أخرى بعد " + lockedUntil;
    }
    
    public String getUsername() {
        return username;
    }
    
    public java.time.LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
    
    public String getMessageAr() {
        return messageAr;
    }
}
