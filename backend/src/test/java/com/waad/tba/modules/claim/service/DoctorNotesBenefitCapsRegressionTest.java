package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleService;
import com.waad.tba.modules.claim.dto.engine.BulkCoverageEngineRequest;
import com.waad.tba.modules.claim.dto.engine.ClaimLineInput;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * DOCTOR-NOTES-BENEFIT-CAPS-REGRESSION-1 — reproduces the business scenarios
 * described in handwritten doctor's notes about benefit caps, coverage
 * percentages, copay, and category classification, against
 * {@link CoverageEngineService} (the line-level financial engine). Money-
 * amounts/category codes in the notes are made-up local fixtures, not real
 * production data.
 *
 * Each test targets ONE numbered scenario from the ticket. See
 * docs/claims/DOCTOR-NOTES-BENEFIT-CAPS-REGRESSION-1-REPORT.md for the full
 * writeup (which scenarios pass, which reveal gaps, and why).
 */
@ExtendWith(MockitoExtension.class)
class DoctorNotesBenefitCapsRegressionTest {

    @Mock
    private BenefitPolicyRuleService benefitPolicyRuleService;

    @Mock
    private MedicalAuditLogService medicalAuditLogService;

    @InjectMocks
    private CoverageEngineService coverageEngineService;

    private BenefitPolicyRuleResponseDto ruleWithCoverage(long categoryId, int coveragePercent) {
        return BenefitPolicyRuleResponseDto.builder()
                .id(categoryId)
                .effectiveCoveragePercent(coveragePercent)
                .requiresPreApproval(false)
                .medicalCategoryId(categoryId)
                .build();
    }

    private ClaimLineInput lineWithQuantity(long categoryId, BigDecimal unitPrice, int quantity) {
        return lineWithQuantity("L-1", categoryId, unitPrice, quantity);
    }

    private ClaimLineInput lineWithQuantity(String lineId, long categoryId, BigDecimal unitPrice, int quantity) {
        return ClaimLineInput.builder()
                .lineId(lineId)
                .serviceId(1L)
                .categoryId(categoryId)
                .serviceCategoryId(categoryId)
                .quantity(quantity)
                .enteredUnitPrice(unitPrice)
                .contractPrice(unitPrice)
                .build();
    }

    private BulkCoverageEngineRequest requestWithLine(ClaimLineInput line) {
        return BulkCoverageEngineRequest.builder()
                .policyId(1L)
                .memberId(9001L)
                .serviceYear(2026)
                .lines(List.of(line))
                .build();
    }

