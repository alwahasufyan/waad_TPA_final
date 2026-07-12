package com.waad.tba.modules.medicaltaxonomy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Alternative / colloquial display aliases for medical services.
 *
 * <p>Stores additional Arabic (and optionally English) names used in search-as-you-type
 * and data-entry autocomplete for claim and pre-authorization workflows.
 *
 * <p>Also the backbone of the Medical Classification Engine's learning loop
 * (MC-2/MC-6): every reviewer approval or MC-4C linked add-service can write
 * an alias here, so the next import recognizes the same wording automatically.
 *
 * <p>Table: {@code ent_service_aliases} (created in V70; {@code active} added in V75)
 */
@Entity
@Table(name = "ent_service_aliases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "alias_seq")
    @SequenceGenerator(name = "alias_seq", sequenceName = "ent_service_alias_seq", allocationSize = 50)
    private Long id;

    /**
     * FK → medical_services.id
     */
    @Column(name = "medical_service_id", nullable = false)
    private Long medicalServiceId;

    /**
     * The alias text (alternative name for the service)
     */
    @Column(name = "alias_text", nullable = false, length = 255)
    private String aliasText;

    /**
     * BCP-47 locale for this alias. Default: "ar" (Arabic)
     */
    @Column(name = "locale", nullable = false, length = 10)
    @Builder.Default
    private String locale = "ar";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    /**
     * Provenance of this learned wording: REVIEWER_DECISION (MC-2 review
     * approval) | ADD_SERVICE (MC-4C add-service linked to a catalog service)
     * | MANUAL (admin-entered) | ODOO_MIGRATION | SYNONYM_FILE.
     */
    @Column(name = "source", length = 30)
    private String source;

    /** Reserved for future confidence-weighted matching; unused in MC-6 Lite. */
    @Column(name = "weight", precision = 5, scale = 2)
    private BigDecimal weight;

    /** Soft-disable: false = no longer used for auto-matching, kept for audit. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
