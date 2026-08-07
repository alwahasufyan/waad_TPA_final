package com.waad.tba.modules.preauthorization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): one service line within a
 * PreAuthorizationResponseDto.lines response — full detail including the
 * per-line reviewer decision, unlike the header's own financial fields
 * which stay intentionally null in the response (pre-auth is non-financial
 * at the header level, per prior product decision — see
 * PreAuthorizationService.mapToResponseDto). Per-line financial fields ARE
 * exposed here since the user explicitly requires "each line keeps its own
 * approved amount and decision" to be visible to the reviewer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationLineResponseDto {

    private Long id;
    private Integer lineNumber;

    private Long pricingItemId;
    private String serviceCode;
    private String serviceName;
    private Long serviceCategoryId;
    private String serviceCategoryName;

    /** WAAD-PREAUTH-LINE-QUANTITY-FIX-1: units/sessions requested. */
    private Integer quantity;
    /** Per-unit catalog price snapshot; contractPrice = unitPrice * quantity. */
    private BigDecimal unitPrice;
    private BigDecimal contractPrice;
    private Boolean requiresPA;

    private BigDecimal approvedAmount;
    private BigDecimal copayAmount;
    private BigDecimal copayPercentage;
    private BigDecimal insuranceCoveredAmount;

    /** APPROVED, REJECTED, CLARIFICATION_REQUIRED, or null (no decision yet). */
    private String reviewerDecision;
    private String rejectionReason;
}
