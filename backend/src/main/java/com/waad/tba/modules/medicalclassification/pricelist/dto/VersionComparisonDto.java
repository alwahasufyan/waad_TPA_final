package com.waad.tba.modules.medicalclassification.pricelist.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A11 — the approval artifact: statistical comparison of a DRAFT version
 * against the previous ACTIVE version, plus the financial-validation gate.
 * The approver approves THIS report, not a raw list of services.
 */
@Value
@Builder
public class VersionComparisonDto {

    Long versionId;
    Integer versionNo;
    String versionStatus;
    Long contractId;
    Long providerId;
    Long sourceImportId;
    Integer previousVersionNo;

    /** D1: approve and publish are separate stages — the UI gates «نشر» on these. */
    String approvedBy;
    java.time.LocalDateTime approvedAt;

    // Headline stats
    int totalServices;
    int previousTotalServices;
    int added;
    int removed;
    int repriced;
    int reclassified;
    int unchanged;
    BigDecimal totalValue;
    BigDecimal previousTotalValue;
    BigDecimal totalValueChangePercent;

    /** Histogram buckets of % price change (e.g. "<-50", "-50..-10", ...). */
    Map<String, Integer> priceChangeDistribution;

    List<ItemChange> topIncreases;
    List<ItemChange> topDecreases;
    List<ItemChange> addedItems;
    List<ItemChange> removedItems;
    List<ItemChange> reclassifiedItems;

    // Financial validation gate (A10)
    long openBlockers;
    long openWarnings;
    boolean publishGateOpen;

    @Value
    @Builder
    public static class ItemChange {
        Long lineId;
        String serviceCode;
        String serviceName;
        BigDecimal oldPrice;
        BigDecimal newPrice;
        BigDecimal changePercent;
        String oldCategory;
        String newCategory;
    }
}
