package com.waad.tba.modules.medicalclassification.pricelist.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Financial Validation Engine finding (A10). The publish gate:
 * BLOCKER must be RESOLVED (never waivable); WARNING must be RESOLVED or
 * WAIVED with an audited note; INFO is informational.
 */
@Entity
@Table(name = "price_list_validation_findings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListValidationFinding {

    public enum Severity { BLOCKER, WARNING, INFO }

    public enum Status { OPEN, RESOLVED, WAIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "import_id")
    private Long importId;

    /** Staging line / pricing item id this finding points at (nullable for aggregates). */
    @Column(name = "line_ref")
    private Long lineRef;

    @Column(name = "line_ref_type", length = 20)
    private String lineRefType;

    @Column(name = "finding_type", nullable = false, length = 40)
    private String findingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(name = "old_price", precision = 15, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", precision = 15, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "change_percent", precision = 8, scale = 2)
    private BigDecimal changePercent;

    @Column(name = "reference_value", precision = 15, scale = 2)
    private BigDecimal referenceValue;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.OPEN;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "waiver_note", length = 1000)
    private String waiverNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
