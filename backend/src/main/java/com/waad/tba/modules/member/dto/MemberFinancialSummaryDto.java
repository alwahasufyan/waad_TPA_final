package com.waad.tba.modules.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Member Financial Summary DTO
 * 
 * Provides comprehensive financial overview for a member including:
 * - Policy information
 * - Utilization metrics
 * - Claim statistics
 * 
 * PHASE 1: Critical endpoint for financial visibility
 * 
 * @version 2026.1
 * @since Phase 1 - Financial Lifecycle Completion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberFinancialSummaryDto {

    // ==================== MEMBER INFO ====================
    
    /**
     * Member ID
     */
    private Long memberId;
    
    /**
     * Full name (Arabic)
     */
    private String fullName;
    
    /**
     * Card number (for display)
     */
    private String cardNumber;
    
    /**
     * Barcode (for Principal members)
     */
    private String barcode;
    
    /**
     * Is this member a dependent?
     */
    private Boolean isDependent;
    
    // ==================== POLICY INFO ====================
    
    /**
     * Benefit policy ID
     */
    private Long policyId;
    
    /**
     * Benefit policy name
     */
    private String policyName;
    
    /**
     * Annual coverage limit from policy
     */
    private BigDecimal annualLimit;
    
    /**
     * Policy start date
     */
    private LocalDate policyStartDate;
    
    /**
     * Policy end date
     */
    private LocalDate policyEndDate;
    
    /**
     * Is policy active?
     */
    private Boolean policyActive;
    
    // ==================== FINANCIAL METRICS ====================
    
    /**
     * Total amount claimed (sum of all requestedAmount)
     */
    private BigDecimal totalClaimed;
    
    /**
     * Total amount approved by insurance (sum of approvedAmount)
     */
    private BigDecimal totalApproved;
    
    /**
     * Total amount paid/settled (sum of claims with status PAID/SETTLED)
     */
    private BigDecimal totalPaid;
    
    /**
     * Remaining coverage (annualLimit - totalApproved)
     */
    private BigDecimal remainingCoverage;
    
    /**
     * Utilization percentage ((totalApproved / annualLimit) * 100)
     */
    private BigDecimal utilizationPercent;
    
    // ==================== CLAIM STATISTICS ====================
    
    /**
     * Total number of claims
     */
    private Integer claimsCount;
    
    /**
     * Number of pending claims
     */
    private Integer pendingClaimsCount;
    
    /**
     * Number of approved claims
     */
    private Integer approvedClaimsCount;
    
    /**
     * Number of rejected claims
     */
    private Integer rejectedClaimsCount;
    
    /**
     * Date of last claim submission
     */
    private LocalDate lastClaimDate;
    
    // ==================== PATIENT RESPONSIBILITY ====================
    
    /**
     * Total patient co-pay across all approved claims
     */
    private BigDecimal totalPatientCoPay;
    
    /**
     * Total deductible applied across all approved claims
     */
    private BigDecimal totalDeductibleApplied;
    
    // ==================== WARNINGS / ALERTS ====================
    
    /**
     * Warning message if coverage is low or expired
     */
    private String warningMessage;
    
    /**
     * Indicates if member is close to annual limit (>80% utilization)
     */
    private Boolean nearingLimit;
    
    /**
     * Indicates if policy is expiring soon (within 30 days)
     */
    private Boolean policyExpiringSoon;
}
