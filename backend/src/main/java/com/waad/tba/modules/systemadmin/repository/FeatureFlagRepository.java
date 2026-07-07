package com.waad.tba.modules.systemadmin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.systemadmin.entity.FeatureFlag;

/**
 * Repository for Feature Flag Entity
 * Phase 2 - System Administration
 */
@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {
    
    /**
     * Find feature flag by unique key
     */
    Optional<FeatureFlag> findByFlagKey(String flagKey);
    
    /**
     * Check if feature flag exists by key
     */
    boolean existsByFlagKey(String flagKey);
    
    /**
     * Find all enabled feature flags
     */
    List<FeatureFlag> findByEnabledTrue();
    
    /**
     * Find all disabled feature flags
     */
    List<FeatureFlag> findByEnabledFalse();
    
    /**
     * Find feature flags containing specific role in roleFilters
     * Note: This requires custom implementation for JSON search
     */
    @Query(value = "SELECT * FROM feature_flags WHERE " +
           "role_filters IS NOT NULL AND " +
           "JSON_CONTAINS(role_filters, JSON_QUOTE(?1))",
           nativeQuery = true)
    List<FeatureFlag> findByRoleFiltersContaining(String role);
}
