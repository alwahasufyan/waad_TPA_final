package com.waad.tba.common.email;

/**
 * Email template data for account locked notification
 */
public record AccountLockedData(
    String recipientEmail,
    String recipientName,
    String lockedUntil,
    int failedAttempts
) {}
