package com.waad.tba.modules.preauthorization.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): one service line within a
 * PreAuthorizationCreateDto.lines request. Mirrors ClaimLineDto's minimal
 * input shape — pricingItemId is the required identity; serviceCategoryId
 * is optional and falls back to the pricing item's own category, exactly
 * like the legacy flat-field create path already does.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorizationLineDto {

    @Positive(message = "Pricing Item ID must be positive")
    private Long pricingItemId;

    /**
     * @deprecated Use pricingItemId instead. Kept for backward compatibility
     *             with the legacy flat request shape wrapping into one line.
     */
    @Deprecated
    @Positive(message = "Medical Service ID must be positive")
    private Long medicalServiceId;

    @Positive(message = "Service Category ID must be positive")
    private Long serviceCategoryId;

    @Size(max = 255, message = "Service Category Name must not exceed 255 characters")
    private String serviceCategoryName;
}
