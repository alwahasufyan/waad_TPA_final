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
 * DTO for creating a new ProviderContract
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderContractCreateDto {

    /**
     * Service Code
     * Must reference an existing MedicalService.code
     */
    @NotBlank(message = "Service code is required")
    @Size(max = 50, message = "Service code must not exceed 50 characters")
    @JsonAlias({"serviceCode", "service_code", "code"})
    private String serviceCode;

    /**
     * Contract Price
     * Must be >= 0
     */
    @NotNull(message = "Contract price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Contract price must be >= 0")
    @Digits(integer = 10, fraction = 2, message = "Contract price must have max 10 digits and 2 decimals")
    @JsonAlias({"contractPrice", "contract_price", "price"})
    private BigDecimal contractPrice;

    /**
     * Currency
     * ISO 4217 currency code
     * Default: LYD
     */
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    @JsonAlias({"currency"})
    @Builder.Default
    private String currency = "LYD";

    /**
     * Effective From Date
     * Contract starts on this date
     */
    @NotNull(message = "Effective from date is required")
    @JsonAlias({"effectiveFrom", "effective_from", "startDate", "start_date"})
    private LocalDate effectiveFrom;

    /**
     * Effective To Date
     * Contract ends on this date
     * NULL = open-ended contract
     */
    @JsonAlias({"effectiveTo", "effective_to", "endDate", "end_date"})
    private LocalDate effectiveTo;

    /**
     * Notes
     * Optional contract notes
     */
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @JsonAlias({"notes", "comment", "description"})
    private String notes;

    /**
     * Validate date range
     */
    @AssertTrue(message = "Effective to date must be after or equal to effective from date")
    public boolean isValidDateRange() {
        if (effectiveTo == null) {
            return true; // Open-ended contracts are valid
        }
        return !effectiveTo.isBefore(effectiveFrom);
    }
}
