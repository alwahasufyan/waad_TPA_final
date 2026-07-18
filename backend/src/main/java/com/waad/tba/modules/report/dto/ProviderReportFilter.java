package com.waad.tba.modules.report.dto;

import com.waad.tba.modules.provider.entity.Provider;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Server-side filter for the Providers report. All fields are optional; a null
 * field means "no restriction". The same filter drives preview rows, summary and
 * export so the three always agree.
 */
@Getter
@Setter
@Builder
public class ProviderReportFilter {
    private Long providerId; // exact provider (from the report's provider picker)
    private String search;
    private String name;
    private String code;
    private Provider.ProviderType providerType;
    private String city;
    private Boolean active;

    // Contract (provider-level dates) derived flags
    private Boolean hasActiveContract;
    private Boolean expired;
    private Boolean expiringSoon;
    private LocalDate contractStartFrom;
    private LocalDate contractStartTo;
    private LocalDate contractEndFrom;
    private LocalDate contractEndTo;

    // Price list
    private Boolean hasActivePriceList;
}
