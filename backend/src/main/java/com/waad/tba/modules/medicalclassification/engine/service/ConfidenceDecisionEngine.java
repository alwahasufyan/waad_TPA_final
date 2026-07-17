package com.waad.tba.modules.medicalclassification.engine.service;

import com.waad.tba.modules.medicalclassification.engine.dto.ClassificationLineResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Deterministic, extensible evidence scorer. AI is intentionally not included. */
public class ConfidenceDecisionEngine {
    public enum Decision { TRUSTED, REVIEW, UNRESOLVED }
    public record DecisionResult(BigDecimal confidence, Decision decision, List<String> evidence,
                                 String reason, String trustSource) {}

    public DecisionResult decide(ClassificationLineResult line, boolean approvedKnowledge,
                                 boolean providerHistorical, boolean facilityEvidence,
                                 boolean odooEvidence) {
        List<String> evidence = new ArrayList<>();
        double score = 0;
        String source = "NONE";
        boolean exact = "exact".equalsIgnoreCase(line.getMatchMethod())
                || "deterministic_exact".equalsIgnoreCase(line.getMatchMethod());
        // A deterministic exact rule is the only source allowed to dominate:
        // when it also carries a category code, it is safe to trust directly.
        if (exact) { score = 100; evidence.add("DETERMINISTIC_EXACT"); source = "WAAD_RULE"; }
        if ("LAB_EXACT".equalsIgnoreCase(line.getMatchMethod()) || "LAB_EXACT".equalsIgnoreCase(line.getStatus())) {
            score += 35; evidence.add("LAB_EXACT"); source = "LAB_EXACT";
        }
        if (approvedKnowledge) { score += 30; evidence.add("APPROVED_ALIAS"); source = "APPROVED_ALIAS"; }
        if (line.getReferenceMatch()!=null && !line.getReferenceMatch().isBlank()) { score += 15; evidence.add("REFERENCE_MATCH"); }
        if (line.getConfidence()!=null) { score += Math.min(25, line.getConfidence().doubleValue()*0.25); evidence.add("ENGINE_SCORE:"+line.getConfidence()); }
        if (providerHistorical) { score += 8; evidence.add("PROVIDER_HISTORY"); }
        if (facilityEvidence) { score += 5; evidence.add("FACILITY_DATASET"); }
        if (odooEvidence) { score += 5; evidence.add("ODOO_DATASET"); }
        if (line.isNeedsReview() && !exact) score = Math.min(score, 84);
        if (line.getSubCategoryCode()==null || line.getSubCategoryCode().isBlank()) score = Math.min(score, 69);
        score = Math.min(100, Math.max(0, score));
        Decision d = score >= 85 ? Decision.TRUSTED : score >= 70 ? Decision.REVIEW : Decision.UNRESOLVED;
        String reason = evidence.isEmpty() ? "لا توجد أدلة كافية" : "النتيجة مبنية على: " + String.join(", ", evidence);
        return new DecisionResult(BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP), d, evidence, reason, source);
    }
}
