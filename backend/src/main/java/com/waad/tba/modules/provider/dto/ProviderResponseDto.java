package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Simple DTO for Provider PDF reports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponseDto {
    private Long id;
    
    /**
     * Provider name (unified field)
     */
    private String name;
    
    private String licenseNumber;
    private String taxNumber;
    private String city;
    private String address;
    private String phone;
    private String email;
    private String providerType;
    private String providerTypeLabel;
    private String networkStatus;
    private String networkStatusLabel;
    private Boolean active;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private BigDecimal defaultDiscountRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
