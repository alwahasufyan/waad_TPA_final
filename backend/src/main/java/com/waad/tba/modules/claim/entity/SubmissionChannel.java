package com.waad.tba.modules.claim.entity;

/**
 * How a claim entered the system — distinguishes provider-portal submissions
 * from claims entered manually (paper claims) by a medical reviewer/admin via
 * the batch entry screen. Used to keep unreviewed provider-portal claims out
 * of the financial batch/monthly screens (PROVIDER-PORTAL-REVIEW-ROUTING-2).
 */
public enum SubmissionChannel {
    PROVIDER_PORTAL,
    MANUAL_ENTRY
}
