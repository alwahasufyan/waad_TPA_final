package com.waad.tba.common.entity;

/**
 * EmployerScoped Interface
 * 
 * Purpose: Marks entities that belong to a specific employer.
 * Security: Ensures employer-level data isolation across the system.
 * 
 * Implementation Pattern:
 * - Any entity implementing this interface MUST be filterable by employerId
 * - Used by EmployerFilterSpecification for unified filtering
 * - Prevents data leakage between employers
 * 
 * Examples:
 * - Member implements EmployerScoped (direct employer_id)
 * - Claim implements EmployerScoped (via member.employer_id)
 * - Visit implements EmployerScoped (via member.employer_id)
 * 
 * Domain Architecture Decision (2026-02-13):
 * - Employer is the ONLY top-level business entity
 * - No organization type hierarchy
 * - Single security boundary: employerId
 * 
 * @see com.waad.tba.common.specification.EmployerFilterSpecification
 */
public interface EmployerScoped {
    
    /**
     * Get the employer ID for this entity.
     * 
     * @return Employer ID, or null if not applicable
     */
    Long getEmployerId();
}
