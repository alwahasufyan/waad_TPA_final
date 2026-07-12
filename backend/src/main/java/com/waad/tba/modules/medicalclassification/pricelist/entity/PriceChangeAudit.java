package com.waad.tba.modules.medicalclassification.pricelist.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mandatory audit record for in-place exception edits (MC-4C, E2).
 * Safe by design: claims snapshot all their amounts at claim time, so an
 * in-place price change never alters historical results — this table keeps
 * the {@code ClaimLine.pricingItemId} pointer's story reconstructible.
 */
@Entity
@Table(name = "price_change_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceChangeAudit {

    /**
     * MC-4C operation types (2026-07-12). The first six are the simplified
     * direct-edit vocabulary; the last three are legacy values retained so old
     * audit rows and the deprecated exception/patch flow still validate.
     */
    public enum ChangeType {
        PRICE_CORRECTION, ADD_SERVICE, DEACTIVATE_SERVICE, CLASSIFICATION_CORRECTION,
        VERSION_IMPORT, VERSION_RESTORE,
        PRICE_EDIT, SERVICE_ADDED, SERVICE_DEACTIVATED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "pricing_item_id")
    private Long pricingItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    private ChangeType changeType;

    @Column(name = "service_code", length = 50)
    private String serviceCode;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Column(name = "old_price", precision = 15, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", precision = 15, scale = 2)
    private BigDecimal newPrice;

    /** Generic before value for non-price changes (old code/category/status). */
    @Column(name = "old_value", length = 500)
    private String oldValue;

    /** Generic after value for non-price changes (new code/category/status). */
    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
