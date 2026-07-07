package com.waad.tba.modules.preauthorization.repository;

import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAudit;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAudit.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for PreAuthorization Audit Trail
 */
@Repository
public interface PreAuthorizationAuditRepository extends JpaRepository<PreAuthorizationAudit, Long> {

    /**
     * Find all audit records for a specific PreAuthorization
     * Ordered by change date descending (most recent first)
     */
    Page<PreAuthorizationAudit> findByPreAuthorizationIdOrderByChangeDateDesc(
            Long preAuthorizationId, 
            Pageable pageable
    );

    /**
     * Find all audit records for a specific PreAuthorization (non-paginated)
     */
    List<PreAuthorizationAudit> findByPreAuthorizationIdOrderByChangeDateDesc(Long preAuthorizationId);

    /**
     * Find audit records by user
     */
    Page<PreAuthorizationAudit> findByChangedByOrderByChangeDateDesc(
            String changedBy, 
            Pageable pageable
    );

    /**
     * Find audit records by action type
     */
    Page<PreAuthorizationAudit> findByActionOrderByChangeDateDesc(
            AuditAction action, 
            Pageable pageable
    );

    /**
     * Find audit records within date range
     */
    @Query("SELECT a FROM PreAuthorizationAudit a " +
           "WHERE a.changeDate BETWEEN :startDate AND :endDate " +
           "ORDER BY a.changeDate DESC")
    Page<PreAuthorizationAudit> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Find recent audit records (last N days)
     */
    @Query("SELECT a FROM PreAuthorizationAudit a " +
           "WHERE a.changeDate >= :sinceDate " +
           "ORDER BY a.changeDate DESC")
    Page<PreAuthorizationAudit> findRecentAudits(
            @Param("sinceDate") LocalDateTime sinceDate,
            Pageable pageable
    );

    /**
     * Count audit records by action type
     */
    long countByAction(AuditAction action);

    /**
     * Count audit records by user
     */
    long countByChangedBy(String changedBy);

    /**
     * Search audit records
     */
    @Query("SELECT a FROM PreAuthorizationAudit a " +
           "WHERE a.referenceNumber LIKE %:query% " +
           "OR a.changedBy LIKE %:query% " +
           "OR a.notes LIKE %:query% " +
           "ORDER BY a.changeDate DESC")
    Page<PreAuthorizationAudit> search(
            @Param("query") String query,
            Pageable pageable
    );
}
