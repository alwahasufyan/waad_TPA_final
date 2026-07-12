package com.waad.tba.modules.medicalclassification.engine.service;

import com.waad.tba.modules.medicalclassification.repository.ClassificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to classification_settings (seeded by V70).
 * Values are also snapshotted into each import's threshold_config (MC-1)
 * so every run is auditable against the config it actually used.
 */
@Service
@RequiredArgsConstructor
public class ClassificationSettingsService {

    public static final String KEY_PYTHON_PATH = "engine.python.path";
    public static final String KEY_SCRIPT_DIR = "engine.script.dir";
    public static final String KEY_TIMEOUT_SECONDS = "engine.timeout.seconds";
    public static final String KEY_HIGH_CONFIDENCE_MIN_SCORE = "queue.high_confidence.min_score";
    public static final String KEY_AUTO_APPROVAL_ENABLED = "review.auto_approval.enabled";
    public static final String KEY_PRICE_SPIKE_WARN_PERCENT = "validation.price_spike.warn_percent";
    public static final String KEY_PRICE_SPIKE_BLOCK_PERCENT = "validation.price_spike.block_percent";
    public static final String KEY_OUTLIER_CATALOG_COST_FACTOR = "validation.outlier.catalog_cost_factor";
    public static final String KEY_TOTAL_SWING_WARN_PERCENT = "validation.total_swing.warn_percent";

    private final ClassificationSettingRepository repository;

    @Value("${ENGINE_SCRIPT_DIR:}")
    private String engineScriptDirOverride;

    @Value("${ENGINE_PYTHON_PATH:}")
    private String enginePythonPathOverride;

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        String override = envOverrideFor(key);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return repository.findBySettingKey(key)
                .map(s -> s.getSettingValue() == null ? "" : s.getSettingValue().trim())
                .filter(v -> !v.isEmpty())
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    /**
     * A4 guard: auto-approval is hard-disabled in Phase 1 regardless of the
     * stored value. Kept as a method so the (future, separate) decision to
     * enable it is a single audited change.
     */
    public boolean isAutoApprovalEnabled() {
        return false;
    }

    private String envOverrideFor(String key) {
        if (KEY_SCRIPT_DIR.equals(key)) {
            return engineScriptDirOverride;
        }
        if (KEY_PYTHON_PATH.equals(key)) {
            return enginePythonPathOverride;
        }
        return null;
    }
}
