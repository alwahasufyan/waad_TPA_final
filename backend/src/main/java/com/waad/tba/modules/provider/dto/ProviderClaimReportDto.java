package com.waad.tba.modules.provider.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider Claims Report DTO
 * For provider-specific claims reporting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderClaimReportDto {
    
    private Long claimId;
    private String claimNumber;
    private LocalDate claimDate;
    private LocalDate submissionDate;
    
    // Member info
    private String memberName;
    private String memberBarcode;
    private String civilId;
    
    // Employer/Company
    private String employerName;
    
    // Financial
    private BigDecimal claimedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal rejectedAmount;
    private BigDecimal netAmount;
    
    // Status
    private String status;
    private String statusLabel;
    
    // Visit reference
    private Long visitId;
    private String visitNumber;
    
    // Services count
    private Integer servicesCount;
    private Integer attachmentsCount;
    
    // Reviewer notes
    private String reviewerNotes;
    private LocalDate reviewDate;
    
    // Diagnosis
    private String diagnosis;
}
