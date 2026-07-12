package com.waad.tba.modules.medicalclassification.engine.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.exception.ValidationException;
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
import java.util.List;
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
    /** MC-4C add-service, linked to an existing catalog MedicalService. */
    public static final String ALIAS_SOURCE_ADD_SERVICE = "ADD_SERVICE";
    /** Admin-entered directly via the knowledge-inspection endpoints. */
    public static final String ALIAS_SOURCE_MANUAL = "MANUAL";

    private final MedicalServiceRepository serviceRepository;
    private final ServiceAliasRepository aliasRepository;
    private final CatalogClassificationHistoryRepository historyRepository;

    /** canonical text → service id (services' names + active aliases only). */
    private final AtomicReference<Map<String, Long>> knowledgeIndex = new AtomicReference<>();

    @Value
    public static class KnowledgeResult {
        Long serviceId;
        boolean serviceCreated;
        int aliasesAdded;
    }

    /** MC-6 Lite: view of one service's learned knowledge (for inspection endpoints). */
    @Value
    public static class ServiceKnowledgeView {
        Long serviceId;
        String code;
        String name;
        Long categoryId;
        List<ServiceAlias> aliases;
        long historyCount;
    }

    /** MC-6 Lite: "why would/did this text match?" inspection result. */
    @Value
    public static class MatchInspection {
        boolean matched;
        Long serviceId;
        String matchedCanonicalKey;
        String queryCanonicalKey;
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

        int aliasesAdded = addAliasIfNew(service.getId(), line.getRawName(), reviewer, ALIAS_SOURCE_REVIEWER)
                + addAliasIfNew(service.getId(), line.getRawNameAlt(), reviewer, ALIAS_SOURCE_REVIEWER);
        if (aliasesAdded > 0 || created) {
            knowledgeIndex.set(null); // new knowledge → rebuild on next lookup
        }
        return new KnowledgeResult(service.getId(), created, aliasesAdded);
    }

    /**
     * MC-6 Lite / MC-4C: records a direct "add service" decision that was
     * explicitly linked to an existing catalog {@link MedicalService}. This is
     * a deliberate human act (the reviewer/admin chose the catalog link in the
     * add-service dialog) so it is safe to feed the same learning loop as a
     * review approval — the provider's wording becomes a known alias and the
     * category gap is filled if the catalog service had none yet.
     *
     * Unlinked add-service calls (no catalog service chosen) must NOT call
     * this method — an ad-hoc provider-only line must never silently become
     * global catalog knowledge (owner directive: prevent dangerous learning).
     */
    @Transactional
    public KnowledgeResult recordAdminLink(Long serviceId, Long categoryId, String providerServiceName, String user) {
        MedicalService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical service not found: " + serviceId));

        Long oldCategory = service.getCategoryId();
        if (categoryId != null && oldCategory == null) {
            service.setCategoryId(categoryId);
            serviceRepository.save(service);
        }
        historyRepository.save(CatalogClassificationHistory.builder()
                .medicalServiceId(service.getId())
                .categoryIdOld(oldCategory)
                .categoryIdNew(categoryId != null ? categoryId : oldCategory)
                .changeSource("ADMIN")
                .changedBy(user)
                .build());

        int aliasesAdded = addAliasIfNew(service.getId(), providerServiceName, user, ALIAS_SOURCE_ADD_SERVICE);
        if (aliasesAdded > 0) {
            knowledgeIndex.set(null);
        }
        return new KnowledgeResult(service.getId(), false, aliasesAdded);
    }

    // ── MC-6 Lite: minimal knowledge-inspection surface ─────────────────────

    @Transactional(readOnly = true)
    public ServiceKnowledgeView viewService(Long serviceId) {
        MedicalService s = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical service not found: " + serviceId));
        List<ServiceAlias> aliases = aliasRepository.findByMedicalServiceId(serviceId);
        long historyCount = historyRepository.countByMedicalServiceId(serviceId);
        return new ServiceKnowledgeView(s.getId(), s.getCode(), s.getName(), s.getCategoryId(), aliases, historyCount);
    }

    @Transactional(readOnly = true)
    public MatchInspection inspectMatch(String rawName, String rawNameAlt) {
        String k1 = ArabicTextCanonicalizer.canonicalize(rawName);
        String k2 = ArabicTextCanonicalizer.canonicalize(rawNameAlt);
        Map<String, Long> idx = index();
        if (!k1.isEmpty() && idx.containsKey(k1)) {
            return new MatchInspection(true, idx.get(k1), k1, k1);
        }
        if (!k2.isEmpty() && idx.containsKey(k2)) {
            return new MatchInspection(true, idx.get(k2), k2, k1);
        }
        return new MatchInspection(false, null, null, k1);
    }

    /** Admin-entered alias (MANUAL source) — e.g. correcting a known typo without waiting for the next import. */
    @Transactional
    public ServiceAlias addManualAlias(Long serviceId, String aliasText, String locale, String user) {
        if (aliasText == null || aliasText.isBlank()) {
            throw new ValidationException("نص المرادف مطلوب");
        }
        serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical service not found: " + serviceId));
        String canonical = ArabicTextCanonicalizer.canonicalize(aliasText);
        Map<String, Long> idx = index();
        if (!canonical.isEmpty() && idx.containsKey(canonical) && !idx.get(canonical).equals(serviceId)) {
            throw new BusinessRuleException("هذه الصياغة مرتبطة بالفعل بخدمة أخرى في القاموس");
        }
        ServiceAlias alias = aliasRepository.save(ServiceAlias.builder()
                .medicalServiceId(serviceId)
                .aliasText(truncate(aliasText.trim(), 255))
                .locale(locale == null || locale.isBlank() ? (isMostlyLatin(aliasText) ? "en" : "ar") : locale)
                .source(ALIAS_SOURCE_MANUAL)
                .active(true)
                .createdBy(user)
                .build());
        knowledgeIndex.set(null);
        return alias;
    }

    /** Soft-disable an alias (kept for audit; stops feeding future auto-matching). */
    @Transactional
    public void deactivateAlias(Long aliasId, String user) {
        ServiceAlias alias = aliasRepository.findById(aliasId)
                .orElseThrow(() -> new ResourceNotFoundException("Alias not found: " + aliasId));
        alias.setActive(false);
        aliasRepository.save(alias);
        log.info("[MCE] Alias #{} ('{}') deactivated by {}", aliasId, alias.getAliasText(), user);
        knowledgeIndex.set(null);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private int addAliasIfNew(Long serviceId, String text, String reviewer, String source) {
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
                .source(source)
                .active(true)
                .createdBy(reviewer + " (" + source + ")")
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
            // MC-6 Lite: only ACTIVE aliases feed auto-matching — a deactivated
            // (bad/typo) alias stops recognizing that wording immediately.
            for (ServiceAlias a : aliasRepository.findByActiveTrue()) {
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
