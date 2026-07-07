package com.waad.tba.modules.medicaltaxonomy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Medical Service ↔ Category junction entity (multi-context mapping).
 *
 * <p>Allows one medical service to be classified under multiple categories
 * across different clinical contexts (OUTPATIENT, INPATIENT, EMERGENCY, ANY).
 *
 * <p>Table: {@code medical_service_categories} (created in V83)
 */
@Entity
@Table(name = "medical_service_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "msc_seq")
    @SequenceGenerator(name = "msc_seq", sequenceName = "medical_service_category_seq", allocationSize = 50)
    private Long id;

    /**
     * FK → medical_services.id
     */
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    /**
     * FK → medical_categories.id
     */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    /**
     * Clinical context for this classification.
     * Controls which category grouping is used during claim/pre-auth processing.
     */
    @Column(name = "context", nullable = false, length = 20)
    @Builder.Default
    private String context = "ANY";

    /**
     * Marks the primary category for this service+context combination.
     * At most one primary mapping per (service_id, context).
     */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;

    /** Active flag — soft-disable a category link without deleting (added V90) */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
