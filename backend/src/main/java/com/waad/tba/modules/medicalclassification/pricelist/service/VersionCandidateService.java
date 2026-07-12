package com.waad.tba.modules.medicalclassification.pricelist.service;

import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListVersion;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportLineRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MC-4C: one source of "what will this DRAFT version contain?" for both the
 * Financial Validation Engine (A10) and the Version Comparison report (A11).
 *
 * IMPORT drafts → the approved staging lines of the source import.
 * PATCH/ROLLBACK drafts → the version's own pre-materialized (inactive) rows.
 */
@Service
@RequiredArgsConstructor
public class VersionCandidateService {

    /** A candidate priced line of a draft version, source-agnostic. */
    public record CandidateItem(
            Long lineRef,          // import-line id or pricing-item id
            String lineRefType,    // IMPORT_LINE | PRICING_ITEM
            Long serviceId,        // catalog service (nullable)
            String serviceCode,
            String name,
            Long categoryId,
            BigDecimal price) {
    }

    private final PriceListImportLineRepository lineRepository;
    private final ProviderContractPricingItemRepository pricingItemRepository;
    private final MedicalServiceRepository serviceRepository;

    @Transactional(readOnly = true)
    public List<CandidateItem> candidatesOf(PriceListVersion version) {
        // Backfilled v1 versions have no source import but do have version-tagged
        // pricing rows; treat them like patch-like materialized artifacts.
        if (version.isPatchLike() || version.getSourceImportId() == null) {
            return pricingItemRepository.findByVersionId(version.getId()).stream()
                    .map(i -> new CandidateItem(
                            i.getId(), "PRICING_ITEM",
                            null,
                            i.getServiceCode(),
                            i.getServiceName(),
                            i.getMedicalCategory() != null ? i.getMedicalCategory().getId() : null,
                            i.getContractPrice()))
                    .toList();
        }

        List<PriceListImportLine> lines = lineRepository.findByImportIdAndReviewStatus(
                version.getSourceImportId(), PriceListImportLine.ReviewStatus.APPROVED);
        Map<Long, String> codes = new HashMap<>();
        serviceRepository.findAllById(lines.stream()
                        .map(PriceListImportLine::getFinalServiceId)
                        .filter(Objects::nonNull).toList())
                .forEach(s -> codes.put(s.getId(), s.getCode()));
        return lines.stream()
                .map(l -> new CandidateItem(
                        l.getId(), "IMPORT_LINE",
                        l.getFinalServiceId(),
                        codes.getOrDefault(l.getFinalServiceId(), l.getRawCode()),
                        l.getRawName(),
                        l.getFinalCategoryId(),
                        l.getFinalPrice()))
                .toList();
    }
}
