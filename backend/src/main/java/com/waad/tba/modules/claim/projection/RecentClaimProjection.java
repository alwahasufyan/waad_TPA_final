package com.waad.tba.modules.claim.projection;

import java.time.LocalDateTime;
import com.waad.tba.modules.claim.entity.ClaimStatus;

/**
 * Projection interface for recent claims query.
 * Type-safe alternative to Object[] mapping.
 */
public interface RecentClaimProjection {
    
    /**
     * Claim ID
     */
    Long getId();
    
    /**
     * Member full name
     */
    String getMemberName();
    
    /**
     * Diagnosis description
     */
    String getDiagnosisDescription();
    
    /**
     * Claim status
     */
    ClaimStatus getStatus();
    
    /**
     * Claim creation date/time
     */
    LocalDateTime getCreatedAt();
}
