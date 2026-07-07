package com.waad.tba.modules.claim.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MedicalReviewerProviderAssignmentsRequest(
        @NotNull(message = "providerIds is required")
        List<Long> providerIds
) {
}
