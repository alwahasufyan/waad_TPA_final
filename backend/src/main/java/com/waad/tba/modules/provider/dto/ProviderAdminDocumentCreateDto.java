package com.waad.tba.modules.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for creating provider administrative documents
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderAdminDocumentCreateDto {
    
    @NotNull(message = "Provider ID is required")
    private Long providerId;
    
    @NotBlank(message = "Document type is required")
    private String type; // LICENSE, COMMERCIAL_REGISTER, TAX_CERTIFICATE, CONTRACT_COPY, OTHER
    
    @NotBlank(message = "File name is required")
    private String fileName;
    
    private LocalDate expiryDate;
    
    private String notes;
    
    private String documentNumber;
}
