package com.waad.tba.modules.member.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for a member attribute (key-value pair)
 */
@Schema(description = "Member attribute (dynamic key-value)")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("deprecation")
public class MemberAttributeDto {
    
    @Schema(description = "Attribute ID (null for new)", example = "1")
    private Long id;
    
    @Schema(description = "Attribute code/key", example = "job_title", required = true)
    private String code;
    
    @Schema(description = "Attribute value", example = "Software Engineer")
    private String value;
    
    @Schema(description = "Display name (from definitions)", example = "Job Title")
    private String displayName;
    
    @Schema(description = "Data source", example = "MANUAL")
    private String source;
    
    /**
     * Convert from Map.Entry
     */
    public static MemberAttributeDto from(String code, String value) {
        return MemberAttributeDto.builder()
                .code(code)
                .value(value)
                .build();
    }
    
    /**
     * Convert from Map
     */
    public static java.util.List<MemberAttributeDto> fromMap(Map<String, String> attributes) {
        if (attributes == null) return java.util.List.of();
        return attributes.entrySet().stream()
                .map(e -> from(e.getKey(), e.getValue()))
                .toList();
    }
}

