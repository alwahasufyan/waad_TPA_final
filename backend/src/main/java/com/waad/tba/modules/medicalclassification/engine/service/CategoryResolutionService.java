package com.waad.tba.modules.medicalclassification.engine.service;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the engine's suggested category LABEL to a WAAD MedicalCategory.
 *
 * ⚠️ NEVER by CAT-code: the script's approved list and WAAD's
 * medical_categories share the CAT0xx numbering but with DIFFERENT meanings
 * for several codes (verified: script CAT003 = العناية الفائقة, WAAD CAT003 =
 * الولادة القيصرية). Code-based mapping would silently misclassify —
 * a direct financial risk. Resolution is by canonicalized NAME equality only;
 * unresolved lines are forced into the review queue (CATEGORY_UNRESOLVED).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryResolutionService {

    private final MedicalCategoryRepository categoryRepository;

    /** canonical name → category id (rebuilt on demand). */
    private final AtomicReference<Map<String, Long>> cache = new AtomicReference<>();

    /**
     * @param engineLabel the engine's sub-category label, e.g. "CAT023 - رسوم الاخصائيين ..."
     */
    public Optional<Long> resolveCategoryId(String engineLabel) {
        if (engineLabel == null || engineLabel.isBlank()) {
            return Optional.empty();
        }
        String nameOnly = engineLabel;
        int sep = engineLabel.indexOf(" - ");
        if (sep > 0) {
            nameOnly = engineLabel.substring(sep + 3);
        }
        String key = ArabicTextCanonicalizer.canonicalize(nameOnly);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        Long id = index().get(key);
        return Optional.ofNullable(id);
    }

    /** Invalidate after category admin changes (cheap full rebuild on next use). */
    public void invalidate() {
        cache.set(null);
    }

    private Map<String, Long> index() {
        Map<String, Long> idx = cache.get();
        if (idx == null) {
            idx = new HashMap<>();
            for (MedicalCategory cat : categoryRepository.findAll()) {
                if (cat.isDeleted() || !cat.isActive()) {
                    continue;
                }
                put(idx, cat.getName(), cat.getId());
                put(idx, cat.getNameAr(), cat.getId());
            }
            cache.set(idx);
            log.info("[MCE] Category name index built: {} keys", idx.size());
        }
        return idx;
    }

    private static void put(Map<String, Long> idx, String name, Long id) {
        String key = ArabicTextCanonicalizer.canonicalize(name);
        if (!key.isEmpty()) {
            idx.putIfAbsent(key, id);
        }
    }
}
