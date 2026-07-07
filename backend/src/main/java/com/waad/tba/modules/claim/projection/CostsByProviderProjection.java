package com.waad.tba.modules.claim.projection;

import java.math.BigDecimal;

/**
 * Projection interface for costs by provider query.
 * Type-safe alternative to Object[] mapping.
 */
public interface CostsByProviderProjection {
    
    /**
     * Provider ID (may be null for unassigned claims)
     */
    Long getProviderId();
    
    /**
     * Provider name
     */
    String getProviderName();
    
    /**
     * Total approved amount (cost)
     */
    BigDecimal getTotalCost();
    
    /**
     * Number of claims for this provider
     */
    Long getClaimCount();
}
