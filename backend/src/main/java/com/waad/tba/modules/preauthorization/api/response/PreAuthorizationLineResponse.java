package com.waad.tba.modules.preauthorization.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Pre-Authorization Line Response (WAAD-PREAUTH-MULTI-LINE-1)
 * ════════════════════════════════════════════════════════════════════════════
 *
 * One service line within {@link PreAuthorizationResponse#getLines()}.
 * Per-line financial fields ARE exposed here (approvedAmount, copay, etc.)
 * even though the header's own equivalent fields stay null in the response
 * by prior product decision (pre-auth is non-financial at the header
 * level) — the per-line decision/amount is what a reviewer needs to see.
 *
 * @since API v1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthorizationLineResponse {

    private Long id;
    private Integer lineNumber;

    private Long pricingItemId;
    private String serviceCode;
    private String serviceName;
    private Long serviceCategoryId;
    private String serviceCategoryName;

    private BigDecimal contractPrice;
    private Boolean requiresPA;

    private BigDecimal approvedAmount;
    private BigDecimal copayAmount;
    private BigDecimal copayPercentage;
    private BigDecimal insuranceCoveredAmount;

    private String reviewerDecision;
    private String rejectionReason;
}
