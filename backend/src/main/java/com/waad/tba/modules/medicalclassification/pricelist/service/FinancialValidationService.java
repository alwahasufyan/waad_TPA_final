package com.waad.tba.modules.medicalclassification.pricelist.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.medicalclassification.engine.service.ClassificationSettingsService;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListValidationFinding;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListVersion;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportLineRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListValidationFindingRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListVersionRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Financial Validation Engine (A10) — the pre-publish gate.
 *
 * Classification review protects WHAT a service is; this protects WHAT IT
 * COSTS. Runs at draft creation and again at publish time (authoritative).
 * Gate: OPEN BLOCKERs make publish impossible (never waivable); WARNINGs must
 * be RESOLVED or WAIVED with an audited note. Thresholds come from
 * classification_settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialValidationService {

    private final PriceListVersionRepository versionRepository;
    private final PriceListImportLineRepository lineRepository;
    private final PriceListValidationFindingRepository findingRepository;
    private final ProviderContractPricingItemRepository pricingItemRepository;
    private final MedicalServiceRepository serviceRepository;
    private final ClassificationSettingsService settings;
    private final VersionCandidateService candidateService;

    @Value
    public static class GateState {
        long openBlockers;
        long openWarnings;

        public boolean isOpen() {
            return openBlockers == 0 && openWarnings == 0;
        }
    }

    @Transactional(readOnly = true)
    public GateState gateState(Long versionId) {
        return new GateState(
                findingRepository.countByVersionIdAndSeverityAndStatus(versionId,
                        PriceListValidationFinding.Severity.BLOCKER, PriceListValidationFinding.Status.OPEN),
                findingRepository.countByVersionIdAndSeverityAndStatus(versionId,
                        PriceListValidationFinding.Severity.WARNING, PriceListValidationFinding.Status.OPEN));
    }

    @Transactional(readOnly = true)
    public List<PriceListValidationFinding> findings(Long versionId) {
        return findingRepository.findByVersionIdOrderBySeverityAscIdAsc(versionId);
    }

    /**
     * (Re-)runs all checks for a DRAFT version. OPEN findings are regenerated;
     * RESOLVED/WAIVED ones are preserved (they carry audit value), and a
     * previously waived/resolved situation is not re-raised for the same
     * line+type.
     */
    @Transactional
    public GateState validate(Long versionId, String user) {
        PriceListVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionId));
        if (version.getStatus() != PriceListVersion.Status.DRAFT) {
            return gateState(versionId); // published artifacts are never re-validated
        }

        int spikeWarn = settings.getInt(ClassificationSettingsService.KEY_PRICE_SPIKE_WARN_PERCENT, 30);
        int spikeBlock = settings.getInt(ClassificationSettingsService.KEY_PRICE_SPIKE_BLOCK_PERCENT, 100);
        int costFactor = settings.getInt(ClassificationSettingsService.KEY_OUTLIER_CATALOG_COST_FACTOR, 5);
        int swingWarn = settings.getInt(ClassificationSettingsService.KEY_TOTAL_SWING_WARN_PERCENT, 25);

        // keep handled findings (audit), drop only OPEN ones before regeneration
        List<PriceListValidationFinding> existing = findingRepository
                .findByVersionIdOrderBySeverityAscIdAsc(versionId);
        Map<String, PriceListValidationFinding> handled = new HashMap<>();
        for (PriceListValidationFinding f : existing) {
            if (f.getStatus() != PriceListValidationFinding.Status.OPEN) {
                handled.put(f.getFindingType() + ":" + f.getLineRef(), f);
            }
        }
        findingRepository.deleteByVersionIdAndStatus(versionId, PriceListValidationFinding.Status.OPEN);

        // MC-4C: candidates come from import lines (IMPORT) or the draft's own
        // pre-materialized rows (PATCH/ROLLBACK) — one validation for both.
        List<VersionCandidateService.CandidateItem> candidates = candidateService.candidatesOf(version);

        // previous ACTIVE version's rows, keyed by service code and canonical name
        Map<String, ProviderContractPricingItem> previous = new HashMap<>();
        pricingItemRepository.findByContractIdAndActiveTrue(version.getContractId())
                .forEach(i -> {
                    if (i.getServiceCode() != null) previous.putIfAbsent("C:" + i.getServiceCode().trim().toUpperCase(), i);
                    if (i.getServiceName() != null) previous.putIfAbsent("N:" + canon(i.getServiceName()), i);
                });

        Map<Long, BigDecimal> serviceCosts = new HashMap<>();
        serviceRepository.findAllById(candidates.stream().map(VersionCandidateService.CandidateItem::serviceId)
                        .filter(java.util.Objects::nonNull).toList())
                .forEach(s -> {
                    if (s.getCost() != null) serviceCosts.put(s.getId(), s.getCost());
                });

        List<PriceListValidationFinding> out = new ArrayList<>();
        Map<String, BigDecimal> pricePerService = new HashMap<>();
        BigDecimal totalPrevMatched = BigDecimal.ZERO;
        BigDecimal matchedNewTotal = BigDecimal.ZERO;

        for (VersionCandidateService.CandidateItem item : candidates) {
            BigDecimal price = item.price();

            // 1) zero/negative — BLOCKER
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                add(out, handled, version, item.lineRef(), item.lineRefType(), "ZERO_OR_NEGATIVE_PRICE",
                        PriceListValidationFinding.Severity.BLOCKER,
                        null, price, null, null,
                        "سعر صفري/سالب للخدمة: " + item.name());
            }

            // 2) same service twice with a different price — BLOCKER
            String identity = item.serviceId() != null ? "S:" + item.serviceId()
                    : item.serviceCode() != null ? "C:" + item.serviceCode().trim().toUpperCase() : null;
            if (identity != null && price != null) {
                BigDecimal prior = pricePerService.putIfAbsent(identity, price);
                if (prior != null && prior.compareTo(price) != 0) {
                    add(out, handled, version, item.lineRef(), item.lineRefType(), "DUPLICATE_PRICE_CONFLICT",
                            PriceListValidationFinding.Severity.BLOCKER,
                            prior, price, null, null,
                            "نفس الخدمة وردت بسعرين مختلفين: " + item.name()
                                    + " (" + prior + " ≠ " + price + ")");
                }
            }

            // 3) spike/drop vs previous ACTIVE version — WARNING / BLOCKER
            ProviderContractPricingItem prev = matchPrevious(previous, item);
            if (prev != null && prev.getContractPrice() != null
                    && prev.getContractPrice().compareTo(BigDecimal.ZERO) > 0 && price != null) {
                totalPrevMatched = totalPrevMatched.add(prev.getContractPrice());
                matchedNewTotal = matchedNewTotal.add(price);
                BigDecimal changePct = price.subtract(prev.getContractPrice())
                        .divide(prev.getContractPrice(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                double abs = Math.abs(changePct.doubleValue());
                if (abs > spikeBlock) {
                    add(out, handled, version, item.lineRef(), item.lineRefType(),
                            changePct.signum() >= 0 ? "PRICE_SPIKE_VS_PREVIOUS" : "PRICE_DROP_VS_PREVIOUS",
                            PriceListValidationFinding.Severity.BLOCKER,
                            prev.getContractPrice(), price, changePct, null,
                            "تغير سعري حاد " + changePct + "% عن النسخة السابقة: " + item.name());
                } else if (abs > spikeWarn) {
                    add(out, handled, version, item.lineRef(), item.lineRefType(),
                            changePct.signum() >= 0 ? "PRICE_SPIKE_VS_PREVIOUS" : "PRICE_DROP_VS_PREVIOUS",
                            PriceListValidationFinding.Severity.WARNING,
                            prev.getContractPrice(), price, changePct, null,
                            "تغير سعري " + changePct + "% عن النسخة السابقة: " + item.name());
                }
            }

            // 4) outlier vs catalog cost — WARNING (skipped when catalog cost unset)
            BigDecimal cost = item.serviceId() != null ? serviceCosts.get(item.serviceId()) : null;
            if (cost != null && cost.compareTo(BigDecimal.ZERO) > 0 && price != null) {
                if (price.compareTo(cost.multiply(BigDecimal.valueOf(costFactor))) > 0
                        || price.multiply(BigDecimal.TEN).compareTo(cost) < 0) {
                    add(out, handled, version, item.lineRef(), item.lineRefType(), "OUTLIER_VS_CATALOG_COST",
                            PriceListValidationFinding.Severity.WARNING,
                            null, price, null, cost,
                            "السعر شاذ مقارنة بتكلفة الكتالوج (" + cost + "): " + item.name());
                }
            }
        }

        // 5) total-value swing vs previous version (aggregate) — WARNING
        if (totalPrevMatched.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal swing = matchedNewTotal.subtract(totalPrevMatched)
                    .divide(totalPrevMatched, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            if (Math.abs(swing.doubleValue()) > swingWarn) {
                add(out, handled, version, null, null, "TOTAL_VALUE_SWING",
                        PriceListValidationFinding.Severity.WARNING,
                        totalPrevMatched, matchedNewTotal, swing, null,
                        "تأرجح القيمة الإجمالية للخدمات المشتركة " + swing + "% عن النسخة السابقة");
            }
        }

        findingRepository.saveAll(out);
        GateState gate = gateState(versionId);
        log.info("[MCE] Validation of version #{} by {}: {} new findings (blockers={}, warnings={}), gate {}",
                versionId, user, out.size(), gate.getOpenBlockers(), gate.getOpenWarnings(),
                gate.isOpen() ? "OPEN" : "CLOSED");
        return gate;
    }

    @Transactional
    public PriceListValidationFinding resolve(Long versionId, Long findingId, String note, String user) {
        PriceListValidationFinding f = getOpenFinding(versionId, findingId);
        f.setStatus(PriceListValidationFinding.Status.RESOLVED);
        f.setResolvedBy(user);
        f.setResolvedAt(LocalDateTime.now());
        f.setWaiverNote(note);
        return findingRepository.save(f);
    }

    /** WAIVE is a financial act: WARNING only (blockers can never be waived), note required. */
    @Transactional
    public PriceListValidationFinding waive(Long versionId, Long findingId, String note, String user) {
        PriceListValidationFinding f = getOpenFinding(versionId, findingId);
        if (f.getSeverity() == PriceListValidationFinding.Severity.BLOCKER) {
            throw new BusinessRuleException("الموانع (BLOCKER) لا تُعفى — يجب معالجة السطر نفسه");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("الإعفاء يتطلب سببًا موثقًا");
        }
        f.setStatus(PriceListValidationFinding.Status.WAIVED);
        f.setResolvedBy(user);
        f.setResolvedAt(LocalDateTime.now());
        f.setWaiverNote(note);
        log.info("[MCE] Finding #{} WAIVED by {}: {}", findingId, user, note);
        return findingRepository.save(f);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private ProviderContractPricingItem matchPrevious(
            Map<String, ProviderContractPricingItem> previous,
            VersionCandidateService.CandidateItem item) {
        ProviderContractPricingItem prev = null;
        if (item.serviceCode() != null) {
            prev = previous.get("C:" + item.serviceCode().trim().toUpperCase());
        }
        if (prev == null && item.name() != null) {
            prev = previous.get("N:" + canon(item.name()));
        }
        return prev;
    }

    private PriceListValidationFinding getOpenFinding(Long versionId, Long findingId) {
        PriceListValidationFinding f = findingRepository.findById(findingId)
                .filter(x -> versionId.equals(x.getVersionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Finding not found: " + findingId));
        if (f.getStatus() != PriceListValidationFinding.Status.OPEN) {
            throw new BusinessRuleException("هذه الملاحظة معالجة مسبقًا (" + f.getStatus() + ")");
        }
        return f;
    }

    private void add(List<PriceListValidationFinding> out,
                     Map<String, PriceListValidationFinding> handled,
                     PriceListVersion version, Long lineRef, String lineRefType, String type,
                     PriceListValidationFinding.Severity severity,
                     BigDecimal oldPrice, BigDecimal newPrice, BigDecimal changePct,
                     BigDecimal referenceValue, String message) {
        if (handled.containsKey(type + ":" + lineRef)) {
            return; // already resolved/waived in a previous run — keep the audit, don't re-raise
        }
        out.add(PriceListValidationFinding.builder()
                .versionId(version.getId())
                .importId(version.getSourceImportId())
                .lineRef(lineRef)
                .lineRefType(lineRef == null ? null : lineRefType)
                .findingType(type)
                .severity(severity)
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .changePercent(changePct)
                .referenceValue(referenceValue)
                .message(message)
                .build());
    }

    private static String canon(String s) {
        return com.waad.tba.modules.medicalclassification.engine.service.ArabicTextCanonicalizer
                .canonicalize(s);
    }
}
