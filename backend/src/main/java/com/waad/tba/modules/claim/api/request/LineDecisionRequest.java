package com.waad.tba.modules.claim.api.request;

import com.waad.tba.modules.claim.entity.LineReviewDecision;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Claim Line Reviewer Decision Request
 * ════════════════════════════════════════════════════════════════════════════
 *
 * CLAIM-REVIEW-SPLIT-2C. Extended by DOCUMENTS-REVIEW-UX-1 with a real,
 * financially-connected partial-rejection amount (matching the Batch entry
 * screen's full/partial rejection UX) — previously this request only ever
 * recorded reviewer intent/notes with zero effect on money; now a REJECTED
 * decision genuinely recomputes that line's refusedAmount/companyShare (see
 * {@code ClaimService.submitLineDecision}).
 *
 * BUSINESS RULES:
 * - {@code decision} is mandatory.
 * - {@code reason} is mandatory when decision is REJECTED or
 *   CLARIFICATION_REQUIRED (enforced in the service layer, since it is
 *   conditional on {@code decision} rather than always-required).
 * - {@code manualRefusedAmount} is only meaningful when decision is REJECTED:
 *   null/omitted means "reject the line's entire company share" (full
 *   rejection, matching the Batch screen's "رفض كلي"); a positive value less
 *   than the line's company share means a partial rejection ("رفض جزئي") —
 *   the service layer caps/validates this against the line's actual
 *   companyShareBeforeDiscount.
 * - This still never directly touches claim-level financial fields
 *   (approvedAmount, netProviderAmount, patientCoPay, requestedAmount) itself
 *   — those are only ever set by {@code POST /claims/{id}/approve}, which
 *   now re-sums them from the live lines at approval time.
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

    /**
     * Only used when {@code decision == REJECTED}. Null/omitted = full
     * rejection (the entire company share). A positive amount less than the
     * line's company share = partial rejection.
     */
    @DecimalMin(value = "0.00", message = "Manual refused amount cannot be negative")
    private BigDecimal manualRefusedAmount;
}
