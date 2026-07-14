package com.waad.tba.modules.medicalclassification.pricelist.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One staged service line from a provider file, carrying the engine's
 * classification result and the reviewer's (future, MC-2) decision.
 *
 * A6: unknown services live here and ONLY here — no MedicalService row is
 * created before an explicit reviewer approval.
 * A4/A5: review_status bands control queue visibility, never approval.
 */
@Entity
@Table(name = "price_list_import_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListImportLine {

    public enum ReviewStatus { PENDING_BULK, NEEDS_REVIEW, APPROVED, REJECTED }

    public enum ApprovalMode { INDIVIDUAL, BULK_REMAINING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_id", nullable = false)
    private Long importId;

    @Column(name = "row_no")
    private Integer rowNo;

    @Column(name = "source_sheet", length = 255)
    private String sourceSheet;

    // ── raw payload ──────────────────────────────────────────────────────────

    @Column(name = "raw_name", nullable = false, length = 500)
    private String rawName;

    @Column(name = "raw_name_alt", length = 500)
    private String rawNameAlt;

    @Column(name = "raw_code", length = 100)
    private String rawCode;

    @Column(name = "raw_price", precision = 15, scale = 2)
    private BigDecimal rawPrice;

    @Column(name = "raw_category_text", length = 255)
    private String rawCategoryText;

    // ── engine result ────────────────────────────────────────────────────────

    @Column(name = "normalized_name", length = 500)
    private String normalizedName;

    @Column(name = "matched_service_id")
    private Long matchedServiceId;

    @Column(name = "matched_service_code", length = 50)
    private String matchedServiceCode;

    @Column(name = "suggested_main_category", length = 255)
    private String suggestedMainCategory;

    /** TAX-1: optional delivery/coverage context, never a medical category. */
    @Column(name = "coverage_context", length = 20)
    private String coverageContext;

    @Column(name = "suggested_category_id")
    private Long suggestedCategoryId;

    /** Approved sub-category label as emitted by the engine ("CAT0xx - ..."). */
    @Column(name = "suggested_sub_label", length = 255)
    private String suggestedSubLabel;

    @Column(name = "confidence_score", precision = 5, scale = 1)
    private BigDecimal confidenceScore;

    @Column(name = "match_method", length = 30)
    private String matchMethod;

    @Column(name = "classification_source", length = 20)
    private String classificationSource;

    /** The script's human-readable reason string — reviewers rely on it. */
    @Column(name = "engine_reason", length = 500)
    private String engineReason;

    /** The engine's closest reference suggestion (what it compared against). */
    @Column(name = "reference_match", length = 500)
    private String referenceMatch;

    /** Comma-separated guard flags (DUPLICATE_IN_FILE, CATEGORY_UNRESOLVED, ...). */
    @Column(name = "flags", length = 255)
    private String flags;

    // ── queue + decision ─────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.NEEDS_REVIEW;

    @Column(name = "final_service_id")
    private Long finalServiceId;

    @Column(name = "final_category_id")
    private Long finalCategoryId;

    @Column(name = "final_price", precision = 15, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", length = 20)
    private ApprovalMode approvalMode;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
