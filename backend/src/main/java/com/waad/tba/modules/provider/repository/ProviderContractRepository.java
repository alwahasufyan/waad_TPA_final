package com.waad.tba.modules.provider.repository;

import com.waad.tba.modules.provider.entity.ProviderContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ProviderContract Entity (Legacy module - integrated with Provider)
 * 
 * Provides queries for:
 * - Finding active contracts
 * - Date-based queries (effective contracts)
 * - Provider-specific contracts
 * - Service-specific contracts
 * - Expired contracts (maintenance)
 * - Price lookups
 */
@Repository("legacyProviderContractRepository")
public interface ProviderContractRepository extends JpaRepository<ProviderContract, Long> {

    // ==================== BASIC QUERIES ====================

    /**
     * Find all active contracts for a provider
     * Ordered by service code
     */
    List<ProviderContract> findByProviderIdAndActiveOrderByServiceCode(
        Long providerId, 
        boolean active
    );

    /**
     * Find all active contracts for a provider (paginated)
     */
    Page<ProviderContract> findByProviderIdAndActive(
        Long providerId, 
        boolean active, 
        Pageable pageable
    );

    /**
     * Find active contract for provider and service (latest effective_from)
     * Returns the most recent contract
     */
    Optional<ProviderContract> findFirstByProviderIdAndServiceCodeAndActiveOrderByEffectiveFromDesc(
        Long providerId,
        String serviceCode,
        boolean active
    );

    /**
     * Find all active contracts for a service code
     * Useful for finding all providers offering this service
     */
    List<ProviderContract> findByServiceCodeAndActiveOrderByContractPrice(
        String serviceCode,
        boolean active
    );

    // ==================== DATE-BASED QUERIES ====================

    /**
     * Find effective contract on a specific date
     * 
     * Business rules:
     * - active = true
     * - date >= effective_from
     * - date <= effective_to OR effective_to IS NULL
     * 
     * Returns the most recent contract if multiple match
     */
    @Query("""
        SELECT pc FROM LegacyProviderContract pc
        WHERE pc.providerId = :providerId
        AND pc.serviceCode = :serviceCode
        AND pc.active = true
        AND :date >= pc.effectiveFrom
        AND (:date <= pc.effectiveTo OR pc.effectiveTo IS NULL)
        ORDER BY pc.effectiveFrom DESC
        """)
    Optional<ProviderContract> findEffectiveContract(
        @Param("providerId") Long providerId,
        @Param("serviceCode") String serviceCode,
        @Param("date") LocalDate date
    );

    /**
     * Find effective contract on today's date
     */
    @Query("""
        SELECT pc FROM LegacyProviderContract pc
        WHERE pc.providerId = :providerId
        AND pc.serviceCode = :serviceCode
        AND pc.active = true
        AND CURRENT_DATE >= pc.effectiveFrom
        AND (CURRENT_DATE <= pc.effectiveTo OR pc.effectiveTo IS NULL)
        ORDER BY pc.effectiveFrom DESC
        """)
    Optional<ProviderContract> findCurrentEffectiveContract(
        @Param("providerId") Long providerId,
        @Param("serviceCode") String serviceCode
    );

    /**
     * Find all currently effective contracts for a provider
     */
    @Query("""
        SELECT pc FROM LegacyProviderContract pc
        WHERE pc.providerId = :providerId
        AND pc.active = true
        AND CURRENT_DATE >= pc.effectiveFrom
        AND (CURRENT_DATE <= pc.effectiveTo OR pc.effectiveTo IS NULL)
        ORDER BY pc.serviceCode
        """)
    List<ProviderContract> findCurrentlyEffectiveContracts(@Param("providerId") Long providerId);

    /**
     * Find all expired contracts
     * For maintenance/cleanup
     */
    @Query("""
        SELECT pc FROM LegacyProviderContract pc
        WHERE pc.active = true
        AND pc.effectiveTo IS NOT NULL
        AND pc.effectiveTo < CURRENT_DATE
        ORDER BY pc.effectiveTo DESC
        """)
    List<ProviderContract> findExpiredContracts();

    /**
     * Find contracts expiring within N days
     * For renewal notifications
     */
    @Query("""
        SELECT pc FROM LegacyProviderContract pc
        WHERE pc.active = true
        AND pc.effectiveTo IS NOT NULL
        AND pc.effectiveTo BETWEEN CURRENT_DATE AND :expiryDate
        ORDER BY pc.effectiveTo
        """)
    List<ProviderContract> findContractsExpiringBefore(@Param("expiryDate") LocalDate expiryDate);

    // ==================== VALIDATION QUERIES ====================

    /**
     * Check if contract exists for provider and service
     * Used for duplicate prevention
     */
    boolean existsByProviderIdAndServiceCodeAndActive(
        Long providerId,
        String serviceCode,
        boolean active
    );

