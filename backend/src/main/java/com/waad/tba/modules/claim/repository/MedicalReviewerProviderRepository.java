package com.waad.tba.modules.claim.repository;

import com.waad.tba.modules.claim.entity.MedicalReviewerProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Medical Reviewer Provider Mapping.
 * 
 * Provides queries for reviewer-provider isolation enforcement.
 * 
 * @since Medical Reviewer Isolation Phase (2026-02-12)
 */
@Repository
public interface MedicalReviewerProviderRepository extends JpaRepository<MedicalReviewerProvider, Long> {

       List<MedicalReviewerProvider> findByReviewerId(Long reviewerId);

       boolean existsByReviewerId(Long reviewerId);

    /**
     * Find all active provider IDs assigned to a reviewer.
     * 
     * This is the CRITICAL query for isolation enforcement.
     * Used to filter claims list to only show claims from assigned providers.
     * 
     * @param reviewerId User ID of the medical reviewer
     * @return List of provider IDs this reviewer is assigned to
     */
    @Query("SELECT mrp.provider.id FROM MedicalReviewerProvider mrp " +
           "WHERE mrp.reviewer.id = :reviewerId AND mrp.active = true")
    List<Long> findProviderIdsByReviewerId(@Param("reviewerId") Long reviewerId);

    /**
     * Find all active reviewer IDs assigned to a provider.
     * 
     * Used for reverse lookup and admin queries.
     * 
     * @param providerId Provider ID
     * @return List of reviewer IDs assigned to this provider
     */
    @Query("SELECT mrp.reviewer.id FROM MedicalReviewerProvider mrp " +
           "WHERE mrp.provider.id = :providerId AND mrp.active = true")
    List<Long> findReviewerIdsByProviderId(@Param("providerId") Long providerId);

    /**
     * Find all active mappings for a reviewer (with eager loading).
     * 
     * @param reviewerId User ID of the medical reviewer
     * @return List of active assignments with provider details
     */
    @Query("SELECT mrp FROM MedicalReviewerProvider mrp " +
           "LEFT JOIN FETCH mrp.provider " +
           "WHERE mrp.reviewer.id = :reviewerId AND mrp.active = true")
    List<MedicalReviewerProvider> findByReviewerIdAndActiveTrue(@Param("reviewerId") Long reviewerId);

    /**
     * Find all active mappings for a provider (with eager loading).
     * 
     * @param providerId Provider ID
     * @return List of active assignments with reviewer details
     */
    @Query("SELECT mrp FROM MedicalReviewerProvider mrp " +
           "LEFT JOIN FETCH mrp.reviewer " +
           "WHERE mrp.provider.id = :providerId AND mrp.active = true")
    List<MedicalReviewerProvider> findByProviderIdAndActiveTrue(@Param("providerId") Long providerId);

    /**
     * Find a specific reviewer-provider mapping.
     * 
     * @param reviewerId User ID of the medical reviewer
     * @param providerId Provider ID
     * @return Optional mapping (active or inactive)
     */
    @Query("SELECT mrp FROM MedicalReviewerProvider mrp " +
           "WHERE mrp.reviewer.id = :reviewerId AND mrp.provider.id = :providerId")
    Optional<MedicalReviewerProvider> findByReviewerIdAndProviderId(
        @Param("reviewerId") Long reviewerId,
        @Param("providerId") Long providerId
    );

    /**
     * Check if a reviewer has access to a specific provider.
     * 
     * This is used for quick validation in service layer.
     * 
     * @param reviewerId User ID of the medical reviewer
     * @param providerId Provider ID
     * @return true if reviewer is assigned to this provider (active)
     */
    @Query("SELECT CASE WHEN COUNT(mrp) > 0 THEN true ELSE false END " +
           "FROM MedicalReviewerProvider mrp " +
           "WHERE mrp.reviewer.id = :reviewerId " +
           "AND mrp.provider.id = :providerId " +
           "AND mrp.active = true")
    boolean existsByReviewerIdAndProviderIdAndActiveTrue(
        @Param("reviewerId") Long reviewerId,
        @Param("providerId") Long providerId
    );

    /**
     * Count active assignments for a reviewer.
     * 
     * @param reviewerId User ID of the medical reviewer
     * @return Number of providers assigned to this reviewer
     */
    @Query("SELECT COUNT(mrp) FROM MedicalReviewerProvider mrp " +
           "WHERE mrp.reviewer.id = :reviewerId AND mrp.active = true")
    long countByReviewerIdAndActiveTrue(@Param("reviewerId") Long reviewerId);

    /**
     * Find all active mappings (for admin queries).
     * 
     * @return List of all active reviewer-provider mappings
     */
    @Query("SELECT mrp FROM MedicalReviewerProvider mrp " +
           "LEFT JOIN FETCH mrp.reviewer " +
           "LEFT JOIN FETCH mrp.provider " +
           "WHERE mrp.active = true " +
           "ORDER BY mrp.createdAt DESC")
    List<MedicalReviewerProvider> findAllActive();
}
