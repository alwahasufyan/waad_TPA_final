package com.waad.tba.modules.medicalclassification.pricelist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.waad.tba.modules.medicalclassification.engine.dto.ClassificationLineResult;
import com.waad.tba.modules.medicalclassification.engine.dto.ClassificationRequest;
import com.waad.tba.modules.medicalclassification.engine.dto.ClassificationResult;
import com.waad.tba.modules.medicalclassification.engine.service.CatalogKnowledgeService;
import com.waad.tba.modules.medicalclassification.engine.service.CategoryResolutionService;
import com.waad.tba.modules.medicalclassification.engine.service.ClassificationEngineClient;
import com.waad.tba.modules.medicalclassification.engine.service.ClassificationSettingsService;
import com.waad.tba.modules.medicalclassification.engine.service.ConfidenceDecisionEngine;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImport;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportLineRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Async classification job (MC-1): PROCESSING → engine (CLI, A1) → staging
 * lines with queue banding (A4/A5) → counters + provenance → CLASSIFIED.
 * Any failure marks the import FAILED with the reason — never partial data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportProcessingService {
    private final ConfidenceDecisionEngine confidenceEngine = new ConfidenceDecisionEngine();

    private final PriceListImportRepository importRepository;
    private final PriceListImportLineRepository lineRepository;
    private final ClassificationEngineClient engineClient;
    private final ClassificationSettingsService settings;
    private final CategoryResolutionService categoryResolution;
    private final CatalogKnowledgeService knowledge;
    private final ObjectMapper objectMapper;
    private final ProviderContractPricingItemRepository pricingItemRepository;

    @Async
    public void processAsync(Long importId) {
        try {
            process(importId);
        } catch (Exception e) {
            log.error("[MCE] Import #{} failed: {}", importId, e.getMessage(), e);
            markFailed(importId, e.getMessage());
        }
    }

    @Transactional
    public void process(Long importId) {
        PriceListImport imp = importRepository.findById(importId).orElseThrow();
        if (imp.getStatus() == PriceListImport.Status.CANCELLED) {
            return; // cancelled before processing started
        }
        imp.setStatus(PriceListImport.Status.PROCESSING);
        importRepository.saveAndFlush(imp);

        int minScore = settings.getInt(ClassificationSettingsService.KEY_HIGH_CONFIDENCE_MIN_SCORE, 85);

        ClassificationResult result = engineClient.classify(ClassificationRequest.builder()
                .channel(ClassificationRequest.Channel.PRICE_LIST)
                .inputFile(Path.of(imp.getFileStoragePath()).toAbsolutePath().toString())
                .threshold((double) minScore)
                .hint(imp.getProviderTypeHint())
                .build());

        // idempotent re-run safety: clear any partial lines of a previous attempt
        lineRepository.deleteByImportId(importId);

        // ── duplicate detection within the file (guard → NEEDS_REVIEW) ──────
        Map<String, Integer> nameCounts = new HashMap<>();
        for (ClassificationLineResult line : result.getLines()) {
            nameCounts.merge(dupKey(line), 1, Integer::sum);
        }

        int known = 0, unknown = 0, lowConfidence = 0, duplicates = 0;
        List<PriceListImportLine> lines = new ArrayList<>(result.getLines().size());

        int knowledgeHits = 0;
        for (ClassificationLineResult line : result.getLines()) {
            List<String> flags = new ArrayList<>();
            String reason = line.getReason();
            boolean isDuplicate = nameCounts.getOrDefault(dupKey(line), 0) > 1;
            if (isDuplicate) {
                flags.add("DUPLICATE_IN_FILE");
                duplicates++;
            }
            boolean existingInContract = imp.getContractId() != null
                    && imp.getUploadMode() == PriceListImport.UploadMode.APPEND_NEW_SERVICES
                    && ((line.getServiceCode() != null && !line.getServiceCode().isBlank()
                        && pricingItemRepository.findByContractIdAndServiceCodeActiveTrue(imp.getContractId(), line.getServiceCode()).isPresent())
                        || (line.getRawName() != null && !line.getRawName().isBlank()
                        && pricingItemRepository.findByContractIdAndServiceNameActiveTrue(imp.getContractId(), line.getRawName()).isPresent()));
            if (existingInContract) {
                flags.add("EXISTING_CONTRACT_SERVICE");
                duplicates++;
            }
            boolean invalidPrice = line.getPrice() == null;
            boolean badPrice = line.getPrice() != null
                    && line.getPrice().compareTo(BigDecimal.ZERO) <= 0;
            if (invalidPrice) {
                flags.add("INVALID_OR_MISSING_PRICE");
            } else if (badPrice) {
                flags.add("ZERO_OR_NEGATIVE_PRICE");
            }
            if (existingInContract) {
                reason = appendReason(reason, "الخدمة موجودة مسبقًا في عقد المرفق — تم منع إعادة الإضافة في وضع الخدمات الجديدة فقط");
            }

            // ── LEARNING LOOP (owner directive): wording approved by reviewers
            // in earlier imports is recognized here — those lines skip review.
            Long knownServiceId = knowledge
                    .findServiceIdByText(line.getRawName(), line.getRawNameAlt())
                    .orElse(null);
            Long knownCategoryId = null;
            boolean engineTrusted = !line.isNeedsReview();
            String source = null;
            // The Python engine is authoritative for the classification band.
            // Preserve its trusted result even when no matching DB service has
            // been learned yet; otherwise every official-dictionary hit is
            // downgraded to NEEDS_REVIEW here.
            boolean scriptTrusted = !line.isNeedsReview() || isTrustedEngineStatus(line.getStatus());
            if (knownServiceId != null) {
                knownCategoryId = knowledge.getService(knownServiceId)
                        .map(s -> s.getCategoryId()).orElse(null);
                engineTrusted = true;
                source = "KNOWLEDGE_BASE";
                reason = "✔ معروف من قرارات مراجعة سابقة (قاموس وعد الطبي)";
                knowledgeHits++;
            }

            // ── WAAD category resolution — by NAME only, never by CAT-code
            // (the script's CAT numbering differs from WAAD's; see
            // CategoryResolutionService). Unresolved → must be reviewed.
            Long suggestedCategoryId = knownCategoryId != null
                    ? knownCategoryId
                    : categoryResolution.resolveCategoryId(line.getSubCategory()).orElse(null);
            if (suggestedCategoryId == null) {
                flags.add("CATEGORY_UNRESOLVED");
            }

            // Queue banding (§6): bands decide VISIBILITY only — approval is
            // always human (A4). Trusted + high score + no flags → hidden
            // majority (PENDING_BULK, approved later via "Approve Remaining").
            ConfidenceDecisionEngine.DecisionResult confidence = confidenceEngine.decide(
                    line, knownServiceId != null || scriptTrusted, existingInContract, false, false);
            if (source == null || source.isBlank()) {
                source = scriptTrusted ? inferEngineSource(line) : confidence.trustSource();
            }
            boolean highConfidence = scriptTrusted
                    || confidence.decision() == ConfidenceDecisionEngine.Decision.TRUSTED;
            PriceListImportLine.ReviewStatus band =
                    (highConfidence && flags.isEmpty())
                            ? PriceListImportLine.ReviewStatus.PENDING_BULK
                            : PriceListImportLine.ReviewStatus.NEEDS_REVIEW;

            if (invalidPrice) {
                String rawPrice = line.getRawPriceText() == null || line.getRawPriceText().isBlank()
                        ? "فارغ"
                        : line.getRawPriceText();
                reason = appendReason(reason,
                        "السعر غير رقمي أو يحتاج مراجعة يدوية: " + rawPrice);
            } else if (badPrice) {
                reason = appendReason(reason, "السعر صفر أو أقل من صفر ويحتاج مراجعة يدوية");
            }

            boolean noMatch = knownServiceId == null
                    && (line.getReferenceMatch() == null || line.getReferenceMatch().isBlank());
            if (line.isNeedsReview() && knownServiceId == null) {
                if (noMatch) unknown++; else lowConfidence++;
            } else {
                known++;
            }

            lines.add(PriceListImportLine.builder()
                    .importId(importId)
                    .rowNo(line.getRowNo())
                    .sourceSheet(line.getSourceSheet())
                    .rawName(line.getRawName() == null || line.getRawName().isBlank()
                            ? "(بدون اسم)" : line.getRawName())
                    .rawNameAlt(line.getRawNameAlt())
                    .rawCode(line.getServiceCode())
                    .rawPrice(line.getPrice())
                    .suggestedMainCategory(line.getMainCategory())
                    .coverageContext(line.getCoverageContext())
                    .suggestedSubLabel(line.getSubCategory())
                    .suggestedCategoryId(suggestedCategoryId)
                    .matchedServiceId(knownServiceId)
                    .confidenceScore(confidence.confidence())
                    .decisionLevel(PriceListImportLine.DecisionLevel.valueOf(confidence.decision().name()))
                    .evidenceJson(toEvidenceJson(confidence.evidence()))
                    .confidenceReason(confidence.reason())
                    .matchMethod(line.getMatchMethod())
                    .classificationSource(source)
                    .engineReason(reason)
                    .referenceMatch(line.getReferenceMatch())
                    .flags(flags.isEmpty() ? null : String.join(",", flags))
                    .reviewStatus(band)
                    .build());
        }
        if (knowledgeHits > 0) {
            log.info("[MCE] Import #{}: {} lines auto-recognized from the WAAD dictionary (learning loop)",
                    importId, knowledgeHits);
        }
        lineRepository.saveAll(lines);

        // ── counters + provenance (owner condition #2) ───────────────────────
        imp.setTotalLines(result.getTotalLines());
        imp.setKnownServices(known);
        imp.setUnknownServices(unknown);
        imp.setLowConfidence(lowConfidence);
        imp.setDuplicates(duplicates);
        imp.setEngineVersion(result.getEngineVersion());
        imp.setFuzzEngine(result.getFuzzEngine());
        imp.setExecutionMs(result.getExecutionMs());
        imp.setDictionaryVersion(writeJson(result.getKnowledge(), 1000));
        imp.setThresholdConfig(thresholdSnapshot(minScore, result));
        imp.setProcessedAt(LocalDateTime.now());
        imp.setStatus(PriceListImport.Status.CLASSIFIED);
        importRepository.save(imp);

        log.info("[MCE] Import #{} classified: total={}, pendingBulk={}, needsReview={}, "
                        + "unknown={}, lowConf={}, dupes={}, {}ms",
                importId, result.getTotalLines(),
                lines.stream().filter(l -> l.getReviewStatus() == PriceListImportLine.ReviewStatus.PENDING_BULK).count(),
                lines.stream().filter(l -> l.getReviewStatus() == PriceListImportLine.ReviewStatus.NEEDS_REVIEW).count(),
                unknown, lowConfidence, duplicates, result.getExecutionMs());
    }

    private void markFailed(Long importId, String reason) {
        try {
            importRepository.findById(importId).ifPresent(imp -> {
                imp.setStatus(PriceListImport.Status.FAILED);
                imp.setErrorMessage(toArabicFailure(reason));
                imp.setProcessedAt(LocalDateTime.now());
                importRepository.save(imp);
            });
        } catch (Exception e) {
            log.error("[MCE] Could not mark import #{} as FAILED: {}", importId, e.getMessage());
        }
    }

    /** Duplicate key: normalized primary+alt name (case/space-insensitive). */
    private static String toEvidenceJson(List<String> evidence) {
        return evidence == null ? "[]" : evidence.stream()
                .map(s -> "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static boolean isTrustedEngineStatus(String status) {
        if (status == null || status.isBlank()) return false;
        return status.contains("\u2714") || status.contains("\u0645\u0648\u062b\u0642")
                || "TRUSTED".equalsIgnoreCase(status.trim());
    }

    private static String inferEngineSource(ClassificationLineResult line) {
        String method = line.getMatchMethod() == null ? "" : line.getMatchMethod().toLowerCase();
        if (method.contains("exact") || method.contains("category") || method.contains("rule")) {
            return "WAAD_RULE";
        }
        return "OFFICIAL_KNOWLEDGE";
    }

    private static String dupKey(ClassificationLineResult line) {
        String a = line.getRawName() == null ? "" : line.getRawName();
        String b = line.getRawNameAlt() == null ? "" : line.getRawNameAlt();
        return (a + "||" + b).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String thresholdSnapshot(int minScore, ClassificationResult result) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(ClassificationSettingsService.KEY_HIGH_CONFIDENCE_MIN_SCORE, minScore);
        node.put("engine.threshold", result.getThreshold() == null ? minScore : result.getThreshold());
        node.put("review.auto_approval.enabled", settings.isAutoApprovalEnabled());
        return node.toString();
    }

    private String writeJson(Object value, int maxLen) {
        try {
            String json = objectMapper.writeValueAsString(value == null ? Map.of() : value);
            return json.length() <= maxLen ? json : json.substring(0, maxLen);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String appendReason(String current, String addition) {
        if (addition == null || addition.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return addition;
        }
        return current + " — " + addition;
    }

    private static String toArabicFailure(String reason) {
        if (reason == null || reason.isBlank()) {
            return "فشلت معالجة الملف. يرجى التحقق من صيغة الملف ثم إعادة المحاولة.";
        }
        if (reason.contains("Cannot deserialize")
                || reason.contains("BigDecimal")
                || reason.contains("not a valid representation")) {
            return "فشلت المعالجة لأن الملف يحتوي أسعارًا غير رقمية أو نطاقات أسعار مثل 550-650. تم إصلاح النظام ليحوّل هذه الصفوف إلى مراجعة بدل فشل الملف كاملًا. أعد رفع الملف.";
        }
        if (reason.contains("input_file not found") || reason.contains("No such file")) {
            return "فشلت المعالجة لأن ملف الرفع غير متاح داخل الخادم. أعد رفع الملف أو تحقق من التخزين.";
        }
        if (reason.contains("Engine I/O failure")) {
            return "فشل الاتصال بمحرك التصنيف أثناء معالجة الملف. تحقق من جاهزية المحرك ثم أعد المحاولة.";
        }
        return "فشلت معالجة الملف: " + reason;
    }
}
