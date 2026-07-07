package com.waad.tba.modules.visit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.waad.tba.modules.visit.entity.VisitType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitResponseDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNumber;
    private Long providerId;
    private String providerName;
    private LocalDate visitDate;
    private String doctorName;
    private String specialty;
    private String diagnosis;
    private String treatment;
    private BigDecimal totalAmount;
    private String notes;
    private Boolean active;
    
    // ==================== EMPLOYER/ORGANIZATION INFO (معلومات الشريك) ====================
    
    /**
     * Employer organization ID
     */
    private Long employerId;
    
    /**
     * Employer organization name (for UI display)
     */
    private String employerName;
    
    /**
     * Type of visit/service location
     */
    private VisitType visitType;
    
    /**
     * Arabic label for visitType (for UI display)
     */
    private String visitTypeLabel;
    
    // ==================== CLAIM INFO (حالة المطالبة) ====================
    
    /**
     * Number of claims associated with this visit
     */
    private Integer claimCount;
    
    /**
     * Latest claim ID (if any)
     */
    private Long latestClaimId;
    
    /**
     * Latest claim status (DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, etc.)
     */
    private String latestClaimStatus;
    
    /**
     * Latest claim status label in Arabic
     */
    private String latestClaimStatusLabel;
    
    // ==================== PRE-AUTHORIZATION INFO (حالة الموافقة المسبقة) ====================
    
    /**
     * Number of pre-authorizations associated with this visit
     */
    private Integer preAuthCount;
    
    /**
     * Latest pre-authorization ID (if any)
     */
    private Long latestPreAuthId;
    
    /**
     * Latest pre-authorization status (PENDING, APPROVED, REJECTED, etc.)
     */
    private String latestPreAuthStatus;
    
    /**
     * Latest pre-authorization status label in Arabic
     */
    private String latestPreAuthStatusLabel;
    
    // ==================== MEDICAL CATEGORY/SERVICE (CANONICAL) ====================
    
    /**
     * Medical Category ID (for coverage resolution)
     */
    private Long medicalCategoryId;
    
    /**
     * Medical Category name (for display)
     */
    private String medicalCategoryName;
    
    /**
     * Medical Service ID (FK to MedicalService)
     */
    private Long medicalServiceId;
    
    /**
     * Medical Service code (for display)
     */
    private String medicalServiceCode;
    
    /**
     * Medical Service name (for display)
     */
    private String medicalServiceName;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
