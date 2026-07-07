package com.waad.tba.modules.claim.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Claim Batch Entity - Real monthly groups for claims.
 * Replaces the calculated virtual batches in the frontend.
 */
@Entity
@Table(name = "claim_batches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique batch code generated from DB.
     * Format: [EMP_CODE][YY]-[MM]-[SERIAL]
     */
    @Column(name = "batch_code", nullable = false, unique = true, length = 30)
    private String batchCode;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "employer_id", nullable = false)
    private Long employerId;

    @Column(name = "batch_year", nullable = false)
    private Integer batchYear;

    @Column(name = "batch_month", nullable = false)
    private Integer batchMonth;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ClaimBatchStatus status = ClaimBatchStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = ClaimBatchStatus.OPEN;
    }

    public enum ClaimBatchStatus {
        /** Open for new claims */
        OPEN("مفتوحة"),
        /** Automatically or manually closed after month end */
        CLOSED("مغلقة"),
        /** Locked by finance for settlement */
        LOCKED("مقفلة");

        private final String arabicLabel;

        ClaimBatchStatus(String arabicLabel) {
            this.arabicLabel = arabicLabel;
        }

        public String getArabicLabel() {
            return arabicLabel;
        }
    }
}
