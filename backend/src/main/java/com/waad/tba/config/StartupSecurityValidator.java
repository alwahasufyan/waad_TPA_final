package com.waad.tba.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 🔐 Startup Security Validator
 * 
 * Validates that all required security-sensitive environment variables are
 * properly configured.
 * Runs BEFORE Spring context initialization to fail fast on startup if critical
 * secrets are missing.
 * 
 * Required Environment Variables:
 * - DB_PASSWORD: Database password (required)
 * - JWT_SECRET: JWT signing key (required, min 32 bytes)
 * - EMAIL_USERNAME: SMTP username (required if EMAIL_ENABLED=true)
 * - EMAIL_PASSWORD: SMTP password (required if EMAIL_ENABLED=true)
 * 
 * @author TBA Security Team
 * @since 2026-01-28
 */
public class StartupSecurityValidator implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(StartupSecurityValidator.class);

    private static final int MIN_JWT_SECRET_BYTES = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        log.info("🔐 [SECURITY] Starting security configuration validation...");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ===========================================
        // 1. Database Password Validation
        // ===========================================
        String dbPassword = environment.getProperty("spring.datasource.password");
        if (isBlank(dbPassword)) {
            errors.add("DB_PASSWORD environment variable is required but not set");
        } else {
            log.info("✅ [SECURITY] Database password configured");
        }

        // ===========================================
        // 2. JWT Secret Validation
        // ===========================================
        String jwtSecret = environment.getProperty("jwt.secret");
        validateJwtSecret(jwtSecret, errors, warnings);

        // ===========================================
        // 3. Email Credentials Validation
        // ===========================================
        boolean emailEnabled = Boolean.parseBoolean(
                environment.getProperty("email.enabled", "false"));
        String emailUsername = environment.getProperty("spring.mail.username");
        String emailPassword = environment.getProperty("spring.mail.password");
        validateEmailConfiguration(emailEnabled, emailUsername, emailPassword, errors, warnings);

        // ===========================================
        // Report Results
        // ===========================================
        reportValidationResults(errors, warnings);
    }

    /**
     * Validates JWT secret exists and meets minimum length requirement.
     * JWT is reserved for future mobile clients - not used for web authentication.
     */
    private void validateJwtSecret(String jwtSecret, List<String> errors, List<String> warnings) {
        if (isBlank(jwtSecret)) {
            errors.add("JWT_SECRET environment variable is required but not set");
            return;
        }

        // Check minimum length (decode Base64 to get actual byte length)
        int secretBytes;
        try {
            byte[] decoded = Base64.getDecoder().decode(jwtSecret);
            secretBytes = decoded.length;
        } catch (IllegalArgumentException e) {
            // Not Base64 encoded - use raw string length
            secretBytes = jwtSecret.getBytes().length;
        }

        if (secretBytes < MIN_JWT_SECRET_BYTES) {
            errors.add(String.format(
                    "JWT_SECRET must be at least %d bytes (currently %d bytes). " +
                            "Generate with: openssl rand -base64 48",
                    MIN_JWT_SECRET_BYTES, secretBytes));
        } else {
            log.info("✅ [SECURITY] JWT secret configured ({} bytes) - reserved for mobile clients", secretBytes);
        }
    }

    /**
     * Validates email credentials if email feature is enabled.
     */
    private void validateEmailConfiguration(boolean emailEnabled, String emailUsername,
            String emailPassword, List<String> errors, List<String> warnings) {
        if (!emailEnabled) {
            log.info("ℹ️ [SECURITY] Email is disabled (EMAIL_ENABLED=false) - skipping email credential validation");
            warnings.add(
                    "Email notifications are DISABLED. Set EMAIL_ENABLED=true and provide credentials for production.");
            return;
        }

        boolean hasUsername = !isBlank(emailUsername);
        boolean hasPassword = !isBlank(emailPassword);

        if (!hasUsername || !hasPassword) {
            StringBuilder errorMsg = new StringBuilder("Email is enabled but credentials are missing: ");
            List<String> missing = new ArrayList<>();
            if (!hasUsername)
                missing.add("EMAIL_USERNAME");
            if (!hasPassword)
                missing.add("EMAIL_PASSWORD");
            errorMsg.append(String.join(", ", missing));
            errors.add(errorMsg.toString());
        } else {
            // Don't log the actual username for security
            log.info("✅ [SECURITY] Email credentials configured");
        }
    }

    /**
     * Reports validation results and fails startup if there are errors.
     */
    private void reportValidationResults(List<String> errors, List<String> warnings) {
        // Log warnings
        for (String warning : warnings) {
            log.warn("⚠️ [SECURITY] {}", warning);
        }

        // If errors exist, fail startup
        if (!errors.isEmpty()) {
            System.err.println();
            System.err.println("═══════════════════════════════════════════════════════════════");
            System.err.println("❌ [SECURITY] Security configuration validation FAILED!");
            System.err.println("═══════════════════════════════════════════════════════════════");
            for (int i = 0; i < errors.size(); i++) {
                System.err.println("  " + (i + 1) + ". " + errors.get(i));
            }
            System.err.println("═══════════════════════════════════════════════════════════════");
            System.err.println();
            System.err.println("💡 SOLUTION: Set the required environment variables:");
            System.err.println("   export DB_PASSWORD=your_database_password");
            System.err.println("   export JWT_SECRET=$(openssl rand -base64 48)");
            System.err.println("   export EMAIL_USERNAME=your_email@domain.com");
            System.err.println("   export EMAIL_PASSWORD=your_email_password");
            System.err.println();
            System.err.println("   Or disable email: export EMAIL_ENABLED=false");
            System.err.println();

            log.error("❌ [SECURITY] Security configuration validation failed!");
            throw new IllegalStateException(
                    "Security configuration validation failed: " + String.join("; ", errors));
        }

        log.info("🔐 [SECURITY] All security configurations validated successfully!");
    }

    /**
     * Checks if a string is null, empty, or contains only whitespace.
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
