package com.waad.tba.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Cost by Provider DTO
 * 
 * Aggregated cost data grouped by provider.
 * Used for bar charts showing costs per provider.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostByProviderDto {
    
    /**
     * Provider ID
     */
    private Long providerId;
    
    /**
     * Provider name (Arabic)
     */
    private String providerName;
    
    /**
     * Total cost for this provider
     */
    private BigDecimal totalCost;
    
    /**
     * Number of claims for this provider
     */
    private Long claimCount;
}

