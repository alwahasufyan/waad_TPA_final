package com.waad.tba.modules.claim.entity;

/**
 * CLAIM-REVIEW-SPLIT-2C: a reviewer's persisted decision on a single
 * {@link ClaimLine}, independent of the claim's overall status/approval.
 *
 * This is reviewer intent/notes, not a financial instruction — it does not
 * itself change {@code Claim.approvedAmount}/{@code netProviderAmount} at
 * save time. Only the claim's own approval flow ({@code POST
 * /claims/{id}/approve}) computes those fields.
 */
public enum LineReviewDecision {
    APPROVED,
    REJECTED,
    CLARIFICATION_REQUIRED
}
