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
}
