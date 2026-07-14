package com.waad.tba.modules.providercontract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request/response DTOs for MC-4C simplified direct (version-less) price edits.
 * Each edit updates the ACTIVE price list in place and records a mandatory
 * audit entry — it never creates a new price-list version.
 */
public final class ContractPriceEditDtos {

    private ContractPriceEditDtos() {
    }

    public record PriceCorrectionRequest(
            @NotNull BigDecimal newPrice,
            @NotBlank String reason) {
    }

    public record AddServiceRequest(
            String serviceCode,
            @NotBlank String serviceName,
            @NotNull Long categoryId,
            Long medicalServiceId,
            @NotNull BigDecimal price,
            @NotBlank String reason) {
    }

    public record DeactivateServiceRequest(
            @NotBlank String reason) {
    }

    public record ReactivateServiceRequest(
            @NotBlank String reason) {
    }

    public record ClassificationCorrectionRequest(
            String newServiceCode,
            String newServiceName,
            Long newCategoryId,
            @NotBlank String reason) {
    }

    /** One audit row for the «سجل التعديلات» screen. */
    public record AuditEntry(
            Long id,
            Long providerId,
            String operationType,
            String serviceCode,
            String serviceName,
            BigDecimal oldPrice,
            BigDecimal newPrice,
            String oldValue,
            String newValue,
            String beforeState,
            String afterState,
            String reason,
            String changedBy,
            LocalDateTime changedAt) {
    }
}
