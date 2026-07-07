package com.waad.tba.modules.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for ProviderService response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Provider service assignment details")
public class ProviderServiceResponseDto {

    @Schema(description = "Assignment ID", example = "1")
    private Long id;

    @Schema(description = "Provider ID", example = "100")
    @JsonProperty("provider_id")
    private Long providerId;

    @Schema(description = "Medical service code", example = "SRV-CARDIO-001")
    @JsonProperty("service_code")
    private String serviceCode;

    @Schema(description = "Service name (unified field)", example = "Cardiac Exam")
    @JsonProperty("service_name")
    private String serviceName;

    @Schema(description = "Category Code", example = "CAT-001")
    @JsonProperty("category_code")
    private String categoryCode;

    @Schema(description = "Category Name", example = "Cardiology")
    @JsonProperty("category_name")
    private String categoryName;

    @Schema(description = "Requires Pre-Authorization", example = "true")
    @JsonProperty("requires_pre_auth")
    private Boolean requiresPreAuth;
    
    @Schema(description = "Assignment active status", example = "true")
    private Boolean active;

    @Schema(description = "Assignment creation timestamp")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "Assignment last update timestamp")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
