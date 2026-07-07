package com.waad.tba.modules.providercontract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Simple DTO for Provider Contract PDF reports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractResponseDto {
    private Long id;
    private String contractCode;
    private String contractNumber;
    private String providerName;
    private String status;
    private String statusLabel;
    private String pricingModel;
    private String pricingModelLabel;
    private BigDecimal discountPercent;
    private BigDecimal totalValue;
    private String currency;
    private String paymentTerms;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate signedDate;
    private Boolean autoRenew;
    private String contactPerson;
    private String contactPhone;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