    private Map<String, Object> usage(BigDecimal amountLimit, BigDecimal usedAmount) {
        Map<String, Object> u = new HashMap<>();
        u.put("covered", true);
        u.put("hasLimit", true);
        u.put("ruleId", 1L);
        u.put("timesLimit", null);
        u.put("amountLimit", amountLimit);
        u.put("usedCount", 0);
        u.put("usedAmount", usedAmount == null ? BigDecimal.ZERO : usedAmount);
        return u;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2) MRI / رنين مغناطيسي cap not enforced
    // ═══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("mriCapExceeded_shouldPersistLimitRefusalAndSnapshots")
    void mriCapExceeded_shouldPersistLimitRefusalAndSnapshots() {
        long mriCategoryId = 3001L;
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(mriCategoryId, 80)));
        // cap = 1500, nothing used yet, line requests 2000 -> excess of 500 must be refused.
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usage(new BigDecimal("1500.00"), BigDecimal.ZERO));

        ClaimLineInput line = lineWithQuantity(mriCategoryId, new BigDecimal("2000.00"), 1);
        CoverageResult result = coverageEngineService.calculateBulk(requestWithLine(line)).get(0);

        assertEquals(new BigDecimal("500.00"), result.getLimitRefused());
        assertEquals(new BigDecimal("1500.00"), result.getUsageDetails().getAmountLimit());
        assertEquals(new BigDecimal("0.00"), result.getUsageDetails().getUsedAmount());
        assertEquals(new BigDecimal("0.00"), result.getUsageDetails().getRemainingAmount());
        assertEquals("تجاوز سقف المبلغ المسموح به", result.getRefusalReason());
        // Net payable must never exceed the cap: 80% of the capped 1500 = 1200.
        assertEquals(new BigDecimal("1200.00"), result.getCompanyShare());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4) Natural/C-section delivery cap not enforced
    // ═══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("deliveryCapExceeded_shouldApplyCategoryCap")
    void deliveryCapExceeded_shouldApplyCategoryCap() {
        long deliveryCategoryId = 3002L;
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(deliveryCategoryId, 100)));
        // cap = 4000, line requests 5500 -> excess of 1500 refused.
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usage(new BigDecimal("4000.00"), BigDecimal.ZERO));

        ClaimLineInput line = lineWithQuantity(deliveryCategoryId, new BigDecimal("5500.00"), 1);
        CoverageResult result = coverageEngineService.calculateBulk(requestWithLine(line)).get(0);

        assertEquals(new BigDecimal("1500.00"), result.getLimitRefused());
        assertEquals(new BigDecimal("4000.00"), result.getCompanyShare());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5) Physiotherapy session cap not enforced (accumulation across claims)
    // ═══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("physiotherapyCapAccumulation_shouldRejectExcessAcrossClaims")
    void physiotherapyCapAccumulation_shouldRejectExcessAcrossClaims() {
        long physioCategoryId = 3003L;
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(physioCategoryId, 80)));
        // cap = 10000, PRIOR claims already used 9200 (simulating checkUsageLimit's real
        // cross-claim DB aggregation) -> a new 1500 line only has 800 of headroom.
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usage(new BigDecimal("10000.00"), new BigDecimal("9200.00")));

        ClaimLineInput line = lineWithQuantity(physioCategoryId, new BigDecimal("1500.00"), 1);
        CoverageResult result = coverageEngineService.calculateBulk(requestWithLine(line)).get(0);

        // remaining = 10000 - 9200 = 800; requested 1500 -> excess of 700 refused.
        assertEquals(new BigDecimal("700.00"), result.getLimitRefused());
        assertEquals(new BigDecimal("800.00"), result.getUsageDetails().getRemainingAmount());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 7) Dental operations cap not enforced
    // ═══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("dentalOperationsCapExceeded_shouldRejectExcess")
    void dentalOperationsCapExceeded_shouldRejectExcess() {
        long dentalOpsCategoryId = 3004L;
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(dentalOpsCategoryId, 50)));
        // cap = 2000, line requests 3200 -> excess of 1200 refused.
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usage(new BigDecimal("2000.00"), BigDecimal.ZERO));

        ClaimLineInput line = lineWithQuantity(dentalOpsCategoryId, new BigDecimal("3200.00"), 1);
        CoverageResult result = coverageEngineService.calculateBulk(requestWithLine(line)).get(0);

        assertEquals(new BigDecimal("1200.00"), result.getLimitRefused());
        // 50% of the capped 2000 = 1000.
        assertEquals(new BigDecimal("1000.00"), result.getCompanyShare());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 9) Accommodation/inpatient vs surgery rules must not be conflated
    // ═══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("accommodationAndSurgery_shouldUseDifferentRules")
    void accommodationAndSurgery_shouldUseDifferentRules() {
        long accommodationCategoryId = 3005L;
        long surgeryCategoryId = 3006L;

        when(benefitPolicyRuleService.findCoverageForService(any(), eq(accommodationCategoryId), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(accommodationCategoryId, 100)));
        when(benefitPolicyRuleService.findCoverageForService(any(), eq(surgeryCategoryId), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(surgeryCategoryId, 50)));
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("covered", true, "hasLimit", false));

        ClaimLineInput accommodationLine = lineWithQuantity("L-ACC", accommodationCategoryId, new BigDecimal("1000.00"), 1);
        ClaimLineInput surgeryLine = lineWithQuantity("L-SUR", surgeryCategoryId, new BigDecimal("1000.00"), 1);

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(1L).memberId(9001L).serviceYear(2026)
                .lines(List.of(accommodationLine, surgeryLine))
                .build();
        List<CoverageResult> results = coverageEngineService.calculateBulk(request);

        CoverageResult accResult = results.stream().filter(r -> "L-ACC".equals(r.getLineId())).findFirst().orElseThrow();
        CoverageResult surResult = results.stream().filter(r -> "L-SUR".equals(r.getLineId())).findFirst().orElseThrow();

        // Accommodation at 100% must fully cover its own 1000; surgery at 50% must
        // only cover 500 of its own 1000 — the two categories' rules must never mix.
        assertEquals(new BigDecimal("1000.00"), accResult.getCompanyShare());
        assertEquals(new BigDecimal("500.00"), surResult.getCompanyShare());
        assertEquals(new BigDecimal("500.00"), surResult.getPatientShare());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 10) Admission days/quantity behavior
    // ═══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("admissionQuantityDays_shouldAffectRequestedTotal")
    void admissionQuantityDays_shouldAffectRequestedTotal() {
        long accommodationCategoryId = 3005L;
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(accommodationCategoryId, 100)));
        // cap = 4000; daily room price = 500, admitted for 5 days -> requested total
        // must be 2500 (500 x 5), computed BEFORE the cap/coverage math, not a
        // single day's 500.
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usage(new BigDecimal("4000.00"), BigDecimal.ZERO));

        ClaimLineInput line = lineWithQuantity(accommodationCategoryId, new BigDecimal("500.00"), 5);
        CoverageResult result = coverageEngineService.calculateBulk(requestWithLine(line)).get(0);

        assertEquals(new BigDecimal("2500.00"), result.getRequestedTotal());
        assertEquals(new BigDecimal("2500.00"), result.getEffectiveTotal());
        assertEquals(new BigDecimal("0.00"), result.getLimitRefused());
        assertEquals(new BigDecimal("2500.00"), result.getCompanyShare());
    }

    @Test
    @DisplayName("admissionQuantityDays_capAppliesToTotalNotSingleDay")
    void admissionQuantityDays_capAppliesToTotalNotSingleDay() {
        long accommodationCategoryId = 3005L;
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(accommodationCategoryId, 100)));
        // cap = 2000; daily room price = 500 x 6 days = 3000 total -> excess of 1000
        // must be refused, proving the cap is checked against the multiplied total,
        // not evaluated (and passed) once per individual day.
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usage(new BigDecimal("2000.00"), BigDecimal.ZERO));

        ClaimLineInput line = lineWithQuantity(accommodationCategoryId, new BigDecimal("500.00"), 6);
        CoverageResult result = coverageEngineService.calculateBulk(requestWithLine(line)).get(0);

        assertEquals(new BigDecimal("3000.00"), result.getRequestedTotal());
        assertEquals(new BigDecimal("1000.00"), result.getLimitRefused());
        assertEquals(new BigDecimal("2000.00"), result.getCompanyShare());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 12) Cap exhausted behavior
    // ═══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("exhaustedCap_shouldRefuseOrShiftExcess")
    void exhaustedCap_shouldRefuseOrShiftExcess() {
        long mriCategoryId = 3001L;
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(mriCategoryId, 80)));
        // cap = 1500, ALREADY fully used (1500 of 1500) -> a new line must be refused
        // in full, not silently approved because "remaining" underflowed past zero.
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usage(new BigDecimal("1500.00"), new BigDecimal("1500.00")));

        ClaimLineInput line = lineWithQuantity(mriCategoryId, new BigDecimal("800.00"), 1);
        CoverageResult result = coverageEngineService.calculateBulk(requestWithLine(line)).get(0);

        assertEquals(new BigDecimal("800.00"), result.getLimitRefused());
        assertEquals(new BigDecimal("0.00"), result.getCompanyShare());
        assertEquals(new BigDecimal("0.00"), result.getUsageDetails().getRemainingAmount());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 8) Dental service percentages not differentiated — architecturally
    // confirmed removed since V228 (BenefitPolicyRule is category-only).
    // This proves the CURRENT engine correctly differentiates by CATEGORY
    // (two dental sub-categories CAN carry different %), which is the finest
    // granularity the current architecture supports — it cannot differentiate
    // within a single category by individual service/procedure.
    // ═══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("dentalCoveragePercent_shouldDifferByCategory")
    void dentalCoveragePercent_shouldDifferByCategory() {
        long dentalRoutineCategoryId = 3007L;
        long dentalMajorCategoryId = 3008L;

        when(benefitPolicyRuleService.findCoverageForService(any(), eq(dentalRoutineCategoryId), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(dentalRoutineCategoryId, 75)));
        when(benefitPolicyRuleService.findCoverageForService(any(), eq(dentalMajorCategoryId), any(), any()))
                .thenReturn(Optional.of(ruleWithCoverage(dentalMajorCategoryId, 25)));
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("covered", true, "hasLimit", false));

        ClaimLineInput routineLine = lineWithQuantity("L-ROUTINE", dentalRoutineCategoryId, new BigDecimal("100.00"), 1);
        ClaimLineInput majorLine = lineWithQuantity("L-MAJOR", dentalMajorCategoryId, new BigDecimal("100.00"), 1);

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(1L).memberId(9001L).serviceYear(2026)
                .lines(List.of(routineLine, majorLine))
                .build();
        List<CoverageResult> results = coverageEngineService.calculateBulk(request);

        CoverageResult routineResult = results.stream().filter(r -> "L-ROUTINE".equals(r.getLineId())).findFirst().orElseThrow();
        CoverageResult majorResult = results.stream().filter(r -> "L-MAJOR".equals(r.getLineId())).findFirst().orElseThrow();

        assertEquals(new BigDecimal("75.00"), routineResult.getCompanyShare());
        assertEquals(new BigDecimal("25.00"), majorResult.getCompanyShare());
    }
}
