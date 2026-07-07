package com.waad.tba.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Validator implementation for @PasswordPolicy annotation
 * 
 * Enforces production-ready password security requirements.
 */
@Slf4j
public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {

    private int min;
    private int max;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireDigit;
    private boolean requireSpecialChar;
    private String specialChars;
    
    private Pattern uppercasePattern;
    private Pattern lowercasePattern;
    private Pattern digitPattern;
    private Pattern specialCharPattern;

    @Override
    public void initialize(PasswordPolicy constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
        this.requireUppercase = constraintAnnotation.requireUppercase();
        this.requireLowercase = constraintAnnotation.requireLowercase();
        this.requireDigit = constraintAnnotation.requireDigit();
        this.requireSpecialChar = constraintAnnotation.requireSpecialChar();
        this.specialChars = constraintAnnotation.specialChars();
        
        // Compile patterns for performance
        if (requireUppercase) {
            uppercasePattern = Pattern.compile("[A-Z]");
        }
        if (requireLowercase) {
            lowercasePattern = Pattern.compile("[a-z]");
        }
        if (requireDigit) {
            digitPattern = Pattern.compile("\\d");
        }
        if (requireSpecialChar) {
            // Escape special chars for regex
            String escaped = Pattern.quote(specialChars);
            specialCharPattern = Pattern.compile("[" + escaped + "]");
        }
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true; // Use @NotBlank for null check
        }
        
        // Disable default message
        context.disableDefaultConstraintViolation();
        
        // Check length
        if (password.length() < min) {
            context.buildConstraintViolationWithTemplate(
                "Password must be at least " + min + " characters long"
            ).addConstraintViolation();
            return false;
        }
        
        if (password.length() > max) {
            context.buildConstraintViolationWithTemplate(
                "Password must not exceed " + max + " characters"
            ).addConstraintViolation();
            return false;
        }
        
        // Check uppercase requirement
        if (requireUppercase && !uppercasePattern.matcher(password).find()) {
            context.buildConstraintViolationWithTemplate(
                "Password must contain at least one uppercase letter (A-Z)"
            ).addConstraintViolation();
            return false;
        }
        
        // Check lowercase requirement
        if (requireLowercase && !lowercasePattern.matcher(password).find()) {
            context.buildConstraintViolationWithTemplate(
                "Password must contain at least one lowercase letter (a-z)"
            ).addConstraintViolation();
            return false;
        }
        
        // Check digit requirement
        if (requireDigit && !digitPattern.matcher(password).find()) {
            context.buildConstraintViolationWithTemplate(
                "Password must contain at least one digit (0-9)"
            ).addConstraintViolation();
            return false;
        }
        
        // Check special character requirement
        if (requireSpecialChar && !specialCharPattern.matcher(password).find()) {
            context.buildConstraintViolationWithTemplate(
                "Password must contain at least one special character (" + specialChars + ")"
            ).addConstraintViolation();
            return false;
        }
        
        return true;
    }
}
