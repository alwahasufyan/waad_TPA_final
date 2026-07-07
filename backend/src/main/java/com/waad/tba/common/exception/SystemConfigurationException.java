package com.waad.tba.common.exception;

/**
 * Exception thrown when system configuration is invalid or missing.
 * 
 * This is a critical exception that indicates the system cannot operate
 * due to missing or invalid configuration (e.g., no default company).
 * 
 * Used for startup validation and configuration checks.
 */
public class SystemConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SystemConfigurationException(String message) {
        super(message);
    }

    public SystemConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
