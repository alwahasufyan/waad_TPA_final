package com.waad.tba.common.email;

/**
 * Email template data for password reset email
 */
public record PasswordResetData(
    String recipientEmail,
    String recipientName,
    String resetToken,
    String resetUrl
) {}
