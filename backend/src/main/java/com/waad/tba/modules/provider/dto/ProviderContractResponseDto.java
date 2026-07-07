package com.waad.tba.modules.provider.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for ProviderContract Response
 * 
 * Enriched with service names from MedicalService
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProviderContractResponseDto {

    /**
     * Contract ID
     */
    private Long id;

    /**
     * Provider ID
     */
    private Long providerId;

    /**
     * Service Code
     */
    private String serviceCode;

    /**
     * Service Name (unified field)
     * Fetched from MedicalService
     */
    private String serviceName;

    /**
     * Contract Price
     */
    private BigDecimal contractPrice;

    /**
     * Currency
     */
    private String currency;

    /**
     * Effective From Date
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveFrom;

    /**
     * Effective To Date
     * NULL = open-ended contract
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveTo;

    /**
     * Active Status
     */
    private boolean active;

    /**
     * Notes
     */
    private String notes;

    /**
     * Is Currently Effective
     * Computed field - true if contract is effective today
     */
    private Boolean isCurrentlyEffective;

    /**
     * Is Expired
     * Computed field - true if contract has expired
     */
    private Boolean isExpired;

    /**
     * Is Open-Ended
     * Computed field - true if contract has no end date
     */
    private Boolean isOpenEnded;

    /**
     * Created At
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Updated At
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * Created By
     */
    private String createdBy;

    /**
     * Updated By
     */
    private String updatedBy;
}
