package com.waad.tba.modules.claim.projection;

import java.math.BigDecimal;
import com.waad.tba.modules.claim.entity.ClaimStatus;

/**
 * Projection interface for financial summary by status query.
 * Type-safe alternative to Object[] mapping.
 */
public interface FinancialSummaryByStatusProjection {
    
    /**
     * Claim status
     */
    ClaimStatus getStatus();
    
    /**
     * Count of claims in this status
     */
    Long getCount();
    
    /**
     * Total requested amount for this status
     */
    BigDecimal getTotalRequestedAmount();
    
    /**
     * Total approved amount for this status
     */
    BigDecimal getTotalApprovedAmount();
}
