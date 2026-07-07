package com.waad.tba.modules.preauthorization.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Paginated Pre-Authorization List Response
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * USAGE:
 * This response is returned by paginated list endpoints:
 * - GET /api/v1/pre-authorizations
 * - GET /api/v1/pre-authorizations/inbox/pending
 * - GET /api/v1/pre-authorizations/inbox/approved
 * - GET /api/v1/pre-authorizations/status/{status}
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthorizationListResponse {
    
    /**
     * List of pre-authorization items (READ-ONLY)
     */
    private List<PreAuthorizationResponse> items;
    
    /**
     * Total number of pre-authorizations matching the query (READ-ONLY)
     */
    private Long total;
    
    /**
     * Current page number (1-indexed) (READ-ONLY)
     */
    private Integer page;
    
    /**
     * Page size (number of items per page) (READ-ONLY)
     */
    private Integer size;
    
    /**
     * Total number of pages (READ-ONLY)
     * Calculated as: ceil(total / size)
     */
    private Integer totalPages;
    
    /**
     * Whether there is a next page (READ-ONLY)
     */
    private Boolean hasNext;
    
    /**
     * Whether there is a previous page (READ-ONLY)
     */
    private Boolean hasPrevious;
}