    /**
     * Check for overlapping contracts
     * 
     * Finds active contracts where date ranges overlap
     * Used to prevent duplicate pricing periods
     */
    @Query("""
        SELECT CASE WHEN COUNT(pc) > 0 THEN true ELSE false END
        FROM LegacyProviderContract pc
        WHERE pc.providerId = :providerId
        AND pc.serviceCode = :serviceCode
        AND pc.active = true
        AND pc.id != :excludeId
        AND (
            (:effectiveFrom BETWEEN pc.effectiveFrom AND COALESCE(pc.effectiveTo, :effectiveFrom))
            OR
            (:effectiveTo IS NOT NULL AND :effectiveTo BETWEEN pc.effectiveFrom AND COALESCE(pc.effectiveTo, :effectiveTo))
            OR
            (pc.effectiveFrom BETWEEN :effectiveFrom AND COALESCE(:effectiveTo, pc.effectiveFrom))
        )
        """)
    boolean hasOverlappingContract(
        @Param("providerId") Long providerId,
        @Param("serviceCode") String serviceCode,
        @Param("effectiveFrom") LocalDate effectiveFrom,
        @Param("effectiveTo") LocalDate effectiveTo,
        @Param("excludeId") Long excludeId
    );

    // ==================== COUNT/STATISTICS QUERIES ====================

    /**
     * Count active contracts for a provider
     */
    long countByProviderIdAndActive(Long providerId, boolean active);

    /**
     * Count currently effective contracts for a provider
     */
    @Query("""
        SELECT COUNT(pc) FROM LegacyProviderContract pc
        WHERE pc.providerId = :providerId
        AND pc.active = true
        AND CURRENT_DATE >= pc.effectiveFrom
        AND (CURRENT_DATE <= pc.effectiveTo OR pc.effectiveTo IS NULL)
        """)
    long countCurrentlyEffectiveContracts(@Param("providerId") Long providerId);

    /**
     * Count providers offering a service
     */
    @Query("""
        SELECT COUNT(DISTINCT pc.providerId) FROM LegacyProviderContract pc
        WHERE pc.serviceCode = :serviceCode
        AND pc.active = true
        AND CURRENT_DATE >= pc.effectiveFrom
        AND (CURRENT_DATE <= pc.effectiveTo OR pc.effectiveTo IS NULL)
        """)
    long countProvidersOfferingService(@Param("serviceCode") String serviceCode);

    // ==================== BULK QUERIES ====================

    /**
     * Find all service codes for a provider
     * Lightweight query - returns only codes
     */
    @Query("""
        SELECT DISTINCT pc.serviceCode FROM LegacyProviderContract pc
        WHERE pc.providerId = :providerId
        AND pc.active = true
        AND CURRENT_DATE >= pc.effectiveFrom
        AND (CURRENT_DATE <= pc.effectiveTo OR pc.effectiveTo IS NULL)
        ORDER BY pc.serviceCode
        """)
    List<String> findServiceCodesByProviderId(@Param("providerId") Long providerId);

    /**
     * Find all provider IDs offering a service
     * Lightweight query - returns only IDs
     */
    @Query("""
        SELECT DISTINCT pc.providerId FROM LegacyProviderContract pc
        WHERE pc.serviceCode = :serviceCode
        AND pc.active = true
        AND CURRENT_DATE >= pc.effectiveFrom
        AND (CURRENT_DATE <= pc.effectiveTo OR pc.effectiveTo IS NULL)
        ORDER BY pc.providerId
        """)
    List<Long> findProviderIdsByServiceCode(@Param("serviceCode") String serviceCode);

    /**
     * Find contracts by provider ID and service codes
     * Bulk lookup
     */
    @Query("""
        SELECT pc FROM LegacyProviderContract pc
        WHERE pc.providerId = :providerId
        AND pc.serviceCode IN :serviceCodes
        AND pc.active = true
        AND CURRENT_DATE >= pc.effectiveFrom
        AND (CURRENT_DATE <= pc.effectiveTo OR pc.effectiveTo IS NULL)
        ORDER BY pc.serviceCode
        """)
    List<ProviderContract> findByProviderIdAndServiceCodes(
        @Param("providerId") Long providerId,
        @Param("serviceCodes") List<String> serviceCodes
    );

    // ==================== CLEANUP QUERIES ====================

    /**
     * Find all contracts by provider ID (including inactive)
     * For admin/audit purposes
     */
    List<ProviderContract> findByProviderIdOrderByEffectiveFromDesc(Long providerId);

    /**
     * Find contract by ID and provider ID
     * Security check - ensure contract belongs to provider
     */
    Optional<ProviderContract> findByIdAndProviderId(Long id, Long providerId);
}
