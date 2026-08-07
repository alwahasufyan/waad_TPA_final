package com.waad.tba.modules.preauthorization.api.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Pre-Authorization Line Request (WAAD-PREAUTH-MULTI-LINE-1)
 * ════════════════════════════════════════════════════════════════════════════
 *
 * One service line within {@link CreatePreAuthorizationRequest#getLines()}.
 * medicalServiceId is used as the pricing-item identifier, matching the
 * outer request's own legacy naming convention (see
 * PreAuthorizationApiMapper.toCreateDto).
 *
 * @since API v1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthorizationLineRequest {

    @Positive(message = "Medical Service ID must be positive")
    private Long medicalServiceId;

    @Positive(message = "Service Category ID must be positive")
    private Long serviceCategoryId;

    @Size(max = 255, message = "Service Category Name must not exceed 255 characters")
    private String serviceCategoryName;

    /**
     * WAAD-PREAUTH-LINE-QUANTITY-FIX-1: number of units/sessions requested.
     * Optional — defaults to 1 when absent, matching the previous (silent)
     * behavior for every request that predates this field.
     */
    @Positive(message = "Quantity must be positive")
    private Integer quantity;
}
