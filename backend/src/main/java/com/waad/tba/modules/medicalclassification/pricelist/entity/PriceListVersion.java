package com.waad.tba.modules.medicalclassification.pricelist.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable-after-activation price-list version (the versioning backbone).
 * Publish/activate/diff logic arrives in MC-3; MC-1 uses it read-only
 * (Version-1 backfill is done in SQL by V71).
 */
@Entity
@Table(name = "provider_price_list_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListVersion {

    public enum Status { DRAFT, ACTIVE, SUPERSEDED, ARCHIVED }

    /** Auditor-facing provenance (D2/F7: never shown in the exception dialog). */
    public enum SourceType { IMPORT, PATCH, ROLLBACK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "source_import_id")
    private Long sourceImportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    @Builder.Default
    private SourceType sourceType = SourceType.IMPORT;

    public boolean isPatchLike() {
        return sourceType == SourceType.PATCH || sourceType == SourceType.ROLLBACK;
    }

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "published_by", length = 100)
    private String publishedBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "notes", length = 2000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
