package com.waad.tba.modules.medicalclassification.pricelist.service;

import com.waad.tba.modules.medicalclassification.engine.service.ArabicTextCanonicalizer;
import com.waad.tba.modules.medicalclassification.pricelist.dto.VersionComparisonDto;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListVersion;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportLineRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListVersionRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Version Comparison (A11) — computes the statistical report the approver
 * approves: added / removed / repriced / reclassified, price-change
 * distribution, top movements, and totals, plus the A10 gate state.
 */
@Service
@RequiredArgsConstructor
public class VersionComparisonService {

    private static final int TOP_N = 20;

    private final PriceListVersionRepository versionRepository;
    private final PriceListImportLineRepository lineRepository;
    private final ProviderContractPricingItemRepository pricingItemRepository;
    private final MedicalServiceRepository serviceRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final FinancialValidationService validationService;
    private final VersionCandidateService candidateService;

    @Transactional(readOnly = true)
    public VersionComparisonDto compare(Long versionId) {
        PriceListVersion version = versionRepository.findById(versionId).orElseThrow();

        // MC-4C: source-agnostic candidates (import lines OR patch/rollback rows)
        List<VersionCandidateService.CandidateItem> candidates = candidateService.candidatesOf(version);

        List<PriceListVersion> history = versionRepository.findByContractIdOrderByVersionNoDesc(version.getContractId());
        PriceListVersion previousVersion = version.getStatus() == PriceListVersion.Status.DRAFT
                ? history.stream().filter(v -> v.getStatus() == PriceListVersion.Status.ACTIVE).findFirst().orElse(null)
                : history.stream().filter(v -> v.getVersionNo() < version.getVersionNo())
                        .filter(v -> v.getStatus() == PriceListVersion.Status.ACTIVE || v.getStatus() == PriceListVersion.Status.SUPERSEDED)
                        .findFirst().orElse(null);
        List<VersionCandidateService.CandidateItem> previousCandidates = previousVersion == null
                ? List.of() : candidateService.candidatesOf(previousVersion);
        Integer previousVersionNo = previousVersion == null ? null : previousVersion.getVersionNo();

        Map<Long, MedicalCategory> categories = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categories.put(c.getId(), c));

        Map<String, VersionCandidateService.CandidateItem> prevByKey = new HashMap<>();
        for (VersionCandidateService.CandidateItem i : previousCandidates) {
            if (i.serviceCode() != null) prevByKey.putIfAbsent("C:" + i.serviceCode().trim().toUpperCase(), i);
            if (i.name() != null) prevByKey.putIfAbsent("N:" + ArabicTextCanonicalizer.canonicalize(i.name()), i);
        }

        List<VersionComparisonDto.ItemChange> addedItems = new ArrayList<>();
        List<VersionComparisonDto.ItemChange> repricedItems = new ArrayList<>();
        List<VersionComparisonDto.ItemChange> reclassifiedItems = new ArrayList<>();
        Set<Long> matchedPrevIds = new HashSet<>();
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (String b : List.of("≤ -50%", "-50..-30%", "-30..-10%", "-10..+10%", "+10..+30%", "+30..+50%", "≥ +50%")) {
            distribution.put(b, 0);
        }

        BigDecimal totalValue = BigDecimal.ZERO;
        int unchanged = 0;

