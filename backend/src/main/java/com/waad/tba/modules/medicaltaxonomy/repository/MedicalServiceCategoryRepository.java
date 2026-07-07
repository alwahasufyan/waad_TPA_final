package com.waad.tba.modules.medicaltaxonomy.repository;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the service ↔ category junction table.
 *
 * <p>Table: {@code medical_service_categories} (backfilled in V90)
 */
@Repository
public interface MedicalServiceCategoryRepository
        extends JpaRepository<MedicalServiceCategory, Long> {

    /** Check whether any linking row exists for a given service */
    boolean existsByServiceId(Long serviceId);

    /** Check whether a specific (service, category) pair already linked */
    boolean existsByServiceIdAndCategoryId(Long serviceId, Long categoryId);

    /** Find primary link for a service */
    Optional<MedicalServiceCategory> findByServiceIdAndIsPrimaryTrue(Long serviceId);

    /**
     * Find a deterministic active category mapping for a service
     * (prefers primary links, then oldest id).
     */
    Optional<MedicalServiceCategory> findFirstByServiceIdAndActiveTrueOrderByIsPrimaryDescIdAsc(Long serviceId);

    /**
     * Find one deterministic active mapping per service for a set of service IDs.
     * Prefers primary mapping, then oldest row.
     */
    @Query(value = """
            SELECT DISTINCT ON (msc.service_id) msc.*
            FROM medical_service_categories msc
            WHERE msc.service_id IN (:serviceIds)
              AND msc.active = true
            ORDER BY msc.service_id, msc.is_primary DESC, msc.id ASC
            """, nativeQuery = true)
    List<MedicalServiceCategory> findPrimaryActiveByServiceIds(@Param("serviceIds") Collection<Long> serviceIds);

    /** Count active services in a category — used for deletion guard */
    @Query("""
            SELECT COUNT(msc) FROM MedicalServiceCategory msc
            WHERE  msc.categoryId = :categoryId
              AND  msc.active      = true
            """)
    long countActiveByCategory(@Param("categoryId") Long categoryId);

    /**
     * Insert a linking row if it does not already exist.
     * Native SQL to avoid sequence allocation overhead.
     */
    @Modifying
    @Query(value = """
            INSERT INTO medical_service_categories
                (service_id, category_id, context, is_primary, active)
            SELECT :serviceId, :categoryId, 'ANY', TRUE, TRUE
            WHERE NOT EXISTS (
                SELECT 1 FROM medical_service_categories
                WHERE service_id = :serviceId
                  AND category_id = :categoryId
            )
            """, nativeQuery = true)
    void insertIfAbsent(@Param("serviceId") Long serviceId,
                        @Param("categoryId") Long categoryId);

    /** Remove all linking rows for a service (used on service soft-delete) */
    void deleteAllByServiceId(Long serviceId);
}
