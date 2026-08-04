package com.waad.tba.modules.preauthorization.dto;

import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineReviewDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): input for
 * PreAuthorizationService.submitLineDecision(...) — a reviewer's decision
 * for one line of a pre-authorization. Mirrors Claim's LineDecisionRequest
 * shape, simplified: no manualRefusedAmount/partial-rejection concept since
 * PreAuthorization lines have no discount/companyShare split.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationLineDecisionDto {

    private LineReviewDecision decision;

    /** Required when decision is REJECTED or CLARIFICATION_REQUIRED. */
    private String reason;

    /**
     * Only used when decision == APPROVED. Null/omitted = approve at the
     * line's full contractPrice. Must not exceed the line's own
     * contractPrice (enforced in the service layer).
     */
    private BigDecimal approvedAmount;
}
