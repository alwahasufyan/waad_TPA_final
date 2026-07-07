package com.waad.tba.modules.claim.dto;

import java.util.List;

public record MedicalReviewerProviderAssignmentsResponse(
        Long reviewerId,
        List<Long> assignedProviderIds
) {
}
