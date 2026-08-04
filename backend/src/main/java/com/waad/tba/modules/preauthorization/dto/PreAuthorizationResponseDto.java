package com.waad.tba.modules.preauthorization.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for PreAuthorization response (CANONICAL REBUILD 2026-01-16)
 * 
 * All fields are derived from system data:
 * - Service info from MedicalService entity
 * - Pricing from Provider Contract
 * - Member/Visit info from linked entities
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationResponseDto {

    private Long id;
    private String referenceNumber;
    
    // ==================== VISIT INFO (from linked Visit) ====================
    private Long visitId;
    private LocalDate visitDate;
    private String visitType;
    
    // ==================== MEMBER DETAILS ====================
    private Long memberId;
    private String memberName;
    private String memberCardNumber;
    private String memberNationalNumber;
    
    // ==================== EMPLOYER DETAILS (جهة العمل) ====================
    private Long employerId;
    private String employerName;
    private String employerCode;
    
    // ==================== PROVIDER DETAILS ====================
    private Long providerId;
    private String providerName;
    private String providerLicense;
    
    // ==================== MEDICAL SERVICE (from Contract) ====================
    private Long medicalServiceId;
    // WAAD-PREAUTH-CLAIM-SERVICE-LINK-1: the ProviderContractPricingItem this
    // pre-auth's service was resolved from — lets a claim converted from this
    // pre-auth re-select the exact same catalog service/price row instead of
    // fuzzy-matching serviceCode text against whatever's currently loaded.
    private Long pricingItemId;
    private String serviceCode;
    private String serviceName;
    private Long serviceCategoryId;
    private String serviceCategoryName;
    private Boolean requiresPA;

    /**
     * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): the full list of service lines
     * for this pre-authorization (always at least one). The flat fields
     * above remain populated from line 0 for backward compatibility.
     */
    private List<PreAuthorizationLineResponseDto> lines;

    // ==================== DIAGNOSIS (System-Selected) ====================
    private String diagnosisCode;
    private String diagnosisDescription;
    
    // ==================== DATES ====================
    private LocalDate requestDate;
    private LocalDate expiryDate;
    private Integer daysUntilExpiry;
    
    // ==================== PRICING (Contract-Driven, READ-ONLY) ====================
    private BigDecimal contractPrice;      // From Provider Contract - NOT editable
    private BigDecimal approvedAmount;     // Set during approval
    private BigDecimal copayAmount;        // Calculated from policy
    private BigDecimal copayPercentage;    // From member's policy
    private BigDecimal insuranceCoveredAmount; // approvedAmount - copayAmount
    private String currency;
    
    // ==================== STATUS ====================
    private String status;
    private String priority;
    
    // ==================== ADDITIONAL INFO ====================
    private String notes;
    private String rejectionReason;
    
    // ==================== FLAGS ====================
    private Boolean hasContract;
    private Boolean isValid;
    private Boolean isExpired;
    private Boolean canBeApproved;
    private Boolean canBeRejected;
    private Boolean canBeCancelled;
    
    // ==================== AUDIT ====================
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private Boolean active;
}
