package com.waad.tba.modules.benefitpolicy.dto;

import java.math.BigDecimal;
import java.util.List;

public record BenefitPolicyRuleImportPreviewDto(
        String fileHash,
        int totalRows,
        int validRows,
        int createCount,
        int updateCount,
        List<Row> rows,
        List<RowError> errors) {

    public boolean valid() {
        return errors == null || errors.isEmpty();
    }

    public record Row(
            int rowNumber,
            String categoryCode,
            String categoryName,
            Integer coveragePercent,
            BigDecimal amountLimit,
            Integer timesLimit,
            Integer waitingPeriodDays,
            boolean requiresPreApproval,
            String notes,
            String action) {
    }

    public record RowError(int rowNumber, String field, String messageAr) {
    }
}
