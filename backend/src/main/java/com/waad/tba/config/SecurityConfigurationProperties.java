package com.waad.tba.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Security Configuration Properties
 * 
 * Binds application security settings from application.yml.
 * 
 * Configuration keys:
 * - app.security.password-reset-token-validity-hours (default: 1)
 * - app.security.email-verification-token-validity-hours (default: 24)
 * - app.security.require-email-verification (default: false)
 * - app.security.account-lockout-duration-minutes (default: 30)
 * - app.security.max-failed-login-attempts (default: 5)
 * - app.frontend.url (default: http://localhost:3000)
 * - app.email.from (default: support@alwahacare.com)
 * - app.email.from-name (default: AlWaha Care Support)
 * 
 * @author TBA WAAD System
 * @version 1.0
 * @since 2024
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class SecurityConfigurationProperties {

    private final Security security = new Security();
    private final Frontend frontend = new Frontend();
    private final Email email = new Email();

    @Data
    public static class Security {
        /**
         * Password reset token validity in hours
         */
        private int passwordResetTokenValidityHours = 1;

        /**
         * Email verification token validity in hours
         */
        private int emailVerificationTokenValidityHours = 24;

        /**
         * Require email verification before allowing login
         * Set to false in development, true in production
         */
        private boolean requireEmailVerification = false;

        /**
         * Account lockout duration in minutes
         */
        private int accountLockoutDurationMinutes = 30;

        /**
         * Maximum failed login attempts before account lockout
         */
        private int maxFailedLoginAttempts = 5;
    }

    @Data
    public static class Frontend {
        /**
         * Frontend URL for email links
         */
        private String url = "http://localhost:3000";
    }

    @Data
    public static class Email {
        /**
         * Email from address
         */
        private String from = "support@alwahacare.com";

        /**
         * Email from name
         */
        private String fromName = "AlWaha Care Support";
    }
}
