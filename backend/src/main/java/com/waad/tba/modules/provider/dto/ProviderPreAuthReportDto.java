package com.waad.tba.modules.provider.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider Pre-Authorization Report DTO
 * For provider-specific pre-auth reporting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderPreAuthReportDto {
    
    private Long preAuthId;
    private String preAuthNumber;
    private LocalDate requestDate;
    private LocalDate validFrom;
    private LocalDate validTo;
    
    // Member info
    private String memberName;
    private String memberBarcode;
    private String civilId;
    
    // Employer/Company
    private String employerName;
    
    // Financial
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    
    // Status
    private String status;
    private String statusLabel;
    
    // Service details
    private String serviceName;
    private Integer sessionsRequested;
    private Integer sessionsApproved;
    private Integer sessionsUsed;
    
    // Medical justification
    private String medicalJustification;
    private String diagnosis;
    
    // Reviewer
    private String reviewerName;
    private String reviewerNotes;
    private LocalDate reviewDate;
    
    // Attachments
    private Integer attachmentsCount;
}
