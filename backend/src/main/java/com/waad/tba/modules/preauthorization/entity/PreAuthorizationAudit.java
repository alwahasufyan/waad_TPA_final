package com.waad.tba.modules.preauthorization.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * PreAuthorization Audit Trail Entity
 * 
 * Purpose: Track all changes to PreAuthorization records for compliance and troubleshooting
 * 
 * Captured Events:
 * - CREATE: Initial creation
 * - UPDATE: Field modifications
 * - APPROVE: Status change to APPROVED
 * - REJECT: Status change to REJECTED
 * - CANCEL: Status change to CANCELLED
 * - DELETE: Soft delete (active = false)
 */
@Entity
@Table(name = "pre_authorization_audit", indexes = {
        @Index(name = "idx_audit_preauth", columnList = "pre_authorization_id"),
        @Index(name = "idx_audit_user", columnList = "changed_by"),
        @Index(name = "idx_audit_date", columnList = "change_date"),
        @Index(name = "idx_audit_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the PreAuthorization being tracked
     */
    @Column(name = "pre_authorization_id", nullable = false)
    private Long preAuthorizationId;

    /**
     * Reference number of the PreAuthorization (for easy lookup)
     */
    @Column(name = "reference_number", length = 50)
    private String referenceNumber;

    /**
     * User who made the change
     */
    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    /**
     * Timestamp of the change
     */
    @CreationTimestamp
    @Column(name = "change_date", nullable = false)
    private LocalDateTime changeDate;

    /**
     * Type of action performed
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    /**
     * Field that was changed (null for CREATE, DELETE)
     */
    @Column(name = "field_name", length = 50)
    private String fieldName;

    /**
     * Previous value (null for CREATE)
     */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /**
     * New value (null for DELETE)
     */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /**
     * Additional notes or comments about the change
     */
    @Column(length = 500)
    private String notes;

    /**
     * IP address of the user (optional, for security)
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Audit Action Types
     */
    public enum AuditAction {
        CREATE,          // Initial creation
        UPDATE,          // Field modification
        APPROVE,         // Approval workflow
        REJECT,          // Rejection workflow
        CANCEL,          // Cancellation
        DELETE,          // Soft delete
        STATUS_CHANGE    // Generic status change
    }

    /**
     * Factory method for CREATE action
     */
    public static PreAuthorizationAudit createAudit(
            Long preAuthId, 
            String referenceNumber, 
            String changedBy, 
            String notes
    ) {
        return PreAuthorizationAudit.builder()
                .preAuthorizationId(preAuthId)
                .referenceNumber(referenceNumber)
                .changedBy(changedBy)
                .action(AuditAction.CREATE)
                .notes(notes)
                .build();
    }

    /**
     * Factory method for field UPDATE
     */
    public static PreAuthorizationAudit fieldUpdateAudit(
            Long preAuthId,
            String referenceNumber,
            String changedBy,
            String fieldName,
            String oldValue,
            String newValue
    ) {
        return PreAuthorizationAudit.builder()
                .preAuthorizationId(preAuthId)
                .referenceNumber(referenceNumber)
                .changedBy(changedBy)
                .action(AuditAction.UPDATE)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
    }

    /**
     * Factory method for APPROVE action
     */
    public static PreAuthorizationAudit approveAudit(
            Long preAuthId,
            String referenceNumber,
            String changedBy,
            String notes
    ) {
        return PreAuthorizationAudit.builder()
                .preAuthorizationId(preAuthId)
                .referenceNumber(referenceNumber)
                .changedBy(changedBy)
                .action(AuditAction.APPROVE)
                .notes(notes)
                .build();
    }

    /**
     * Factory method for REJECT action
     */
    public static PreAuthorizationAudit rejectAudit(
            Long preAuthId,
            String referenceNumber,
            String changedBy,
            String notes
    ) {
        return PreAuthorizationAudit.builder()
                .preAuthorizationId(preAuthId)
                .referenceNumber(referenceNumber)
                .changedBy(changedBy)
                .action(AuditAction.REJECT)
                .notes(notes)
                .build();
    }

    /**
     * Factory method for CANCEL action
     */
    public static PreAuthorizationAudit cancelAudit(
            Long preAuthId,
            String referenceNumber,
            String changedBy,
            String notes
    ) {
        return PreAuthorizationAudit.builder()
                .preAuthorizationId(preAuthId)
                .referenceNumber(referenceNumber)
                .changedBy(changedBy)
                .action(AuditAction.CANCEL)
                .notes(notes)
                .build();
    }

    /**
     * Factory method for DELETE action
     */
    public static PreAuthorizationAudit deleteAudit(
            Long preAuthId,
            String referenceNumber,
            String changedBy
    ) {
        return PreAuthorizationAudit.builder()
                .preAuthorizationId(preAuthId)
                .referenceNumber(referenceNumber)
                .changedBy(changedBy)
                .action(AuditAction.DELETE)
                .build();
    }
}
