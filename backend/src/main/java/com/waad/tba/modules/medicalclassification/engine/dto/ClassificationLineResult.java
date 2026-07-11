package com.waad.tba.modules.medicalclassification.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One classified service line, exactly as produced by the authoritative
 * script's output (via classify_json.py — parity by construction, A9).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassificationLineResult {

    /** Position in the script's output order (matched first, review last). */
    @JsonProperty("row_no")
    private Integer rowNo;

    @JsonProperty("raw_name")
    private String rawName;

    @JsonProperty("raw_name_alt")
    private String rawNameAlt;

    /** Reference code, original provider code, or generated NEWxxxxx code. */
    @JsonProperty("service_code")
    private String serviceCode;

    @JsonProperty("price")
    private BigDecimal price;

    /** Main category label (إيواء / عيادات خارجية). */
    @JsonProperty("main_category")
    private String mainCategory;

    /** Approved sub-category label ("CAT0xx - ..."). */
    @JsonProperty("sub_category")
    private String subCategory;

    @JsonProperty("note")
    private String note;

    /** Script status text: "✔ موثق" or "⚠ راجِع التصنيف". */
    @JsonProperty("status")
    private String status;

    @JsonProperty("needs_review")
    private boolean needsReview;

    /** The script's human-readable reason string (kept for reviewers). */
    @JsonProperty("reason")
    private String reason;

    /** Confidence score 0–100 (null for trusted-category rows without price match). */
    @JsonProperty("confidence")
    private BigDecimal confidence;

    /** exact / synonym / fuzzy / category / none (script wording). */
    @JsonProperty("match_method")
    private String matchMethod;

    /** Matched reference name or closest suggestion. */
    @JsonProperty("reference_match")
    private String referenceMatch;

    /** Source sheet/page inside the uploaded file. */
    @JsonProperty("source_sheet")
    private String sourceSheet;

    /** Extracts the approved sub-category code ("CAT0xx") from the label. */
    public String getSubCategoryCode() {
        if (subCategory == null) {
            return null;
        }
        int sep = subCategory.indexOf(" - ");
        return sep > 0 ? subCategory.substring(0, sep).trim() : subCategory.trim();
    }
}
