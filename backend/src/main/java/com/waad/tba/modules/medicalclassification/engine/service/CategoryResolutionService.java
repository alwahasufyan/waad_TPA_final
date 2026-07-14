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
 * TAX-1 resolves only the official CAT-* taxonomy. The official code is the
 * primary key; canonical Arabic-name equality is a defensive fallback.
 * Legacy CAT0xx, context labels and BEN-* values can never resolve here.
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
        String nameOnly = engineLabel.trim();
        int sep = engineLabel.indexOf(" - ");
        if (sep > 0) {
            String code = engineLabel.substring(0, sep).trim();
            Optional<MedicalCategory> official = categoryRepository
                    .findByCodeAndClassificationEnabledTrueAndActiveTrueAndDeletedFalse(code);
            if (official.isPresent()) {
                return official.map(MedicalCategory::getId);
            }
            nameOnly = engineLabel.substring(sep + 3);
        } else if (nameOnly.startsWith("CAT-")) {
            return categoryRepository
                    .findByCodeAndClassificationEnabledTrueAndActiveTrueAndDeletedFalse(nameOnly)
                    .map(MedicalCategory::getId);
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
            for (MedicalCategory cat : categoryRepository
                    .findByClassificationEnabledTrueAndActiveTrueAndDeletedFalse()) {
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
