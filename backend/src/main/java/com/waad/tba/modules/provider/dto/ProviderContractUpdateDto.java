package com.waad.tba.modules.provider.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for updating an existing ProviderContract
 * 
 * Note: serviceCode is NOT updatable
 * To change service, delete old contract and create new one
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderContractUpdateDto {

    /**
     * Contract Price
     * Optional - only if changing price
     */
    @DecimalMin(value = "0.0", inclusive = true, message = "Contract price must be >= 0")
    @Digits(integer = 10, fraction = 2, message = "Contract price must have max 10 digits and 2 decimals")
    @JsonAlias({"contractPrice", "contract_price", "price"})
    private BigDecimal contractPrice;

    /**
     * Currency
     * Optional - only if changing currency
     */
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    @JsonAlias({"currency"})
    private String currency;

    /**
     * Effective From Date
     * Optional - only if extending/changing start date
     */
    @JsonAlias({"effectiveFrom", "effective_from", "startDate", "start_date"})
    private LocalDate effectiveFrom;

    /**
     * Effective To Date
     * Optional - only if extending/changing end date
     * NULL = make contract open-ended
     */
    @JsonAlias({"effectiveTo", "effective_to", "endDate", "end_date"})
    private LocalDate effectiveTo;

    /**
     * Notes
     * Optional - only if updating notes
     */
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @JsonAlias({"notes", "comment", "description"})
    private String notes;

    /**
     * Active Status
     * Optional - for soft delete/reactivation
     */
    @JsonAlias({"active"})
    private Boolean active;
}
