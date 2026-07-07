package com.waad.tba.modules.provider.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * ProviderService Entity (Junction Table)
 * 
 * Represents many-to-many relationship between Provider and MedicalService.
 * 
 * Key Design Principles:
 * - References MedicalService by CODE (not FK) - loose coupling
 * - Unique constraint: (providerId, serviceCode)
 * - Soft delete pattern (active flag)
 * - Immutable after creation (no updates, only add/remove)
 * 
 * Philosophy:
 * - Provider "offers" a medical service
 * - Service code must exist in MedicalTaxonomy.MedicalService
 * - Validation happens in service layer (not database FK)
 */
@Entity
@Table(
    name = "provider_services",
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_provider_service", 
                         columnNames = {"provider_id", "service_code"})
    },
    indexes = {
        @Index(name = "idx_provider_services_provider", columnList = "provider_id"),
        @Index(name = "idx_provider_services_code", columnList = "service_code"),
        @Index(name = "idx_provider_services_active", columnList = "active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Provider ID (FK to providers table)
     * 
     * IMMUTABLE after creation.
     */
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /**
     * Service code (references MedicalService.code)
     * 
     * IMMUTABLE after creation.
     * Must exist in medical_services table AND be active.
     * 
     * Examples: SRV-CARDIO-001, SRV-NEURO-CONSULT
     */
    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    /**
     * Active flag (soft delete)
     * 
     * When false, service assignment is removed.
     * Preserves history of service offerings.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Creation timestamp
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Pre-persist callback: Normalize service code
     */
    @PrePersist
    protected void onCreate() {
        if (serviceCode != null) {
            serviceCode = serviceCode.trim().toUpperCase();
        }
    }
}
