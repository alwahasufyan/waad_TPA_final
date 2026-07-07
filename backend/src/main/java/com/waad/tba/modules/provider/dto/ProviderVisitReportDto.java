package com.waad.tba.modules.provider.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider Visits Report DTO
 * For provider-specific visits history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderVisitReportDto {
    
    private Long visitId;
    private String visitNumber;
    private LocalDate visitDate;
    
    // Member info
    private String memberName;
    private String memberBarcode;
    private String civilId;
    
    // Employer/Company
    private String employerName;
    
    // Visit type
    private String visitType;
    private String visitTypeLabel;
    
    // Clinical info
    private String chiefComplaint;
    private String diagnosis;
    
    // Related records
    private Integer claimCount;
    private Integer preAuthCount;
    
    // Financial summary
    private BigDecimal totalAmount;
    private BigDecimal approvedAmount;
    private BigDecimal pendingAmount;
    
    // Status
    private String status;
    private String statusLabel;
    
    // Timestamps
    private LocalDate createdAt;
    private LocalDate closedAt;
}
