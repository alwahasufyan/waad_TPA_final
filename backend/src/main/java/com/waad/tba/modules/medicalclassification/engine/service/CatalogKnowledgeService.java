package com.waad.tba.modules.medicalclassification.engine.service;

import com.waad.tba.modules.medicalclassification.entity.CatalogClassificationHistory;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import com.waad.tba.modules.medicalclassification.repository.CatalogClassificationHistoryRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.entity.ServiceAlias;
import com.waad.tba.modules.medicaltaxonomy.enums.MedicalServiceStatus;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.ServiceAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The LEARNING LOOP of the Medical Classification Workspace (owner directive,
 * MC-2): every reviewer decision must add knowledge to the system.
 *
 * On each approval this service:
 *  1. links (or deliberately creates — A6) the catalog {@link MedicalService},
 *  2. records the provider's raw wording as service aliases,
 *  3. writes a {@link CatalogClassificationHistory} audit row.
 *
 * {@link ImportProcessingService} consults the same knowledge before banding,
 * so wording learned today is auto-recognized in tomorrow's imports — the
 * review queue shrinks with every provider onboarded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogKnowledgeService {

    public static final String ALIAS_SOURCE_REVIEWER = "REVIEWER_DECISION";

    private final MedicalServiceRepository serviceRepository;
    private final ServiceAliasRepository aliasRepository;
    private final CatalogClassificationHistoryRepository historyRepository;

    /** canonical text → service id (services' names + all aliases). */
    private final AtomicReference<Map<String, Long>> knowledgeIndex = new AtomicReference<>();

    @Value
    public static class KnowledgeResult {
        Long serviceId;
        boolean serviceCreated;
        int aliasesAdded;
    }

    /** Lookup by learned knowledge (names + aliases), canonicalized. */
    @Transactional(readOnly = true)
    public Optional<Long> findServiceIdByText(String rawName, String rawNameAlt) {
        Map<String, Long> idx = index();
        String k1 = ArabicTextCanonicalizer.canonicalize(rawName);
        if (!k1.isEmpty() && idx.containsKey(k1)) {
            return Optional.of(idx.get(k1));
        }
        String k2 = ArabicTextCanonicalizer.canonicalize(rawNameAlt);
        if (!k2.isEmpty() && idx.containsKey(k2)) {
            return Optional.of(idx.get(k2));
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<MedicalService> getService(Long id) {
        return id == null ? Optional.empty() : serviceRepository.findById(id);
    }

    /**
     * Records an approval decision into the catalog knowledge.
     *
     * @param explicitServiceId reviewer-chosen catalog service (nullable —
     *                          null means find by knowledge or create, A6)
     */
    @Transactional
    public KnowledgeResult recordApproval(PriceListImportLine line, Long categoryId,
                                          Long explicitServiceId, String reviewer) {
        MedicalService service;
        boolean created = false;

        if (explicitServiceId != null) {
            service = serviceRepository.findById(explicitServiceId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Catalog service not found: " + explicitServiceId));
        } else {
            Long known = findServiceIdByText(line.getRawName(), line.getRawNameAlt()).orElse(null);
            if (known != null) {
                service = serviceRepository.findById(known).orElseThrow();
            } else {
                // A6: deliberate creation at approval time — never at import time
                service = MedicalService.builder()
                        .code(generateCode())
                        .name(truncate(line.getRawName(), 200))
                        .nameAr(truncate(line.getRawName(), 255))
                        .nameEn(isMostlyLatin(line.getRawNameAlt())
                                ? truncate(line.getRawNameAlt(), 255) : null)
                        .categoryId(categoryId)
                        .status(MedicalServiceStatus.ACTIVE)
                        .active(true)
                        .isMaster(false)
                        .requiresPA(false)
                        .build();
                service = serviceRepository.save(service);
                created = true;
            }
        }

        // Category learning: fill a missing category, and audit any change
        Long oldCategory = service.getCategoryId();
        if (categoryId != null && !categoryId.equals(oldCategory)) {
            if (oldCategory == null) {
                service.setCategoryId(categoryId);
                serviceRepository.save(service);
            }
            // if oldCategory differs and is non-null we keep the catalog value
            // (catalog is the source of truth) but still audit the reviewer's view
        }
        historyRepository.save(CatalogClassificationHistory.builder()
                .medicalServiceId(service.getId())
                .categoryIdOld(oldCategory)
                .categoryIdNew(categoryId != null ? categoryId : oldCategory)
                .changeSource("IMPORT_REVIEW")
                .importLineId(line.getId())
                .confidenceAtDecision(line.getConfidenceScore())
                .changedBy(reviewer)
                .build());

        int aliasesAdded = addAliasIfNew(service.getId(), line.getRawName(), reviewer)
                + addAliasIfNew(service.getId(), line.getRawNameAlt(), reviewer);
        if (aliasesAdded > 0 || created) {
            knowledgeIndex.set(null); // new knowledge → rebuild on next lookup
        }
        return new KnowledgeResult(service.getId(), created, aliasesAdded);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private int addAliasIfNew(Long serviceId, String text, String reviewer) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String canonical = ArabicTextCanonicalizer.canonicalize(text);
        if (canonical.isEmpty() || index().containsKey(canonical)) {
            return 0; // already known wording (for any service) — no duplicate noise
        }
        aliasRepository.save(ServiceAlias.builder()
                .medicalServiceId(serviceId)
                .aliasText(truncate(text.trim(), 255))
                .locale(isMostlyLatin(text) ? "en" : "ar")
                .createdBy(reviewer + " (" + ALIAS_SOURCE_REVIEWER + ")")
                .build());
        return 1;
    }

    private Map<String, Long> index() {
        Map<String, Long> idx = knowledgeIndex.get();
        if (idx == null) {
            idx = new HashMap<>();
            for (MedicalService s : serviceRepository.findAll()) {
                if (s.isDeleted()) {
                    continue;
                }
                put(idx, s.getName(), s.getId());
                put(idx, s.getNameAr(), s.getId());
                put(idx, s.getNameEn(), s.getId());
            }
            for (ServiceAlias a : aliasRepository.findAll()) {
                put(idx, a.getAliasText(), a.getMedicalServiceId());
            }
            knowledgeIndex.set(idx);
            log.info("[MCE] Knowledge index built: {} entries", idx.size());
        }
        return idx;
    }

    private static void put(Map<String, Long> idx, String text, Long id) {
        String key = ArabicTextCanonicalizer.canonicalize(text);
        if (!key.isEmpty()) {
            idx.putIfAbsent(key, id);
        }
    }

    private String generateCode() {
        for (int i = 0; i < 5; i++) {
            String code = "MCE-" + UUID.randomUUID().toString()
                    .substring(0, 8).toUpperCase(Locale.ROOT);
            if (serviceRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique service code");
    }

    private static boolean isMostlyLatin(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        long latin = text.chars().filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')).count();
        long arabic = text.chars().filter(c -> c >= 0x0621 && c <= 0x064A).count();
        return latin > arabic;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
