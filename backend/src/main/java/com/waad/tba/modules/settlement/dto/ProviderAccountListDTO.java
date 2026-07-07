package com.waad.tba.modules.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Provider Account List DTO
 * 
 * Used for listing provider accounts with provider names.
 * Includes all fields needed for the Provider Accounts List page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderAccountListDTO {

    private Long id;
    private Long providerId;
    private String providerName;
    private String providerType;
    
    private BigDecimal runningBalance;
    private BigDecimal totalApproved;
    private BigDecimal totalPaid;
    
    private String status;
    private String statusArabic;
    
    private int pendingClaimsCount;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
