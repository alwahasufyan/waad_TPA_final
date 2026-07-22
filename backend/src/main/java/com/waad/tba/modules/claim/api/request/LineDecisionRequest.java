package com.waad.tba.modules.claim.api.request;

import com.waad.tba.modules.claim.entity.LineReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Claim Line Reviewer Decision Request
 * ════════════════════════════════════════════════════════════════════════════
 *
 * CLAIM-REVIEW-SPLIT-2C.
 *
 * BUSINESS RULES:
 * - {@code decision} is mandatory.
 * - {@code reason} is mandatory when decision is REJECTED or
 *   CLARIFICATION_REQUIRED (enforced in the service layer, since it is
 *   conditional on {@code decision} rather than always-required).
 * - This is reviewer intent/notes only — it never changes claim-level
 *   financial fields (approvedAmount, netProviderAmount, patientCoPay,
 *   requestedAmount). The only path that sets those is
 *   {@code POST /claims/{id}/approve}.
 *
 * @since API v1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineDecisionRequest {

    @NotNull(message = "Decision is required")
    private LineReviewDecision decision;

    /**
     * Required when {@code decision} is REJECTED or CLARIFICATION_REQUIRED.
     * Reused for both — it holds "the reason for this decision" generically
     * (why the line was rejected, or why clarification is needed), matching
     * the existing {@code ClaimLine.rejectionReason} column it is persisted
     * into rather than introducing a second, parallel reason field.
     */
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;

    @Size(max = 4000, message = "Reviewer notes must not exceed 4000 characters")
    private String reviewerNotes;
}