        for (VersionCandidateService.CandidateItem line : candidates) {
            String code = line.serviceCode();
            BigDecimal price = line.price() == null ? BigDecimal.ZERO : line.price();
            totalValue = totalValue.add(price);

            VersionCandidateService.CandidateItem prev = null;
            if (code != null) prev = prevByKey.get("C:" + code.trim().toUpperCase());
            if (prev == null && line.serviceCode() != null) prev = prevByKey.get("C:" + line.serviceCode().trim().toUpperCase());
            if (prev == null) prev = prevByKey.get("N:" + ArabicTextCanonicalizer.canonicalize(line.name()));

            if (prev == null) {
                addedItems.add(change(line, code, null, price, null, null,
                        categoryName(categories, line.categoryId())));
                continue;
            }
            matchedPrevIds.add(prev.lineRef());

            BigDecimal oldPrice = prev.price();
            BigDecimal changePct = null;
            if (oldPrice != null && oldPrice.compareTo(BigDecimal.ZERO) > 0) {
                changePct = price.subtract(oldPrice).divide(oldPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                bucket(distribution, changePct.doubleValue());
            }
            boolean priceChanged = oldPrice == null || oldPrice.compareTo(price) != 0;
            Long oldCatId = prev.categoryId();
            boolean catChanged = oldCatId != null && line.categoryId() != null
                    && !oldCatId.equals(line.categoryId());

            if (catChanged) {
                reclassifiedItems.add(change(line, code, oldPrice, price, changePct,
                        categoryName(categories, oldCatId),
                        categoryName(categories, line.categoryId())));
            }
            if (priceChanged) {
                repricedItems.add(change(line, code, oldPrice, price, changePct, null,
                        categoryName(categories, line.categoryId())));
            } else if (!catChanged) {
                unchanged++;
            }
        }

        List<VersionComparisonDto.ItemChange> removedItems = previousCandidates.stream()
                .filter(i -> !matchedPrevIds.contains(i.lineRef()))
                .map(i -> VersionComparisonDto.ItemChange.builder()
                        .lineId(i.lineRef()).serviceCode(i.serviceCode()).serviceName(i.name()).oldPrice(i.price())
                        .oldCategory(categoryName(categories, i.categoryId()))
                        .build())
                .toList();

        BigDecimal previousTotal = previousCandidates.stream()
                .map(VersionCandidateService.CandidateItem::price)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalChangePct = previousTotal.compareTo(BigDecimal.ZERO) > 0
                ? totalValue.subtract(previousTotal).divide(previousTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null;

        Comparator<VersionComparisonDto.ItemChange> byPct = Comparator.comparing(
                c -> c.getChangePercent() == null ? BigDecimal.ZERO : c.getChangePercent());

        FinancialValidationService.GateState gate = validationService.gateState(versionId);

        return VersionComparisonDto.builder()
                .versionId(versionId)
                .versionNo(version.getVersionNo())
                .versionStatus(version.getStatus().name())
                .contractId(version.getContractId())
                .providerId(version.getProviderId())
                .sourceImportId(version.getSourceImportId())
                .previousVersionNo(previousVersionNo)
                .approvedBy(version.getApprovedBy())
                .approvedAt(version.getApprovedAt())
                .totalServices(candidates.size())
                .previousTotalServices(previousCandidates.size())
                .added(addedItems.size())
                .removed(removedItems.size())
                .repriced(repricedItems.size())
                .reclassified(reclassifiedItems.size())
                .unchanged(unchanged)
                .totalValue(totalValue)
                .previousTotalValue(previousTotal)
                .totalValueChangePercent(totalChangePct)
                .priceChangeDistribution(distribution)
                .topIncreases(repricedItems.stream().sorted(byPct.reversed()).limit(TOP_N).toList())
                .topDecreases(repricedItems.stream().sorted(byPct).limit(TOP_N).toList())
                .addedItems(addedItems.stream().limit(200).toList())
                .removedItems(removedItems.stream().limit(200).toList())
                .reclassifiedItems(reclassifiedItems.stream().limit(200).toList())
                .openBlockers(gate.getOpenBlockers())
                .openWarnings(gate.getOpenWarnings())
                .publishGateOpen(gate.isOpen())
                .build();
    }

    private static VersionComparisonDto.ItemChange change(VersionCandidateService.CandidateItem line, String code,
            BigDecimal oldPrice, BigDecimal newPrice, BigDecimal changePct,
            String oldCategory, String newCategory) {
        return VersionComparisonDto.ItemChange.builder()
                .lineId(line.lineRef()) // lineId might be pricingItemId for PATCH
                .serviceCode(code)
                .serviceName(line.name())
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .changePercent(changePct)
                .oldCategory(oldCategory)
                .newCategory(newCategory)
                .build();
    }

    private static String categoryName(Map<Long, MedicalCategory> categories, Long id) {
        MedicalCategory c = id == null ? null : categories.get(id);
        return c == null ? null : c.getName();
    }

    private static void bucket(Map<String, Integer> dist, double pct) {
        String key;
        if (pct <= -50) key = "≤ -50%";
        else if (pct <= -30) key = "-50..-30%";
        else if (pct <= -10) key = "-30..-10%";
        else if (pct < 10) key = "-10..+10%";
        else if (pct < 30) key = "+10..+30%";
        else if (pct < 50) key = "+30..+50%";
        else key = "≥ +50%";
        dist.merge(key, 1, Integer::sum);
    }
}
