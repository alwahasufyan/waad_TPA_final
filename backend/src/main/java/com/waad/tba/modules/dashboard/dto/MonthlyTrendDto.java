package com.waad.tba.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Monthly Trend DTO
 * 
 * Represents aggregated data for a specific month.
 * Used for charts showing trends over time.
 * 
 * NOTE: month is serialized as ISO string "YYYY-MM" for Frontend compatibility.
 * 
 * @author TBA WAAD System
 * @version 2026.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyTrendDto {
    
    /**
     * Year and month as ISO string (e.g., "2024-01")
     * Frontend-friendly format - no parsing needed
     */
    private String month;
    
    /**
     * Count for this month
     */
    private Long count;
    
    /**
     * Total amount for this month (if applicable)
     */
    private BigDecimal amount;
}

