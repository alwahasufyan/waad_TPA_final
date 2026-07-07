package com.waad.tba.modules.claim.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Claim Audit Log - Immutable state change history.
 * 
 * Tracks all state transitions for claims with:
 * - Before and after values
 * - Actor (user + role)
 * - Timestamp
 * - Context information
 * 
 * IMMUTABILITY:
 * - Entity has no setters (fields set only via constructor/builder)
 * - No @PreUpdate hook
 * - Table has no UPDATE triggers
 * 
 * @since Phase 7 - Operational Completeness
 */
@Entity
@Table(name = "claim_audit_logs", indexes = {
    @Index(name = "idx_claim_audit_claim_id", columnList = "claim_id"),
    @Index(name = "idx_claim_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_claim_audit_actor", columnList = "actor_user_id")
})
@Getter // Only getters, no setters - immutable
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED) // JPA requires no-arg constructor
@AllArgsConstructor
@Builder
public class ClaimAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The claim this audit entry belongs to.
     */
    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    /**
     * Type of change (STATUS_CHANGE, AMOUNT_CHANGE, ASSIGNMENT_CHANGE, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 50)
    private ChangeType changeType;

    /**
     * Previous status before the change.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private ClaimStatus previousStatus;

    /**
     * New status after the change.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 30)
    private ClaimStatus newStatus;

    /**
     * Previous requested amount (for amount changes).
     */
    @Column(name = "previous_requested_amount", precision = 15, scale = 2)
    private BigDecimal previousRequestedAmount;

    /**
     * New requested amount (for amount changes).
     */
    @Column(name = "new_requested_amount", precision = 15, scale = 2)
    private BigDecimal newRequestedAmount;

    /**
     * Previous approved amount (for approval changes).
     */
    @Column(name = "previous_approved_amount", precision = 15, scale = 2)
    private BigDecimal previousApprovedAmount;

    /**
     * New approved amount (for approval changes).
     */
    @Column(name = "new_approved_amount", precision = 15, scale = 2)
    private BigDecimal newApprovedAmount;

    /**
     * User ID of the actor who made the change.
     */
    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    /**
     * Username of the actor.
     */
    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;

    /**
     * Role of the actor at the time of the change.
     */
    @Column(name = "actor_role", nullable = false, length = 50)
    private String actorRole;

    /**
     * Timestamp when the change occurred.
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Optional comment explaining the change.
     */
    @Column(columnDefinition = "TEXT")
    private String comment;

    /**
     * IP address of the actor (for security tracking).
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * JSON snapshot of the claim before the change.
     * Allows full reconstruction of previous state.
     */
    @Column(name = "before_snapshot", columnDefinition = "TEXT")
    private String beforeSnapshot;

    /**
     * JSON snapshot of the claim after the change.
     */
    @Column(name = "after_snapshot", columnDefinition = "TEXT")
    private String afterSnapshot;

    /**
     * Set timestamp on persist only - immutable after creation.
     */
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    // ==================== Change Types ====================

    /**
     * Types of changes tracked in the audit log.
     */
    public enum ChangeType {
        /**
         * Claim status changed (e.g., DRAFT → SUBMITTED).
         */
        STATUS_CHANGE("تغيير الحالة"),
        
        /**
         * Requested amount was modified.
         */
        AMOUNT_CHANGE("تغيير المبلغ"),
        
        /**
         * Claim was approved with an approved amount set.
         */
        APPROVAL("موافقة"),
        
        /**
         * Claim was rejected.
         */
        REJECTION("رفض"),
        
        /**
         * Claim was created.
         */
        CREATED("إنشاء"),
        
        /**
         * Claim was submitted for review.
         */
        SUBMITTED("تقديم"),
        
        /**
         * Claim was assigned to a reviewer.
         */
        ASSIGNMENT("تعيين"),
        
        /**
         * Comment was added to the claim.
         */
        COMMENT_ADDED("إضافة تعليق"),
        
        /**
         * Attachment was added or removed.
         */
        ATTACHMENT_CHANGE("تغيير المرفقات"),
        
        /**
         * Pre-approval was linked to the claim.
         */
        PREAPPROVAL_LINKED("ربط موافقة مسبقة"),
        
        /**
         * Settlement/payment was processed.
         */
        SETTLEMENT("تسوية"),
        
        /**
         * Claim needs correction and was sent back to provider.
         */
        NEEDS_CORRECTION("يحتاج تصحيح"),
        
        /**
         * General update to claim fields.
         */
        UPDATED("تحديث");

        private final String arabicLabel;

        ChangeType(String arabicLabel) {
            this.arabicLabel = arabicLabel;
        }

        public String getArabicLabel() {
            return arabicLabel;
        }
    }

    // ==================== Factory Methods ====================

    /**
     * Create an audit log entry for a status change.
     */
    public static ClaimAuditLog statusChange(
            Long claimId,
            ClaimStatus previousStatus,
            ClaimStatus newStatus,
            Long actorUserId,
            String actorUsername,
            String actorRole,
            String comment) {
        
        return ClaimAuditLog.builder()
            .claimId(claimId)
            .changeType(ChangeType.STATUS_CHANGE)
            .previousStatus(previousStatus)
            .newStatus(newStatus)
            .actorUserId(actorUserId)
            .actorUsername(actorUsername)
            .actorRole(actorRole)
            .comment(comment)
            .build();
    }

    /**
     * Create an audit log entry for claim approval.
     */
    public static ClaimAuditLog approval(
            Long claimId,
            ClaimStatus previousStatus,
            BigDecimal previousApproved,
            BigDecimal newApproved,
            Long actorUserId,
            String actorUsername,
            String actorRole,
            String comment) {
        
        return ClaimAuditLog.builder()
            .claimId(claimId)
            .changeType(ChangeType.APPROVAL)
            .previousStatus(previousStatus)
            .newStatus(ClaimStatus.APPROVED)
            .previousApprovedAmount(previousApproved)
            .newApprovedAmount(newApproved)
            .actorUserId(actorUserId)
            .actorUsername(actorUsername)
            .actorRole(actorRole)
            .comment(comment)
            .build();
    }

    /**
     * Create an audit log entry for claim rejection.
     */
    public static ClaimAuditLog rejection(
            Long claimId,
            ClaimStatus previousStatus,
            Long actorUserId,
            String actorUsername,
            String actorRole,
            String reason) {
        
        return ClaimAuditLog.builder()
            .claimId(claimId)
            .changeType(ChangeType.REJECTION)
            .previousStatus(previousStatus)
            .newStatus(ClaimStatus.REJECTED)
            .actorUserId(actorUserId)
            .actorUsername(actorUsername)
            .actorRole(actorRole)
            .comment(reason)
            .build();
    }

    /**
     * Create an audit log entry for claim creation.
     */
    public static ClaimAuditLog creation(
            Long claimId,
            BigDecimal requestedAmount,
            Long actorUserId,
            String actorUsername,
            String actorRole) {
        
        return ClaimAuditLog.builder()
            .claimId(claimId)
            .changeType(ChangeType.CREATED)
            .newStatus(ClaimStatus.DRAFT)
            .newRequestedAmount(requestedAmount)
            .actorUserId(actorUserId)
            .actorUsername(actorUsername)
            .actorRole(actorRole)
            .comment("Claim created")
            .build();
    }

    /**
     * Create an audit log entry for settlement.
     */
    public static ClaimAuditLog settlement(
            Long claimId,
            BigDecimal approvedAmount,
            Long actorUserId,
            String actorUsername,
            String actorRole) {
        
        return ClaimAuditLog.builder()
            .claimId(claimId)
            .changeType(ChangeType.SETTLEMENT)
            .previousStatus(ClaimStatus.APPROVED)
            .newStatus(ClaimStatus.SETTLED)
            .newApprovedAmount(approvedAmount)
            .actorUserId(actorUserId)
            .actorUsername(actorUsername)
            .actorRole(actorRole)
            .comment("Payment settled")
            .build();
    }
}
