package com.waad.tba.modules.preauthorization.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PreAuthorizationLine Entity (WAAD-PREAUTH-MULTI-LINE-1, Phase 1)
 *
 * One PreAuthorization header can now cover multiple services, each
 * represented by one PreAuthorizationLine — mirrors ClaimLine/Claim
 * exactly (see backend/.../claim/entity/ClaimLine.java).
 *
 * Phase 1 scope: additive schema/entity only. {@code reviewerDecision} and
 * {@code rejectionReason} are schema-ready but not yet written by any
 * service-layer logic — that wiring (per-line approve/reject/clarify,
 * PARTIALLY_APPROVED header status) is Phase 2.
 *
 * WAAD-PREAUTH-LINE-QUANTITY-FIX-1: {@code quantity}/{@code unitPrice} were
 * added after it was discovered the provider submission form always let
 * providers request a quantity (e.g. "5 sessions") that was silently
 * discarded — {@code contractPrice} is now the computed line total
 * ({@code unitPrice * quantity}), mirroring ClaimLine's own
 * quantity/unitPrice/totalPrice pattern.
 */
@Entity
@Table(name = "pre_authorization_lines", indexes = {
        @Index(name = "idx_preauth_line_header", columnList = "pre_authorization_id"),
        @Index(name = "idx_preauth_line_category", columnList = "service_category_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pre_authorization_id", nullable = false)
    private PreAuthorization preAuthorization;

    /**
     * 1-based, stable ordering for UI/report display.
     */
    @Column(name = "line_number", nullable = false)
    @Builder.Default
    private Integer lineNumber = 1;

    // ==================== SERVICE IDENTIFICATION (snapshot) ====================

    /**
     * Pricing Item ID (from provider_contract_pricing_items). No FK —
     * intentional snapshot decoupling, same convention as
     * claim_lines.pricing_item_id (see V68__add_safe_referential_integrity_fks.sql).
     */
    @Column(name = "pricing_item_id")
    private Long pricingItemId;

    @Column(name = "service_code", length = 50, nullable = false)
    private String serviceCode;

    @Column(name = "service_name", length = 200)
    private String serviceName;

    @Column(name = "service_type", length = 100, nullable = false)
    @Builder.Default
    private String serviceType = "MEDICAL";

    @Column(name = "service_category_id", nullable = false)
    private Long serviceCategoryId;

    @Column(name = "service_category_name", length = 200)
    private String serviceCategoryName;

    // ==================== PRICING (WAAD-PREAUTH-LINE-QUANTITY-FIX-1) ====================

    /**
     * Number of units/sessions requested for this line (e.g. "5 physical
     * therapy sessions" = quantity 5). Defaults to 1 for the common
     * single-unit case.
     */
    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    /**
     * Per-unit catalog price snapshot (from ProviderContractPricingItem at
     * request time). {@code contractPrice} is the computed line total
     * ({@code unitPrice * quantity}) — everything downstream (limit checks,
     * approval ceilings, claim conversion) continues to read
     * {@code contractPrice} as "this line's full requested amount".
     */
    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "contract_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal contractPrice;

    @Column(name = "requires_pa", nullable = false)
    @Builder.Default
    private Boolean requiresPA = true;

    // ==================== COVERAGE SNAPSHOT (FINANCIAL AUDIT TRAIL) ====================

    @Column(name = "coverage_percent_snapshot")
    private Integer coveragePercentSnapshot;

    @Column(name = "patient_copay_percent_snapshot")
    private Integer patientCopayPercentSnapshot;

    // ==================== APPROVAL OUTCOME (per line) ====================

    @Column(name = "approved_amount", precision = 10, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "copay_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal copayAmount = BigDecimal.ZERO;

    @Column(name = "copay_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal copayPercentage = BigDecimal.ZERO;

    @Column(name = "insurance_covered_amount", precision = 10, scale = 2)
    private BigDecimal insuranceCoveredAmount;

    @Column(name = "reserved_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal reservedAmount = BigDecimal.ZERO;

    /**
     * Per-line reviewer decision — schema-ready in Phase 1, wired to actual
     * approve/reject/clarify workflow logic in Phase 2
     * (PreAuthorizationService.submitLineDecision).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_decision", length = 30)
    private LineReviewDecision reviewerDecision;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Mirrors ClaimLine.LineReviewDecision — kept as a nested enum here since
     * PreAuthorizationLine is the only entity that uses it (no shared
     * cross-module enum exists for Claim's version either).
     */
    public enum LineReviewDecision {
        APPROVED,
        REJECTED,
        CLARIFICATION_REQUIRED
    }
}
