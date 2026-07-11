package com.waad.tba.modules.medicalclassification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit trail of every classification/mapping decision on a catalog service
 * (plan §10): who classified what, from which import line, with what
 * confidence. Feeds the Classification Dashboard (MC-4).
 */
@Entity
@Table(name = "catalog_classification_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogClassificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "medical_service_id", nullable = false)
    private Long medicalServiceId;

    @Column(name = "category_id_old")
    private Long categoryIdOld;

    @Column(name = "category_id_new")
    private Long categoryIdNew;

    /** IMPORT_REVIEW / ADMIN / MIGRATION */
    @Column(name = "change_source", nullable = false, length = 20)
    private String changeSource;

    @Column(name = "import_line_id")
    private Long importLineId;

    @Column(name = "confidence_at_decision", precision = 5, scale = 1)
    private BigDecimal confidenceAtDecision;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
