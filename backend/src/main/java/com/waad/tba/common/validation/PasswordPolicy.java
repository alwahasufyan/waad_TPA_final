package com.waad.tba.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Password Policy Validator
 * 
 * Enforces production-ready password requirements:
 * - Minimum 8 characters
 * - Maximum 100 characters
 * - At least one uppercase letter
 * - At least one lowercase letter
 * - At least one digit
 * - At least one special character (@$!%*?&)
 * - Cannot be same as username (validated in service layer)
 * 
 * Usage:
 * <pre>
 * {@code
 * @PasswordPolicy
 * private String password;
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Documented
public @interface PasswordPolicy {
    
    String message() default "Password must be 8-100 characters and contain uppercase, lowercase, number, and special character (@$!%*?&)";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Minimum password length (default: 8)
     */
    int min() default 8;
    
    /**
     * Maximum password length (default: 100)
     */
    int max() default 100;
    
    /**
     * Whether to require at least one uppercase letter (default: true)
     */
    boolean requireUppercase() default true;
    
    /**
     * Whether to require at least one lowercase letter (default: true)
     */
    boolean requireLowercase() default true;
    
    /**
     * Whether to require at least one digit (default: true)
     */
    boolean requireDigit() default true;
    
    /**
     * Whether to require at least one special character (default: true)
     */
    boolean requireSpecialChar() default true;
    
    /**
     * Allowed special characters (default: @$!%*?&)
     */
    String specialChars() default "@$!%*?&";
}
