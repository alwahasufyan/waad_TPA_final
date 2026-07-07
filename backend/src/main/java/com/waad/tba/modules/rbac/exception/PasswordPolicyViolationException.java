package com.waad.tba.modules.rbac.exception;

import java.util.List;

/**
 * Exception thrown when password does not meet policy requirements
 */
public class PasswordPolicyViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    private final transient List<String> violations;
    private final String messageAr;
    
    public PasswordPolicyViolationException(String message, List<String> violations) {
        super(message);
        this.violations = violations;
        this.messageAr = "كلمة المرور لا تستوفي متطلبات السياسة الأمنية";
    }
    
    public List<String> getViolations() {
        return violations;
    }
    
    public String getMessageAr() {
        return messageAr;
    }
}
