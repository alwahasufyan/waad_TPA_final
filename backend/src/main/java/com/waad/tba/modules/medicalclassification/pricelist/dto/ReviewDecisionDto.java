package com.waad.tba.modules.medicalclassification.pricelist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One reviewer decision (MC-2). APPROVE requires a resolvable category
 * (explicit {@code categoryId} or the line's resolved engine suggestion);
 * {@code serviceId} optionally maps the line to an existing catalog service
 * instead of creating one (A6).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDecisionDto {

    public enum Action { APPROVE, REJECT }

    private Action action;

    /** WAAD medical_categories id (overrides the engine suggestion). */
    private Long categoryId;

    /** Existing catalog service to map to (null → find-or-create, A6). */
    private Long serviceId;

    /** Price override (defaults to the line's raw price). */
    private BigDecimal price;

    private String note;

    /** For bulk decisions only. */
    private List<Long> lineIds;
}
