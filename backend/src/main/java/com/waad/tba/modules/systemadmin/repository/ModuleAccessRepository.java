package com.waad.tba.modules.systemadmin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.systemadmin.entity.ModuleAccess;

/**
 * Repository for Module Access Entity
 * Phase 2 - System Administration
 */
@Repository
public interface ModuleAccessRepository extends JpaRepository<ModuleAccess, Long> {
    
    /**
     * Find module by unique key
     */
    Optional<ModuleAccess> findByModuleKey(String moduleKey);
    
    /**
     * Check if module exists by key
     */
    boolean existsByModuleKey(String moduleKey);
    
    /**
     * Find all active modules
     */
    List<ModuleAccess> findByActiveTrue();
    
    /**
     * Find modules by feature flag key
     */
    List<ModuleAccess> findByFeatureFlagKey(String featureFlagKey);
    
    /**
     * Find modules accessible by specific role
     * Note: This requires custom implementation for JSON search
     */
    @Query(value = "SELECT * FROM module_access WHERE " +
           "active = true AND " +
           "JSON_CONTAINS(allowed_roles, JSON_QUOTE(?1))",
           nativeQuery = true)
    List<ModuleAccess> findByAllowedRolesContaining(String role);
}
