package com.waad.tba.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method Security Configuration — Static Role-Based Authorization (Phase 5)
 *
 * Enables @PreAuthorize annotations on controller methods.
 * No custom PermissionEvaluator needed — all checks are role-based.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Slf4j
public class MethodSecurityConfig {
    // No custom beans needed. Spring Security default expression handler
    // handles hasRole(), hasAnyRole(), isAuthenticated() out of the box.
}
