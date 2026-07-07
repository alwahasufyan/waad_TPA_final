package com.waad.tba.modules.medicaltaxonomy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Alternative / colloquial display aliases for medical services.
 *
 * <p>Stores additional Arabic (and optionally English) names used in search-as-you-type
 * and data-entry autocomplete for claim and pre-authorization workflows.
 *
 * <p>Table: {@code ent_service_aliases} (created in V83)
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

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
