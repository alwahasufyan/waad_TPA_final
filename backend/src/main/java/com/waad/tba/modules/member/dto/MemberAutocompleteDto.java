package com.waad.tba.modules.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Phase 2: Arabic Fuzzy Name Search Autocomplete
 * Lightweight response for name-based autocomplete suggestions
 */
@Schema(description = "Autocomplete suggestion for member name search - Phase 2")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberAutocompleteDto {

    @Schema(description = "Member ID", example = "123")
    private Long memberId;

    @Schema(description = "Member full name", example = "أحمد محمد علي")
    private String fullName;

    @Schema(description = "Card number (optional)", example = "12345")
    private String cardNumber;

    @Schema(description = "Similarity score (0.0 to 1.0)", example = "0.85")
    private Double similarity;
}
