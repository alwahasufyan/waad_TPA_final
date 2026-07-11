package com.waad.tba.modules.medicalclassification.pricelist.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One uploaded provider price-list file (aggregate root of the import flow).
 *
 * Idempotency (MC-1 owner condition #1): a partial unique index
 * (provider_id, file_hash) over non-terminal statuses blocks duplicate
 * uploads of the same file; FAILED/CANCELLED imports never block a retry.
 */
@Entity
@Table(name = "price_list_imports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListImport {

    public enum Status { UPLOADED, PROCESSING, CLASSIFIED, IN_REVIEW, REVIEW_COMPLETE, PUBLISHED, FAILED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "channel", nullable = false, length = 20)
    @Builder.Default
    private String channel = "PRICE_LIST";

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    /** SHA-256 of the uploaded file — the idempotency key. */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "file_storage_path", length = 1000)
    private String fileStoragePath;

    @Column(name = "provider_type_hint", length = 20)
    private String providerTypeHint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.UPLOADED;

    // ── provenance (MC-1 owner condition #2) ─────────────────────────────────

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "engine_version", length = 50)
    private String engineVersion;

    @Column(name = "fuzz_engine", length = 20)
    private String fuzzEngine;

    /** JSON: sha256/size of synonyms, Odoo KB, reference, categories at run time. */
    @Column(name = "dictionary_version", length = 1000)
    private String dictionaryVersion;

    @Column(name = "execution_ms")
    private Long executionMs;

    /** JSON snapshot of the classification_settings used for this run. */
    @Column(name = "threshold_config", columnDefinition = "TEXT")
    private String thresholdConfig;

    // ── counters ─────────────────────────────────────────────────────────────

    @Column(name = "total_lines", nullable = false)
    @Builder.Default
    private Integer totalLines = 0;

    @Column(name = "known_services", nullable = false)
    @Builder.Default
    private Integer knownServices = 0;

    @Column(name = "unknown_services", nullable = false)
    @Builder.Default
    private Integer unknownServices = 0;

    @Column(name = "low_confidence", nullable = false)
    @Builder.Default
    private Integer lowConfidence = 0;

    @Column(name = "duplicates", nullable = false)
    @Builder.Default
    private Integer duplicates = 0;

    @Column(name = "approved_count", nullable = false)
    @Builder.Default
    private Integer approvedCount = 0;

    @Column(name = "rejected_count", nullable = false)
    @Builder.Default
    private Integer rejectedCount = 0;

    // ── audit ────────────────────────────────────────────────────────────────

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isTerminal() {
        return status == Status.FAILED || status == Status.CANCELLED || status == Status.PUBLISHED;
    }
}
