package com.waad.tba.modules.medicalclassification.pricelist.dto;

import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Read model of one staged line (MC-1: read-only; decisions arrive in MC-2). */
@Value
@Builder
public class PriceListImportLineDto {

    Long id;
    Integer rowNo;
    String sourceSheet;
    String rawName;
    String rawNameAlt;
    String rawCode;
    BigDecimal rawPrice;
    String suggestedMainCategory;
    String suggestedSubLabel;
    Long suggestedCategoryId;
    Long matchedServiceId;
    BigDecimal confidenceScore;
    String matchMethod;
    String classificationSource;
    String engineReason;
    String referenceMatch;
    String flags;
    String reviewStatus;
    Long finalServiceId;
    Long finalCategoryId;
    BigDecimal finalPrice;
    String approvedBy;
    String approvalMode;
    String reviewerNote;

    public static PriceListImportLineDto from(PriceListImportLine line) {
        return PriceListImportLineDto.builder()
                .id(line.getId())
                .rowNo(line.getRowNo())
                .sourceSheet(line.getSourceSheet())
                .rawName(line.getRawName())
                .rawNameAlt(line.getRawNameAlt())
                .rawCode(line.getRawCode())
                .rawPrice(line.getRawPrice())
                .suggestedMainCategory(line.getSuggestedMainCategory())
                .suggestedSubLabel(line.getSuggestedSubLabel())
                .suggestedCategoryId(line.getSuggestedCategoryId())
                .matchedServiceId(line.getMatchedServiceId())
                .confidenceScore(line.getConfidenceScore())
                .matchMethod(line.getMatchMethod())
                .classificationSource(line.getClassificationSource())
                .engineReason(line.getEngineReason())
                .referenceMatch(line.getReferenceMatch())
                .flags(line.getFlags())
                .reviewStatus(line.getReviewStatus() == null ? null : line.getReviewStatus().name())
                .finalServiceId(line.getFinalServiceId())
                .finalCategoryId(line.getFinalCategoryId())
                .finalPrice(line.getFinalPrice())
                .approvedBy(line.getApprovedBy())
                .approvalMode(line.getApprovalMode() == null ? null : line.getApprovalMode().name())
                .reviewerNote(line.getReviewerNote())
                .build();
    }
}
