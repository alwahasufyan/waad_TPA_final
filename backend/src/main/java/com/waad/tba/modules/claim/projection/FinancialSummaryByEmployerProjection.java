package com.waad.tba.modules.claim.projection;

import java.math.BigDecimal;

/**
 * Projection interface for financial summary by employer query.
 * Type-safe alternative to Object[] mapping.
 */
public interface FinancialSummaryByEmployerProjection {
    
    /**
     * Employer organization ID
     */
    Long getEmployerOrgId();
    
    /**
     * Employer organization name
     */
    String getEmployerOrgName();
    
    /**
     * Total claims count
     */
    Long getClaimsCount();
    
    /**
     * Distinct members count
     */
    Long getMembersCount();
    
    /**
     * Total requested amount
     */
    BigDecimal getRequestedAmount();
    
    /**
     * Total approved amount
     */
    BigDecimal getApprovedAmount();
}
