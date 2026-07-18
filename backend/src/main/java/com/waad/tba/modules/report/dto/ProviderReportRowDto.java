package com.waad.tba.modules.report.dto;

import com.waad.tba.modules.provider.entity.Provider;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only projection row for the Providers report.
 *
 * NOTE: the constructor signature MUST match the JPQL {@code SELECT new ...} in
 * {@link com.waad.tba.modules.report.repository.ProviderReportQueryRepository}.
 * Derived fields ({@code contractStatus}) are set by the service, never persisted.
 */
@Getter
@Setter
public class ProviderReportRowDto {

    private Long id;
    private String code; // provider licence number (authoritative provider code)
    private String name;
    private Provider.ProviderType providerType;
    private String city;
    private Boolean active;
    private Provider.NetworkTier networkStatus;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private LocalDateTime updatedAt;
    private Long activePriceListCount;
    private Integer activePriceListVersionNo;
    private String activeContractCode; // code of the provider's ACTIVE modern contract (nullable)

    // Derived, computed server-side (not from DB): ACTIVE / EXPIRING_SOON /
    // EXPIRED / FUTURE / INACTIVE / NONE.
    private String contractStatus;
    private Boolean hasActivePriceList;

    /** JPQL projection constructor — order/type must match the SELECT new(...). */
    public ProviderReportRowDto(
            Long id,
            String code,
            String name,
            Provider.ProviderType providerType,
            String city,
            Boolean active,
            Provider.NetworkTier networkStatus,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            LocalDateTime updatedAt,
            Long activePriceListCount,
            Integer activePriceListVersionNo,
            String activeContractCode) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.providerType = providerType;
        this.city = city;
        this.active = active;
        this.networkStatus = networkStatus;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.updatedAt = updatedAt;
        this.activePriceListCount = activePriceListCount == null ? 0L : activePriceListCount;
        this.activePriceListVersionNo = activePriceListVersionNo;
        this.activeContractCode = activeContractCode;
        this.hasActivePriceList = this.activePriceListCount > 0;
    }
}
