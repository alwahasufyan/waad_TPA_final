package com.waad.tba.modules.medicaltaxonomy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Medical Specialty — lookup table for service classification.
 *
 * <p>Used to group medical services by clinical discipline
 * (e.g. Cardiology, Orthopedics, ICU). The specialty_id FK on
 * {@link MedicalService} is nullable; existing services are unaffected.
 *
 * <p>Table: {@code medical_specialties} (created by V86)
 */
@Entity
@Table(name = "medical_specialties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalSpecialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique business code — immutable after creation. E.g. "SP-ICU", "SP-ORTHO" */
    @Column(unique = true, nullable = false, length = 50)
    private String code;

    /** Display name in Arabic (system default language) */
    @Column(name = "name_ar", nullable = false, length = 255)
    private String nameAr;

    /** Display name in English (optional) */
    @Column(name = "name_en", length = 255)
    private String nameEn;

    /**
     * FK → medical_categories.id  (added V90)
     * NOT NULL after V90 migration runs.
     */
    @Column(name = "category_id")
    private Long categoryId;

    /** Soft-delete flag — never use hard DELETE on reference data */
    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;
}
