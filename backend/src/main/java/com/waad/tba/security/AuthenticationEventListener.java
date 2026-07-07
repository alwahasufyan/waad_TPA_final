package com.waad.tba.security;

import com.waad.tba.modules.rbac.service.UserSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Authentication Event Listener
 * 
 * Listens to Spring Security authentication events and integrates with
 * the UserSecurityService for account lockout and audit logging.
 * 
 * Events Handled:
 * - AuthenticationSuccessEvent: Records successful login, resets failed attempts
 * - AuthenticationFailureBadCredentialsEvent: Records failed login, increments counter
 * 
 * Account Lockout:
 * - After 5 failed login attempts, account is locked for 30 minutes
 * - Lockout notification email is sent to user
 * - Audit log entry is created
 * 
 * @author TBA WAAD System
 * @version 1.0
 * @since 2024
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationEventListener {

    private final UserSecurityService securityService;

    /**
     * Handle successful authentication
     * 
     * Resets failed login counter and updates last login timestamp.
     * Creates audit log entry for successful login.
     */
    @EventListener
    public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        
        if (principal instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) principal;
            String username = userDetails.getUsername();
            
            log.info("Authentication success for user: {}", username);
            
            try {
                securityService.recordSuccessfulLogin(username);
            } catch (Exception e) {
                log.error("Failed to record successful login for user: {}", username, e);
            }
        }
    }

    /**
     * Handle failed authentication (bad credentials)
     * 
     * Increments failed login counter. After 5 attempts:
     * - Account is locked for 30 minutes
     * - Lockout notification email is sent
     * - Audit log entry is created
     */
    @EventListener
    public void handleAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        
        log.warn("Authentication failure for user: {}", username);
        
        try {
            securityService.recordFailedLogin(username);
        } catch (Exception e) {
            log.error("Failed to record failed login for user: {}", username, e);
        }
    }
}
