package com.waad.tba.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Service Distribution DTO
 * 
 * Aggregated data for service type distribution.
 * Used for donut charts showing service distribution.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDistributionDto {
    
    /**
     * Service type/code
     */
    private String serviceType;
    
    /**
     * Service name (Arabic)
     */
    private String serviceName;
    
    /**
     * Count of claims/visits for this service
     */
    private Long count;
    
    /**
     * Percentage of total (calculated client-side or server-side)
     */
    private Double percentage;
}

