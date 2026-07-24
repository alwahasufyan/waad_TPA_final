package com.waad.tba.modules.claim.service;

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
import static org.mockito.Mockito.when;

/**
 * CLAIMS-FINANCIAL-INTEGRITY-2 — regression tests for Bug A:
 * {@code CoverageEngineService.computeUsage()} was treating a
 * {@code benefit_policy_rules.amount_limit} of {@code null}, {@code 0}, or
 * negative as a real, already-exhausted cap (only checking {@code != null}),
 * fabricating a full-line rejection with reason "تجاوز سقف المبلغ المسموح به"
 * with no real cap ever configured. Only a genuinely positive amountLimit may
 * ever be enforced.
 */
@ExtendWith(MockitoExtension.class)
class CoverageEngineServiceCapTest {

    @Mock
    private BenefitPolicyRuleService benefitPolicyRuleService;

    @InjectMocks
    private CoverageEngineService coverageEngineService;

    private BenefitPolicyRuleResponseDto rule(int coveragePercent) {
        return BenefitPolicyRuleResponseDto.builder()
                .id(30L)
                .effectiveCoveragePercent(coveragePercent)
                .requiresPreApproval(false)
                .medicalCategoryId(2801L)
                .build();
    }

    private ClaimLineInput line() {
        return ClaimLineInput.builder()
                .lineId("L-1")
                .serviceId(1234L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("500.00"))
                .contractPrice(new BigDecimal("500.00"))
                .build();
    }

    private BulkCoverageEngineRequest request() {
        return BulkCoverageEngineRequest.builder()
                .policyId(1L)
                .memberId(5386L)
                .serviceYear(2026)
                .lines(List.of(line()))
                .build();
    }

    private Map<String, Object> usageWithAmountLimit(Object amountLimit) {
        Map<String, Object> usage = new HashMap<>();
        usage.put("covered", true);
        usage.put("hasLimit", true);
        usage.put("ruleId", 30L);
        usage.put("timesLimit", null);
        usage.put("amountLimit", amountLimit);
        usage.put("usedCount", 0);
        usage.put("usedAmount", BigDecimal.ZERO);
        return usage;
    }

    @Test
    @DisplayName("1) amountLimit NULL must not trigger a cap refusal")
    void amountLimitNull_mustNotTriggerCapRefusal() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(rule(80)));
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usageWithAmountLimit(null));

        CoverageResult result = coverageEngineService.calculateBulk(request()).get(0);

        assertEquals(new BigDecimal("0.00"), result.getLimitRefused());
        assertNull(result.getRefusalReason());
    }

    @Test
    @DisplayName("2) amountLimit 0.00 must not trigger a cap refusal (this is CLAIM 901's exact bug)")
    void amountLimitZero_mustNotTriggerCapRefusal() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(rule(80)));
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usageWithAmountLimit(new BigDecimal("0.00")));

        CoverageResult result = coverageEngineService.calculateBulk(request()).get(0);

        assertEquals(new BigDecimal("0.00"), result.getLimitRefused());
        assertNull(result.getRefusalReason());
        // 80% coverage of the un-capped 500.00 requested total: company 400 + patient 100.
        assertEquals(new BigDecimal("400.00"), result.getCompanyShare());
        assertEquals(new BigDecimal("100.00"), result.getPatientShare());
    }

    @Test
    @DisplayName("3) amountLimit negative must not trigger a cap refusal")
    void amountLimitNegative_mustNotTriggerCapRefusal() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(rule(80)));
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usageWithAmountLimit(new BigDecimal("-10.00")));

        CoverageResult result = coverageEngineService.calculateBulk(request()).get(0);

        assertEquals(new BigDecimal("0.00"), result.getLimitRefused());
        assertNull(result.getRefusalReason());
    }

    @Test
    @DisplayName("4) amountLimit positive and exceeded must produce a real limit refusal")
    void amountLimitPositiveExceeded_mustProduceRealLimitRefusal() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(rule(80)));
        // amountLimit=200, already fully used -> the whole 500 line is a real cap excess.
        Map<String, Object> usage = usageWithAmountLimit(new BigDecimal("200.00"));
        usage.put("usedAmount", new BigDecimal("200.00"));
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usage);

        CoverageResult result = coverageEngineService.calculateBulk(request()).get(0);

        assertEquals(new BigDecimal("500.00"), result.getLimitRefused());
        assertEquals("تجاوز سقف المبلغ المسموح به", result.getRefusalReason());
        assertEquals(new BigDecimal("200.00"), result.getUsageDetails().getAmountLimit());
    }

    @Test
    @DisplayName("6) no cap at all (hasLimit=false) => no limitRefused and no cap rejection reason")
    void noCapAtAll_noLimitRefusedNoReason() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(rule(80)));
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("covered", true, "hasLimit", false));

        CoverageResult result = coverageEngineService.calculateBulk(request()).get(0);

        assertEquals(new BigDecimal("0.00"), result.getLimitRefused());
        assertNull(result.getRefusalReason());
        assertNull(result.getUsageDetails());
    }
}
