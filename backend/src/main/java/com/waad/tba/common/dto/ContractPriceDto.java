package com.waad.tba.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Unified Contract Price Response DTO
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * ARCHITECTURAL STANDARD
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This is the CANONICAL response format for contract price queries.
 * All APIs returning contract prices MUST use this structure.
 * 
 * Response structure:
 * {
 *   "data": {
 *     "medicalServiceId": 100,
 *     "contractPrice": 500.00,
 *     "hasContract": true,
 *     "currency": "LYD"
 *   }
 * }
 * 
 * RULES:
 * 1. contractPrice is READ-ONLY - never allow manual entry
 * 2. If hasContract=false, contractPrice should be null
 * 3. Price always comes from ProviderContract, never from MedicalService.basePrice
 * 
 * @version 1.0
 * @since 2026-01-22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractPriceDto {

    /**
     * Medical Service ID
     */
    private Long medicalServiceId;
    
    /**
     * Service Code (for reference)
     */
    private String serviceCode;
    
    /**
     * Service Name (Arabic)
     */
    private String serviceName;

    /**
     * Contract price from ProviderContract
     * NULL if no contract exists
     * 
     * ARCHITECTURAL RULE: This is READ-ONLY
     * - Must come from ProviderContractPricingItem.contractPrice
     * - Never from MedicalService.basePrice
     * - Never from manual entry
     */
    private BigDecimal contractPrice;
    
    /**
     * Base price (reference only - DEPRECATED)
     * @deprecated Use contractPrice for all calculations
     */
    @Deprecated
    private BigDecimal basePrice;

    /**
     * Whether a valid contract exists for this service/provider combination
     */
    private boolean hasContract;

    /**
     * Currency code (ISO 4217)
     * Default: LYD
     */
    @Builder.Default
    private String currency = "LYD";
    
    /**
     * Provider ID this price is for
     */
    private Long providerId;
    
    /**
     * Contract ID
     */
    private Long contractId;
    
    /**
     * Error/warning message if any
     */
    private String message;

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create response when contract is found
     */
    public static ContractPriceDto fromContract(
            Long medicalServiceId,
            String serviceCode,
            String serviceName,
            BigDecimal contractPrice,
            Long providerId,
            Long contractId) {
        
        return ContractPriceDto.builder()
                .medicalServiceId(medicalServiceId)
                .serviceCode(serviceCode)
                .serviceName(serviceName)
                .contractPrice(contractPrice)
                .hasContract(true)
                .providerId(providerId)
                .contractId(contractId)
                .currency("LYD")
                .message("Contract price found")
                .build();
    }

    /**
     * Create response when no contract is found
     */
    public static ContractPriceDto noContract(
            Long medicalServiceId,
            String serviceCode,
            String serviceName,
            Long providerId) {
        
        return ContractPriceDto.builder()
                .medicalServiceId(medicalServiceId)
                .serviceCode(serviceCode)
                .serviceName(serviceName)
                .contractPrice(null)
                .hasContract(false)
                .providerId(providerId)
                .contractId(null)
                .currency("LYD")
                .message("No contract found for this service and provider")
                .build();
    }

    /**
     * Create response for service not found
     */
    public static ContractPriceDto serviceNotFound(Long medicalServiceId) {
        return ContractPriceDto.builder()
                .medicalServiceId(medicalServiceId)
                .hasContract(false)
                .message("Medical service not found")
                .build();
    }
}
