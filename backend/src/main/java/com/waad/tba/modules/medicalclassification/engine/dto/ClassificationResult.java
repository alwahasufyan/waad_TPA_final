package com.waad.tba.modules.medicalclassification.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full result of one classification run (result.json from classify_json.py).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassificationResult {

    @JsonProperty("ok")
    private boolean ok;

    /** Present only when ok == false. */
    @JsonProperty("error")
    private String error;

    @JsonProperty("engine_version")
    private String engineVersion;

    @JsonProperty("fuzz_engine")
    private String fuzzEngine;

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("threshold")
    private Double threshold;

    @JsonProperty("hint")
    private String hint;

    /**
     * Knowledge provenance (MC-1 owner condition #2): sha256/size of the
     * reference, categories, synonyms and Odoo-KB files used for this run.
     */
    @JsonProperty("knowledge")
    private Map<String, Object> knowledge = new LinkedHashMap<>();

    /** Engine execution time in milliseconds. */
    @JsonProperty("execution_ms")
    private Long executionMs;

    /** The script's own Summary sheet as key → value (Arabic keys preserved). */
    @JsonProperty("summary")
    private Map<String, Object> summary = new LinkedHashMap<>();

    @JsonProperty("total_lines")
    private int totalLines;

    @JsonProperty("needs_review_count")
    private int needsReviewCount;

    @JsonProperty("lines")
    private List<ClassificationLineResult> lines = new ArrayList<>();
}
