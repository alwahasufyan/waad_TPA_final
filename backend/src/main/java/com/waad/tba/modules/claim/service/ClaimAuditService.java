package com.waad.tba.modules.claim.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimAuditLog;
import com.waad.tba.modules.claim.entity.ClaimAuditLog.ChangeType;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimAuditLogRepository;
import com.waad.tba.modules.rbac.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Claim Audit Service - Records immutable audit trail for claims.
 * 
 * All audit entries are immutable once created.
 * This service handles the creation and querying of audit records.
 * 
 * USAGE:
 * - Call recordStatusChange() when claim status changes
 * - Call recordApproval() when claim is approved
 * - Call recordRejection() when claim is rejected
 * - Query methods for audit history retrieval
 * 
 * @since Phase 7 - Operational Completeness
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimAuditService {

    private final ClaimAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Record a status change for a claim.
     * 
     * @param claim The claim that changed
     * @param previousStatus The status before the change
     * @param actor The user who made the change
     * @param comment Optional comment explaining the change
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordStatusChange(Claim claim, ClaimStatus previousStatus, User actor, String comment) {
        ClaimAuditLog auditLog = ClaimAuditLog.builder()
            .claimId(claim.getId())
            .changeType(mapStatusToChangeType(claim.getStatus()))
            .previousStatus(previousStatus)
            .newStatus(claim.getStatus())
            .actorUserId(actor.getId())
            .actorUsername(actor.getUsername())
            .actorRole(getPrimaryRole(actor))
            .comment(comment != null ? comment : claim.getReviewerComment())
            .beforeSnapshot(createSnapshot(claim, previousStatus))
            .afterSnapshot(createSnapshot(claim, claim.getStatus()))
            .build();

        auditLogRepository.save(auditLog);
        
        log.info("📝 Audit recorded: Claim {} transitioned {} → {} by {}", 
            claim.getId(), previousStatus, claim.getStatus(), actor.getUsername());
    }

    /**
     * Record claim creation.
     * 
     * ARCHITECTURAL FIX (2026-01-15):
     * Changed from REQUIRES_NEW to REQUIRED to ensure audit log is created
     * within the same transaction as the claim, avoiding FK constraint violation.
     * 
     * @param claim The newly created claim
     * @param actor The user who created the claim
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordCreation(Claim claim, User actor) {
        ClaimAuditLog auditLog = ClaimAuditLog.creation(
            claim.getId(),
            claim.getRequestedAmount(),
            actor.getId(),
            actor.getUsername(),
            getPrimaryRole(actor)
        );
        
        // Add after snapshot
        ClaimAuditLog withSnapshot = ClaimAuditLog.builder()
            .claimId(auditLog.getClaimId())
            .changeType(auditLog.getChangeType())
            .newStatus(auditLog.getNewStatus())
            .newRequestedAmount(auditLog.getNewRequestedAmount())
            .actorUserId(auditLog.getActorUserId())
            .actorUsername(auditLog.getActorUsername())
            .actorRole(auditLog.getActorRole())
            .comment(auditLog.getComment())
            .afterSnapshot(createSnapshot(claim, claim.getStatus()))
            .build();

        auditLogRepository.save(withSnapshot);
        
        log.info("📝 Audit recorded: Claim {} created by {}", claim.getId(), actor.getUsername());
    }

    /**
     * Record claim approval with amount.
     * 
     * @param claim The approved claim
     * @param previousStatus Status before approval
     * @param previousApproved Previous approved amount (usually null)
     * @param actor The reviewer who approved
     * @param comment Approval comment
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordApproval(Claim claim, ClaimStatus previousStatus, 
                               java.math.BigDecimal previousApproved, User actor, String comment) {
        ClaimAuditLog auditLog = ClaimAuditLog.approval(
            claim.getId(),
            previousStatus,
            previousApproved,
            claim.getApprovedAmount(),
            actor.getId(),
            actor.getUsername(),
            getPrimaryRole(actor),
            comment
        );

        auditLogRepository.save(auditLog);
        
        log.info("📝 Audit recorded: Claim {} approved for {} by {}", 
            claim.getId(), claim.getApprovedAmount(), actor.getUsername());
    }

    /**
     * Record claim rejection.
     * 
     * @param claim The rejected claim
     * @param previousStatus Status before rejection
     * @param actor The reviewer who rejected
     * @param reason Rejection reason (required)
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordRejection(Claim claim, ClaimStatus previousStatus, User actor, String reason) {
        ClaimAuditLog auditLog = ClaimAuditLog.rejection(
            claim.getId(),
            previousStatus,
            actor.getId(),
            actor.getUsername(),
            getPrimaryRole(actor),
            reason
        );

        auditLogRepository.save(auditLog);
        
        log.info("📝 Audit recorded: Claim {} rejected by {} - {}", 
            claim.getId(), actor.getUsername(), reason);
    }

    /**
     * Record claim settlement.
     * 
     * @param claim The settled claim
     * @param actor The user who processed settlement
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSettlement(Claim claim, User actor) {
        ClaimAuditLog auditLog = ClaimAuditLog.settlement(
            claim.getId(),
            claim.getApprovedAmount(),
            actor.getId(),
            actor.getUsername(),
            getPrimaryRole(actor)
        );

        auditLogRepository.save(auditLog);
        
        log.info("📝 Audit recorded: Claim {} settled for {} by {}", 
            claim.getId(), claim.getApprovedAmount(), actor.getUsername());
    }

    /**
     * Record a generic change with full details.
     * 
     * @param claim The claim
     * @param changeType Type of change
     * @param actor User making the change
     * @param comment Description of the change
     * @param beforeClaim Claim state before (for snapshot)
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordChange(Claim claim, ChangeType changeType, User actor, 
                            String comment, Claim beforeClaim) {
        ClaimAuditLog auditLog = ClaimAuditLog.builder()
            .claimId(claim.getId())
            .changeType(changeType)
            .previousStatus(beforeClaim != null ? beforeClaim.getStatus() : null)
            .newStatus(claim.getStatus())
            .previousRequestedAmount(beforeClaim != null ? beforeClaim.getRequestedAmount() : null)
            .newRequestedAmount(claim.getRequestedAmount())
            .previousApprovedAmount(beforeClaim != null ? beforeClaim.getApprovedAmount() : null)
            .newApprovedAmount(claim.getApprovedAmount())
            .actorUserId(actor.getId())
            .actorUsername(actor.getUsername())
            .actorRole(getPrimaryRole(actor))
            .comment(comment)
            .beforeSnapshot(beforeClaim != null ? createSnapshot(beforeClaim, beforeClaim.getStatus()) : null)
            .afterSnapshot(createSnapshot(claim, claim.getStatus()))
            .build();

        auditLogRepository.save(auditLog);
        
        log.info("📝 Audit recorded: Claim {} - {} by {}", 
            claim.getId(), changeType, actor.getUsername());
    }

    // ==================== Query Methods ====================

    /**
     * Get complete audit history for a claim.
     */
    @Transactional(readOnly = true)
    public List<ClaimAuditLog> getAuditHistory(Long claimId) {
        return auditLogRepository.findByClaimId(claimId);
    }

    /**
     * Get paginated audit history for a claim.
     */
    @Transactional(readOnly = true)
    public Page<ClaimAuditLog> getAuditHistoryPaged(Long claimId, Pageable pageable) {
        return auditLogRepository.findByClaimIdPaged(claimId, pageable);
    }

    /**
     * Get status change timeline for a claim.
     */
    @Transactional(readOnly = true)
    public List<ClaimAuditLog> getStatusTimeline(Long claimId) {
        return auditLogRepository.findStatusChangesForClaim(claimId);
    }

    /**
     * Get audit entries by user.
     */
    @Transactional(readOnly = true)
    public List<ClaimAuditLog> getAuditsByUser(Long userId) {
        return auditLogRepository.findByActorUserId(userId);
    }

    /**
     * Get audit entries within date range.
     */
    @Transactional(readOnly = true)
    public List<ClaimAuditLog> getAuditsByDateRange(LocalDateTime start, LocalDateTime end) {
        return auditLogRepository.findByTimestampBetween(start, end);
    }

    /**
     * Get the latest audit entry for a claim.
     */
    @Transactional(readOnly = true)
    public ClaimAuditLog getLatestAudit(Long claimId) {
        return auditLogRepository.findLatestByClaimId(claimId);
    }

    /**
     * Delete all audit entries for a claim.
     * WARNING: Use only when deleting a claim entirely to maintain data integrity.
     */
    @Transactional
    public void deleteAuditLog(Long claimId) {
        log.warn("🗑️ Deleting audit logs for claim {}", claimId);
        auditLogRepository.deleteByClaimId(claimId);
    }

    // ==================== Helper Methods ====================

    /**
     * Map a claim status to the appropriate change type.
     */
    private ChangeType mapStatusToChangeType(ClaimStatus status) {
        return switch (status) {
            case DRAFT -> ChangeType.CREATED;
            case SUBMITTED -> ChangeType.SUBMITTED;
            case UNDER_REVIEW -> ChangeType.STATUS_CHANGE;
            case NEEDS_CORRECTION -> ChangeType.NEEDS_CORRECTION;
            case APPROVED -> ChangeType.APPROVAL;
            case REJECTED -> ChangeType.REJECTION;
            case SETTLED -> ChangeType.SETTLEMENT;
            default -> ChangeType.STATUS_CHANGE;
        };
    }

    /**
     * Get primary role name from user.
     */
    private String getPrimaryRole(User user) {
        if (user.getUserType() == null || user.getUserType().isBlank()) {
            return "UNKNOWN";
        }
        return user.getUserType();
    }

    /**
     * Create a JSON snapshot of the claim state.
     * CANONICAL (2026-01-16): Uses diagnosisCode/Description instead of diagnosis
     */
    private String createSnapshot(Claim claim, ClaimStatus status) {
        try {
            // Create a simplified DTO for snapshot to avoid circular references
            var snapshot = new ClaimSnapshot(
                claim.getId(),
                status,
                claim.getRequestedAmount(),
                claim.getApprovedAmount(),
                claim.getDifferenceAmount(),
                claim.getProviderName(),
                claim.getDiagnosisCode(),
                claim.getDiagnosisDescription(),
                claim.getReviewerComment(),
                claim.getAttachmentsCount(),
                claim.getServiceCount()
            );
            
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.warn("Failed to create claim snapshot: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Simplified claim snapshot for audit storage.
     * CANONICAL (2026-01-16): diagnosisCode + diagnosisDescription
     */
    private record ClaimSnapshot(
        Long id,
        ClaimStatus status,
        java.math.BigDecimal requestedAmount,
        java.math.BigDecimal approvedAmount,
        java.math.BigDecimal differenceAmount,
        String providerName,
        String diagnosisCode,
        String diagnosisDescription,
        String reviewerComment,
        Integer attachmentsCount,
        Integer serviceCount
    ) {}
}
