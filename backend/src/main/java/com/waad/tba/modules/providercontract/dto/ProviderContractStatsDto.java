package com.waad.tba.modules.providercontract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for contract statistics summary.
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderContractStatsDto {

    private long totalContracts;
    private long activeContracts;
    private long draftContracts;
    private long expiredContracts;
    private long suspendedContracts;
    private long terminatedContracts;

    private BigDecimal totalActiveValue;
    private BigDecimal totalExpiredValue;

    private long totalPricingItems;
    private BigDecimal averageDiscount;
}
