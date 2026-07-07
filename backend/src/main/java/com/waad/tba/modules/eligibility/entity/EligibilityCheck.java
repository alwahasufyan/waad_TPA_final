package com.waad.tba.modules.eligibility.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Eligibility Check Entity
 * Phase E1 - Eligibility Engine
 * 
 * Audit record for every eligibility check performed.
 * Immutable - records are never updated or deleted.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Entity
@Table(name = "eligibility_checks", indexes = {
        @Index(name = "idx_eligibility_request_id", columnList = "request_id", unique = true),
        @Index(name = "idx_eligibility_member_id", columnList = "member_id"),
        @Index(name = "idx_eligibility_policy_id", columnList = "policy_id"),
        @Index(name = "idx_eligibility_service_date", columnList = "service_date"),
        @Index(name = "idx_eligibility_check_timestamp", columnList = "check_timestamp"),
        @Index(name = "idx_eligibility_company_scope", columnList = "company_scope_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ================================================
    // Request Identification
    // ================================================

    /**
     * Unique request ID (UUID)
     */
    @Column(name = "request_id", nullable = false, unique = true, length = 36)
    private String requestId;

    /**
     * Timestamp of the check
     */
    @Column(name = "check_timestamp", nullable = false)
    private LocalDateTime checkTimestamp;

    // ================================================
    // Input Parameters
    // ================================================

    /**
     * Member being checked
     */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /**
     * Policy being checked against
     */
    @Column(name = "policy_id")
    private Long policyId;

    /**
     * Provider (if specified)
     */
    @Column(name = "provider_id")
    private Long providerId;

    /**
     * Service date being checked
     */
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    /**
     * Service code (if specified)
     */
    @Column(name = "service_code", length = 50)
    private String serviceCode;
    
    // ================================================
    // UNIFIED WORKFLOW
    // ================================================
    
    /**
     * Related visit (unified workflow)
     * Links eligibility check to a visit (optional - for unified workflow)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id")
    private com.waad.tba.modules.visit.entity.Visit visit;

    // ================================================
    // Result
    // ================================================

    /**
     * Eligibility determination
     */
    @Column(name = "eligible", nullable = false)
    private Boolean eligible;

    /**
     * Detailed status
     */
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    /**
     * Reasons as JSON array
     */
    @Column(name = "reasons", columnDefinition = "TEXT")
    private String reasons;

    // ================================================
    // Snapshot (at time of check)
    // ================================================

    @Column(name = "member_name", length = 255)
    private String memberName;

    @Column(name = "member_civil_id", length = 50)
    private String memberCivilId;

    @Column(name = "member_status", length = 30)
    private String memberStatus;

    @Column(name = "policy_number", length = 100)
    private String policyNumber;

    @Column(name = "policy_status", length = 30)
    private String policyStatus;

    @Column(name = "policy_start_date")
    private LocalDate policyStartDate;

    @Column(name = "policy_end_date")
    private LocalDate policyEndDate;

    @Column(name = "employer_id")
    private Long employerId;

    @Column(name = "employer_name", length = 255)
    private String employerName;

    // ================================================
    // Security Context
    // ================================================

    /**
     * User who performed the check
     */
    @Column(name = "checked_by_user_id")
    private Long checkedByUserId;

    @Column(name = "checked_by_username", length = 100)
    private String checkedByUsername;

    /**
     * Company scope
     */
    @Column(name = "company_scope_id")
    private Long companyScopeId;

    /**
     * Client IP address
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // ================================================
    // Metrics
    // ================================================

    /**
     * Processing time in milliseconds
     */
    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    /**
     * Number of rules evaluated
     */
    @Column(name = "rules_evaluated")
    private Integer rulesEvaluated;

    // ================================================
    // Lifecycle (auto-set)
    // ================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (checkTimestamp == null) {
            checkTimestamp = createdAt;
        }
    }
}
