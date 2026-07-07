package com.waad.tba.common.email;

/**
 * Email template data for verification email
 */
public record EmailVerificationData(
    String recipientEmail,
    String recipientName,
    String verificationToken,
    String verificationUrl
) {}
