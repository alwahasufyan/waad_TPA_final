package com.waad.tba.modules.provider.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.provider.entity.ProviderAllowedEmployer;

/**
 * Repository for Provider-Partner Isolation
 * 
 * Manages which employers/partners a provider is allowed to access.
 */
@Repository
public interface ProviderAllowedEmployerRepository extends JpaRepository<ProviderAllowedEmployer, Long> {
    
    /**
     * Find all allowed employers for a provider (active and inactive)
     */
    List<ProviderAllowedEmployer> findByProviderId(Long providerId);
    
    /**
     * Find only ACTIVE allowed employers for a provider
     * Used for runtime authorization checks
     */
    List<ProviderAllowedEmployer> findByProviderIdAndActiveTrue(Long providerId);
    
    /**
     * Find specific provider-employer relationship
     */
    Optional<ProviderAllowedEmployer> findByProviderIdAndEmployerId(Long providerId, Long employerId);
    
    /**
     * Delete all allowed employers for a provider
     * Used when provider is deleted or when resetting partnerships
     */
    void deleteByProviderId(Long providerId);
    
    /**
     * Check if provider has access to a specific employer
     */
    @Query("SELECT CASE WHEN COUNT(pae) > 0 THEN true ELSE false END " +
           "FROM ProviderAllowedEmployer pae " +
           "WHERE pae.provider.id = :providerId " +
           "AND pae.employer.id = :employerId " +
           "AND pae.active = true")
    boolean hasActiveAccessToEmployer(@Param("providerId") Long providerId, 
                                      @Param("employerId") Long employerId);
}
