package com.waad.tba.modules.claim.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.claim.entity.ClaimAuditLog;
import com.waad.tba.modules.claim.entity.ClaimAuditLog.ChangeType;

/**
 * Repository for ClaimAuditLog entity.
 * 
 * NOTE: This repository should only support INSERT operations.
 * UPDATE and DELETE should not be used to maintain audit immutability.
 * 
 * @since Phase 7 - Operational Completeness
 */
@Repository
public interface ClaimAuditLogRepository extends JpaRepository<ClaimAuditLog, Long> {

    /**
     * Find all audit entries for a specific claim.
     * Ordered by timestamp descending (newest first).
     */
    @Query("SELECT a FROM ClaimAuditLog a WHERE a.claimId = :claimId ORDER BY a.timestamp DESC")
    List<ClaimAuditLog> findByClaimId(@Param("claimId") Long claimId);

    /**
     * Find audit entries for a claim with pagination.
     */
    @Query("SELECT a FROM ClaimAuditLog a WHERE a.claimId = :claimId ORDER BY a.timestamp DESC")
    Page<ClaimAuditLog> findByClaimIdPaged(@Param("claimId") Long claimId, Pageable pageable);

    /**
     * Find audit entries by actor (user who made the change).
     */
    @Query("SELECT a FROM ClaimAuditLog a WHERE a.actorUserId = :userId ORDER BY a.timestamp DESC")
    List<ClaimAuditLog> findByActorUserId(@Param("userId") Long userId);

    /**
     * Find audit entries by change type.
     */
    @Query("SELECT a FROM ClaimAuditLog a WHERE a.changeType = :changeType ORDER BY a.timestamp DESC")
    List<ClaimAuditLog> findByChangeType(@Param("changeType") ChangeType changeType);

    /**
     * Find audit entries within a date range.
     */
    @Query("SELECT a FROM ClaimAuditLog a " +
           "WHERE a.timestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY a.timestamp DESC")
    List<ClaimAuditLog> findByTimestampBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find audit entries for a claim within a date range.
     */
    @Query("SELECT a FROM ClaimAuditLog a " +
           "WHERE a.claimId = :claimId " +
           "AND a.timestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY a.timestamp DESC")
    List<ClaimAuditLog> findByClaimIdAndTimestampBetween(
        @Param("claimId") Long claimId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find the latest audit entry for a claim.
     */
    @Query("SELECT a FROM ClaimAuditLog a WHERE a.claimId = :claimId ORDER BY a.timestamp DESC LIMIT 1")
    ClaimAuditLog findLatestByClaimId(@Param("claimId") Long claimId);

    /**
     * Count audit entries for a claim.
     */
    @Query("SELECT COUNT(a) FROM ClaimAuditLog a WHERE a.claimId = :claimId")
    long countByClaimId(@Param("claimId") Long claimId);

    /**
     * Find status change audit entries for a claim.
     */
    @Query("SELECT a FROM ClaimAuditLog a " +
           "WHERE a.claimId = :claimId " +
           "AND a.changeType = 'STATUS_CHANGE' " +
           "ORDER BY a.timestamp ASC")
    List<ClaimAuditLog> findStatusChangesForClaim(@Param("claimId") Long claimId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM ClaimAuditLog a WHERE a.claimId = :claimId")
    void deleteByClaimId(@Param("claimId") Long claimId);
}
