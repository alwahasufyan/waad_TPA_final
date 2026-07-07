package com.waad.tba.modules.claim.projection;

/**
 * Projection interface for service distribution query.
 * Type-safe alternative to Object[] mapping.
 */
public interface ServiceDistributionProjection {
    
    /**
     * Service/category name
     */
    String getServiceName();
    
    /**
     * Count of claims for this service
     */
    Long getCount();
}
