package com.waad.tba.modules.medicalclassification.engine.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Transport-agnostic request to the Medical Classification Engine (A7).
 *
 * The {@code channel} tag exists from day one so future consumers
 * (OCR / CLAIM_TEXT / PHARMACY / API) reuse the identical contract —
 * only PRICE_LIST is implemented in Phase 1.
 */
@Value
@Builder
public class ClassificationRequest {

    public enum Channel { PRICE_LIST, OCR, CLAIM_TEXT, PHARMACY, API }

    @Builder.Default
    Channel channel = Channel.PRICE_LIST;

    /** Absolute path of the uploaded provider file (xlsx/xls/csv/pdf/pptx). */
    String inputFile;

    /** Optional reference catalog override; null = engine's bundled reference. */
    String reference;

    /** Optional match-acceptance threshold (0–100); null = engine default (85). */
    Double threshold;

    /** Optional facility-type hint: dental | optics | physio. */
    String hint;

    /** Prefix for generated codes of services without any code; null = "NEW". */
    String codePrefix;
}
