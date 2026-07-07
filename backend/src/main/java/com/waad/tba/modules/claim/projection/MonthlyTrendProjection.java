package com.waad.tba.modules.claim.projection;

/**
 * Projection interface for monthly trends query.
 * Type-safe alternative to Object[] mapping.
 */
public interface MonthlyTrendProjection {
    
    /**
     * Year of the trend data
     */
    Integer getYear();
    
    /**
     * Month of the trend data (1-12)
     */
    Integer getMonth();
    
    /**
     * Count of claims in this month
     */
    Long getCount();
}
