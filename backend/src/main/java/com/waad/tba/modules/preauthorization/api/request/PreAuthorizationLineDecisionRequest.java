package com.waad.tba.modules.preauthorization.api.request;

import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineReviewDecision;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Pre-Authorization Line Decision Request (WAAD-PREAUTH-MULTI-LINE-1)
 * ════════════════════════════════════════════════════════════════════════════
 *
 * POST /pre-authorizations/{id}/lines/{lineId}/decision. reason is required
 * when decision is REJECTED or CLARIFICATION_REQUIRED — enforced in the
 * service layer since it is conditional on decision, not always required.
 * approvedAmount is only meaningful when decision is APPROVED; the service
 * layer enforces it cannot exceed the line's own contractPrice.
 */
public record PreAuthorizationLineDecisionRequest(
        @NotNull(message = "القرار مطلوب")
        LineReviewDecision decision,

        @Size(max = 500, message = "السبب لا يمكن أن يتجاوز 500 حرف")
        String reason,

        @DecimalMin(value = "0.00", message = "المبلغ المعتمد لا يمكن أن يكون سالبًا")
        @Digits(integer = 13, fraction = 2, message = "المبلغ المعتمد غير صالح")
        BigDecimal approvedAmount) {
}
