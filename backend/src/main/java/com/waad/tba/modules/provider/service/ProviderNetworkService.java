package com.waad.tba.modules.provider.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.enums.NetworkType;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider Network Validation Service.
 * 
 * Determines if a provider is IN_NETWORK or OUT_OF_NETWORK based on:
 * 1. Provider exists and is active
 * 2. Provider has an active contract
 * 3. Contract is within valid date range
 * 
 * Network status affects:
 * - Coverage percentage (IN_NETWORK: 80%, OUT_OF_NETWORK: 60%)
 * - Co-pay percentage (IN_NETWORK: 20%, OUT_OF_NETWORK: 40%)
 * - Discount rates from negotiated contracts
 * 
 * @since Phase 7 - Operational Completeness
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderNetworkService {
    
    private final ProviderRepository providerRepository;
    private final ProviderContractRepository providerContractRepository;
    
    /**
     * Determine the network type for a provider.
     * 
     * @param providerId The provider ID to check
     * @return NetworkType.IN_NETWORK if provider has active contract, OUT_OF_NETWORK otherwise
     */
    public NetworkType determineNetworkType(Long providerId) {
        if (providerId == null) {
            log.debug("Provider ID is null, treating as OUT_OF_NETWORK");
            return NetworkType.OUT_OF_NETWORK;
        }
        
        Optional<Provider> provider = providerRepository.findById(providerId);
        if (provider.isEmpty() || !provider.get().getActive()) {
            log.debug("Provider {} not found or inactive, treating as OUT_OF_NETWORK", providerId);
            return NetworkType.OUT_OF_NETWORK;
        }
        
        return hasActiveContract(providerId) ? NetworkType.IN_NETWORK : NetworkType.OUT_OF_NETWORK;
    }
    
    /**
     * Determine network type by provider name (for claims that reference providers by name).
     * 
     * @param providerName The provider name to lookup
     * @return NetworkType.IN_NETWORK if found with active contract, OUT_OF_NETWORK otherwise
     */
    public NetworkType determineNetworkTypeByName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return NetworkType.OUT_OF_NETWORK;
        }
        
        // Search for provider by name (Arabic or English)
        var providers = providerRepository.search(providerName);
        
        for (Provider provider : providers) {
            // Exact match check
            if (providerName.equalsIgnoreCase(provider.getName())) {
                if (provider.getActive() && hasActiveContract(provider.getId())) {
                    log.debug("Provider '{}' found IN_NETWORK", providerName);
                    return NetworkType.IN_NETWORK;
                }
            }
        }
        
        log.debug("Provider '{}' not found or has no active contract, treating as OUT_OF_NETWORK", providerName);
        return NetworkType.OUT_OF_NETWORK;
    }
    
    /**
     * Check if a provider has an active contract.
     * 
     * @param providerId The provider ID
     * @return true if provider has at least one active, non-expired contract
     */
    public boolean hasActiveContract(Long providerId) {
        if (providerId == null) {
            return false;
        }
        
        var contracts = providerContractRepository.findByProviderIdAndActiveTrue(providerId);
        LocalDate today = LocalDate.now();
        
        return contracts.stream().anyMatch(contract -> 
            contract.getActive() && isContractDateValid(contract, today)
        );
    }
    
    /**
     * Get the best discount rate for a provider.
     * Uses the highest discount from active contracts.
     * 
     * @param providerId The provider ID
     * @return Discount rate (0-100) or BigDecimal.ZERO if no contract
     */
    public BigDecimal getProviderDiscountRate(Long providerId) {
        if (providerId == null) {
            return BigDecimal.ZERO;
        }
        
        var contracts = providerContractRepository.findByProviderIdAndActiveTrue(providerId);
        LocalDate today = LocalDate.now();
        
        return contracts.stream()
            .filter(c -> c.getActive() && isContractDateValid(c, today))
            .map(ProviderContract::getDiscountPercent)
            .filter(rate -> rate != null)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    }
    
    /**
     * Calculate network-adjusted claim amounts.
     * 
     * @param requestedAmount The total claim amount
     * @param networkType The provider's network type
     * @return NetworkCostCalculation with coverage and co-pay amounts
     */
    public NetworkCostCalculation calculateNetworkCosts(BigDecimal requestedAmount, NetworkType networkType) {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new NetworkCostCalculation(
                networkType,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            );
        }
        
        BigDecimal coveragePercent = networkType.getDefaultCoveragePercent();
        BigDecimal coPayPercent = networkType.getDefaultCoPayPercent();
        
        BigDecimal coverageAmount = requestedAmount.multiply(coveragePercent)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal coPayAmount = requestedAmount.multiply(coPayPercent)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        
        return new NetworkCostCalculation(
            networkType,
            coveragePercent,
            coPayPercent,
            coverageAmount,
            coPayAmount
        );
    }
    
    /**
     * Calculate network costs with provider discount applied.
     * 
     * @param requestedAmount The total claim amount
     * @param providerId The provider ID
     * @return NetworkCostCalculation with discount applied
     */
    public NetworkCostCalculation calculateNetworkCostsWithDiscount(BigDecimal requestedAmount, Long providerId) {
        NetworkType networkType = determineNetworkType(providerId);
        BigDecimal discountRate = getProviderDiscountRate(providerId);
        
        // Apply discount to the requested amount first
        BigDecimal discountedAmount = requestedAmount;
        if (discountRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                discountRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
            );
            discountedAmount = requestedAmount.multiply(discountMultiplier)
                .setScale(2, RoundingMode.HALF_UP);
        }
        
        NetworkCostCalculation baseCosts = calculateNetworkCosts(discountedAmount, networkType);
        
        // Return with original amounts but discounted calculations
        return new NetworkCostCalculation(
            networkType,
            baseCosts.coveragePercent(),
            baseCosts.coPayPercent(),
            baseCosts.coverageAmount(),
            baseCosts.coPayAmount(),
            discountRate,
            requestedAmount.subtract(discountedAmount) // discount amount
        );
    }
    
    /**
     * Validate a provider for claim submission.
     * 
     * @param providerName Provider name from the claim
     * @return ValidationResult with network status and any warnings
     */
    public ProviderValidationResult validateProviderForClaim(String providerName) {
        NetworkType networkType = determineNetworkTypeByName(providerName);
        
        String warning = null;
        if (networkType == NetworkType.OUT_OF_NETWORK) {
            warning = "Provider '" + providerName + "' is OUT_OF_NETWORK. " +
                     "Coverage will be reduced to " + networkType.getDefaultCoveragePercent() + "% " +
                     "with " + networkType.getDefaultCoPayPercent() + "% co-pay.";
        }
        
        return new ProviderValidationResult(
            true, // Always allow - just warn for out-of-network
            networkType,
            warning
        );
    }
    
    /**
     * Check if contract dates are valid (within start and end dates).
     */
    private boolean isContractDateValid(ProviderContract contract, LocalDate today) {
        if (contract.getStartDate() == null) {
            return false;
        }
        if (contract.getStartDate().isAfter(today)) {
            return false; // Not yet started
        }
        if (contract.getEndDate() != null && contract.getEndDate().isBefore(today)) {
            return false; // Expired
        }
        return true;
    }
    
    // ==================== Record Types ====================
    
    /**
     * Network cost calculation result.
     */
    public record NetworkCostCalculation(
        NetworkType networkType,
        BigDecimal coveragePercent,
        BigDecimal coPayPercent,
        BigDecimal coverageAmount,
        BigDecimal coPayAmount,
        BigDecimal discountRate,
        BigDecimal discountAmount
    ) {
        /** Constructor without discount */
        public NetworkCostCalculation(
            NetworkType networkType,
            BigDecimal coveragePercent,
            BigDecimal coPayPercent,
            BigDecimal coverageAmount,
            BigDecimal coPayAmount
        ) {
            this(networkType, coveragePercent, coPayPercent, coverageAmount, coPayAmount, 
                 BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
    
    /**
     * Provider validation result for claim submission.
     */
    public record ProviderValidationResult(
        boolean valid,
        NetworkType networkType,
        String warning
    ) {}
}
