package com.waad.tba.modules.provider.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for assigning a service to a provider
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to assign a medical service to a provider")
@SuppressWarnings("deprecation")
public class ProviderServiceAssignDto {

    @NotBlank(message = "Service code is required")
    @Schema(description = "Medical service code (references MedicalService.code)", 
            example = "SRV-CARDIO-001", 
            required = true)
    @JsonAlias({"serviceCode", "service_code", "code"})
    private String serviceCode;
}

