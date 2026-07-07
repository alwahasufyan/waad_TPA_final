package com.waad.tba.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Recent Activity DTO
 * 
 * Represents a recent activity/event in the system.
 * Used for timeline display in dashboard.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivityDto {
    
    /**
     * Activity ID
     */
    private Long id;
    
    /**
     * Activity type (MEMBER_ADDED, CLAIM_SUBMITTED, CLAIM_APPROVED, etc.)
     */
    private String type;
    
    /**
     * Activity title (Arabic)
     */
    private String title;
    
    /**
     * Activity description (Arabic)
     */
    private String description;
    
    /**
     * Entity name (e.g., member name, claim number)
     */
    private String entityName;
    
    /**
     * Entity ID (for navigation)
     */
    private Long entityId;
    
    /**
     * Activity timestamp
     */
    private LocalDateTime createdAt;
}

