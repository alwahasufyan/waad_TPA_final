package com.waad.tba.modules.provider.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ProviderContract Entity
 * 
 * Represents a contract price for a specific service at a specific provider.
 * Each record defines: Provider + Service + Price + Date Range
 * 
 * Design Philosophy:
 * - Flat design: One record per provider-service-period combination
 * - Loose coupling: References serviceCode (not FK) like ProviderService
 * - Time-based: Supports effective dates and price history
 * - Audit trail: Tracks who created/updated and when
 * 
 * Based on Odoo analysis:
 * - Combines Contract (period/status) + Price List (service/price)
 * - Improves on Odoo: Proper FK, validation, audit fields
 * 
 * Example:
 * - Provider: المستشفي الليبي الدولي (ID: 123)
 * - Service: CT Scan (Code: CT-010)
 * - Price: 150.00 LYD
 * - Period: 2025-01-01 to 2025-12-31
 */
@Entity(name = "LegacyProviderContract")
@Table(
    name = "legacy_provider_contracts",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_provider_contract_service_date",
            columnNames = {"provider_id", "service_code", "effective_from"}
        )
    },
    indexes = {
        @Index(name = "idx_legacy_provider_contracts_provider", columnList = "provider_id"),
        @Index(name = "idx_legacy_provider_contracts_service", columnList = "service_code"),
        @Index(name = "idx_legacy_provider_contracts_dates", columnList = "effective_from, effective_to"),
        @Index(name = "idx_legacy_provider_contracts_active", columnList = "active")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Provider Reference
     * FK to Provider entity
     */
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /**
     * Service Reference
     * References MedicalService.code (loose coupling)
     * Runtime validation in service layer
     * 
     * Design: Same pattern as ProviderService entity
     */
    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    /**
     * Contract Price
     * The agreed price for this service at this provider
     * Must be >= 0
     */
    @Column(name = "contract_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal contractPrice;

    /**
     * Currency
     * Default: LYD (Libyan Dinar)
     * ISO 4217 currency code
     */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "LYD";

    /**
     * Effective From Date
     * Contract starts on this date (inclusive)
     * Required field
     */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /**
     * Effective To Date
     * Contract ends on this date (inclusive)
     * NULL = open-ended contract (ongoing)
     * 
     * Business Rule: Must be >= effectiveFrom if not null
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * Active Status
     * Soft delete pattern
     * true = Active contract
     * false = Deleted/Inactive contract
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Notes
     * Optional contract notes or comments
     */
    @Column(name = "notes", length = 500)
    private String notes;

    // ==================== AUDIT FIELDS ====================

    /**
     * Created At
     * Timestamp when contract was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Updated At
     * Timestamp when contract was last updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Created By
     * Username/email of user who created the contract
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * Updated By
     * Username/email of user who last updated the contract
     */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ==================== LIFECYCLE CALLBACKS ====================

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== BUSINESS LOGIC HELPERS ====================

    /**
     * Check if contract is currently effective
     * 
     * @param date Date to check
     * @return true if contract is effective on this date
     */
    public boolean isEffectiveOn(LocalDate date) {
        if (!active) {
            return false;
        }
        
        boolean afterStart = !date.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || !date.isAfter(effectiveTo);
        
        return afterStart && beforeEnd;
    }

    /**
     * Check if contract is currently effective (today)
     * 
     * @return true if contract is effective today
     */
    public boolean isCurrentlyEffective() {
        return isEffectiveOn(LocalDate.now());
    }

    /**
     * Check if contract has expired
     * 
     * @return true if contract has expired
     */
    public boolean isExpired() {
        if (!active) {
            return false; // Inactive contracts are not "expired", they're deleted
        }
        
        if (effectiveTo == null) {
            return false; // Open-ended contracts never expire
        }
        
        return LocalDate.now().isAfter(effectiveTo);
    }

    /**
     * Check if contract is open-ended
     * 
     * @return true if contract has no end date
     */
    public boolean isOpenEnded() {
        return effectiveTo == null;
    }

    /**
     * Validate date range
     * 
     * @throws IllegalArgumentException if dates are invalid
     */
    public void validateDateRange() {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException(
                "Effective to date must be >= effective from date"
            );
        }
    }

    /**
     * Validate price
     * 
     * @throws IllegalArgumentException if price is invalid
     */
    public void validatePrice() {
        if (contractPrice == null || contractPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "Contract price must be >= 0"
            );
        }
    }
}
