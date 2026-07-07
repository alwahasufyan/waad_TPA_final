package com.waad.tba.modules.preauthorization.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Pre-Authorization Response
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * DECISION SAFETY GUARANTEE:
 * All decision and financial fields in this response are READ-ONLY and calculated by backend.
 * Frontend CANNOT modify these values.
 * 
 * This response contains:
 * 1. Pre-authorization identification (id, referenceNumber)
 * 2. Related entities (member, provider, visit, service)
 * 3. Decision data (READ-ONLY - approved amount, copay, coverage)
 * 4. Status and workflow state
 * 5. Audit trail
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthorizationResponse {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Long id;
    
    /**
     * Pre-authorization reference number (formatted identifier)
     */
    private String referenceNumber;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RELATED ENTITIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    // Visit information
    private Long visitId;
    private LocalDate visitDate;
    private String visitType;
    
    // Member information
    private Long memberId;
    private String memberName;
    private String memberCardNumber;
    private String memberNationalNumber;
    
    // Employer information
    private Long employerId;
    private String employerName;
    private String employerCode;
    
    // Provider information
    private Long providerId;
    private String providerName;
    private String providerLicense;
    
    // Medical Service (from Contract)
    private Long medicalServiceId;
    private String serviceCode;
    private String serviceName;
    private Long serviceCategoryId;
    private String serviceCategoryName;
    private Boolean requiresPA;
    
    // Diagnosis (System-Selected)
    private String diagnosisCode;
    private String diagnosisDescription;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DATES (READ-ONLY)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Request date
     * READ-ONLY - Set when pre-authorization is created
     */
    private LocalDate requestDate;
    
    /**
     * Expiry date
     * READ-ONLY - Calculated by backend (approval date + policy days)
     */
    private LocalDate expiryDate;
    
    /**
     * Days until expiry
     * READ-ONLY - Calculated by backend
     */
    private Integer daysUntilExpiry;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DECISION DATA (READ-ONLY - BACKEND CALCULATED)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Contract price from Provider Contract
     * READ-ONLY - Auto-resolved from contract pricing
     */
    private BigDecimal contractPrice;
    
    /**
     * Approved amount (set during approval workflow)
     * READ-ONLY - Calculated by backend from:
     * - Provider contract pricing
     * - Service coverage rules
     * - Member eligibility
     */
    private BigDecimal approvedAmount;
    
    /**
     * Co-pay amount (patient's share)
     * READ-ONLY - Calculated by backend (approved × copay%)
     */
    private BigDecimal copayAmount;
    
    /**
     * Co-pay percentage from benefit policy
     * READ-ONLY - From member's benefit policy
     */
    private BigDecimal copayPercentage;
    
    /**
     * Insurance covered amount (insurance's share)
     * READ-ONLY - Calculated by backend (approved - copay)
     */
    private BigDecimal insuranceCoveredAmount;
    
    /**
     * Currency (LYD, USD, etc.)
     * READ-ONLY - From system configuration
     */
    private String currency;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS AND WORKFLOW (READ-ONLY)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Current status
     * READ-ONLY - Managed by workflow state machine
     * Values: PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED
     */
    private String status;
    
    /**
     * Priority level
     * READ-ONLY - Set during creation, can be updated
     */
    private String priority;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ADDITIONAL INFORMATION (READ-ONLY)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Notes from requester
     */
    private String notes;
    
    /**
     * Rejection reason (if rejected)
     * READ-ONLY - Set during rejection workflow
     */
    private String rejectionReason;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BUSINESS FLAGS (READ-ONLY - BACKEND CALCULATED)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Whether provider has active contract
     * READ-ONLY - Checked by backend
     */
    private Boolean hasContract;
    
    /**
     * Whether pre-authorization is valid (approved and not expired)
     * READ-ONLY - Calculated by backend
     */
    private Boolean isValid;
    
    /**
     * Whether pre-authorization has expired
     * READ-ONLY - Calculated by backend (current date > expiry date)
     */
    private Boolean isExpired;
    
    /**
     * Whether pre-authorization can be approved (workflow permission)
     * READ-ONLY - Backend-driven workflow
     */
    private Boolean canBeApproved;
    
    /**
     * Whether pre-authorization can be rejected (workflow permission)
     * READ-ONLY - Backend-driven workflow
     */
    private Boolean canBeRejected;
    
    /**
     * Whether pre-authorization can be cancelled (workflow permission)
     * READ-ONLY - Backend-driven workflow
     */
    private Boolean canBeCancelled;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // AUDIT TRAIL (READ-ONLY)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creation timestamp
     * READ-ONLY - Set by backend on creation
     */
    private LocalDateTime createdAt;
    
    /**
     * Last update timestamp
     * READ-ONLY - Set by backend on updates
     */
    private LocalDateTime updatedAt;
    
    /**
     * Created by user
     * READ-ONLY - From security context
     */
    private String createdBy;
    
    /**
     * Last updated by user
     * READ-ONLY - From security context
     */
    private String updatedBy;
    
    /**
     * Approval timestamp
     * READ-ONLY - Set when pre-authorization is approved
     */
    private LocalDateTime approvedAt;
    
    /**
     * Approved by user
     * READ-ONLY - From security context during approval
     */
    private String approvedBy;
    
    /**
     * Active status (soft delete flag)
     * READ-ONLY - Managed by backend
     */
    private Boolean active;
}
