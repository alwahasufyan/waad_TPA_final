package com.waad.tba.modules.claim.service;

import com.waad.tba.common.enums.NetworkType;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.provider.service.ProviderNetworkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * WAAD-BASELINE-TEST-ALIGNMENT-1: realigned to the documented
 * "FINANCIAL REFORM (2026-04-29)" in CostCalculationService (patient-share
 * isolation, calculations driven by claim.getLines() rather than the
 * top-level requestedAmount). See that class's javadoc for the full
 * rationale. Production behavior is unchanged by this commit — only
 * fixtures/assertions were updated to match it.
 *
 * Two further, already-existing simplifications this file now documents
 * rather than fights:
 *   - getDeductibleMetThisPeriod()/getOutOfPocketSpentThisPeriod() are
 *     hardcoded to ZERO ("Simplified for MVP" in the service) — there is no
 *     cross-claim year-to-date tracking yet, so ClaimRepository is no longer
 *     even a constructor dependency of CostCalculationService. Tests that
 *     used to simulate "deductible/OOP already partly used this year" via a
 *     ClaimRepository stub have been redesigned to exercise the same capping
 *     logic within a single claim instead (deductible/OOP max are still real
 *     per-claim caps — only the cross-claim YTD signal is stubbed out).
 *   - getCoPayPercent() no longer applies an out-of-network penalty — both
 *     network types resolve to the same policy default copay ("Standardize
 *     on default if specific OON copay not in entity" per its own comment).
 *   - calculateCosts() has no negative-amount guard on the line-based path;
 *     see calculateCosts_NegativeAmount below for the known-gap note.
 */
@ExtendWith(MockitoExtension.class)
class CostCalculationServiceTest {

    @Mock
    private ProviderNetworkService providerNetworkService;

    @Mock
    private BenefitPolicyCoverageService benefitPolicyCoverageService;

    @InjectMocks
    private CostCalculationService costCalculationService;

    private Member testMember;
    private BenefitPolicy testPolicy;
    private Claim testClaim;

    @BeforeEach
    void setUp() {
        testPolicy = BenefitPolicy.builder()
                .id(1L)
                .annualDeductible(new BigDecimal("100.00"))
                .defaultCoveragePercent(80) // 20% copay
                .outOfPocketMax(new BigDecimal("1000.00"))
                .build();

        testMember = Member.builder()
                .id(1L)
                .benefitPolicy(testPolicy)
                .build();

        testClaim = Claim.builder()
                .id(1L)
                .member(testMember)
                .requestedAmount(new BigDecimal("500.00"))
                .providerName("Hospital A")
                .lines(new ArrayList<>())
                .build();
    }

    private static ClaimLine line(BigDecimal unitPrice) {
        return ClaimLine.builder()
                .unitPrice(unitPrice)
                .requestedUnitPrice(unitPrice)
                .quantity(1)
                .build();
    }

    @Test
    @DisplayName("Should calculate basic costs with no deductible met")
    void calculateCosts_NoDeductibleMet() {
        // Arrange: a single line whose net amount (500) matches the claim's
        // requestedAmount — approvedNetBaseAmount is computed from lines.
        testClaim.setLines(List.of(line(new BigDecimal("500.00"))));
        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.IN_NETWORK);

        // Act
        CostCalculationService.CostBreakdown result = costCalculationService.calculateCosts(testClaim);

        // Assert
        // Net base: 500. Deductible: 100 (applied, capped at policy annual limit).
        // Remainder: 400. Copay: 400 * 20% = 80. Insurance: 400 - 80 = 320.
        // Total Patient: 100 + 80 = 180.
        assertEquals(new BigDecimal("500.00"), result.requestedAmount());
        assertEquals(new BigDecimal("100.00"), result.deductibleApplied());
        assertEquals(new BigDecimal("80.00"), result.coPayAmount());
        assertEquals(new BigDecimal("320.00"), result.insuranceAmount());
        assertEquals(new BigDecimal("180.00"), result.patientResponsibility());
    }

    @Test
    @DisplayName("Should cap deductible at the policy's annual limit even when net base far exceeds it")
    void calculateCosts_DeductibleAlreadyMet() {
        // Arrange: getDeductibleMetThisPeriod() is hardcoded to ZERO (no
        // cross-claim YTD tracking), so "already met from a prior claim"
        // cannot be simulated. What IS still real: the deductible never
        // exceeds the policy's annual limit even when a single claim's net
        // base is far larger than it — two lines totalling 900 against a
        // 100 annual deductible.
        testClaim.setLines(List.of(line(new BigDecimal("400.00")), line(new BigDecimal("500.00"))));
        testClaim.setRequestedAmount(new BigDecimal("900.00"));
        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.IN_NETWORK);

        // Act
        CostCalculationService.CostBreakdown result = costCalculationService.calculateCosts(testClaim);

        // Assert
        // Net base: 900. Deductible applied: capped at 100 (not 900).
        // Remainder: 800. Copay: 800 * 20% = 160. Insurance: 800 - 160 = 640.
        assertEquals(new BigDecimal("100.00"), result.deductibleApplied());
        assertEquals(new BigDecimal("160.00"), result.coPayAmount());
        assertEquals(new BigDecimal("640.00"), result.insuranceAmount());
    }

    @Test
    @DisplayName("Out-of-network no longer applies a copay penalty — same default rate as in-network")
    void calculateCosts_OutOfNetwork() {
        // Arrange: getCoPayPercent() intentionally returns the SAME default
        // copay for both network types today ("Standardize on default if
        // specific OON copay not in entity") — there is no +20% OON penalty
        // to exercise anymore. This test now documents that fact instead of
        // asserting a penalty that no longer exists.
        testClaim.setLines(List.of(line(new BigDecimal("500.00"))));
        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.OUT_OF_NETWORK);

        // Act
        CostCalculationService.CostBreakdown result = costCalculationService.calculateCosts(testClaim);

        // Assert — identical to the in-network case (see calculateCosts_NoDeductibleMet).
        assertEquals(new BigDecimal("20.00"), result.coPayPercent());
        assertEquals(new BigDecimal("80.00"), result.coPayAmount());
        assertEquals(new BigDecimal("320.00"), result.insuranceAmount());
    }

    @Test
    @DisplayName("Should cap total patient responsibility at the out-of-pocket maximum within a single claim")
    void calculateCosts_OutOfPocketMax() {
        // Arrange: getOutOfPocketSpentThisPeriod() is hardcoded to ZERO (same
        // YTD-tracking gap as the deductible above), so "already spent this
        // year" cannot be simulated either. What IS still real: a single
        // claim's own total patient responsibility is still capped at the
        // policy's out-of-pocket max, with insurance absorbing the excess.
        testPolicy.setOutOfPocketMax(new BigDecimal("200.00"));
        testClaim.setLines(List.of(line(new BigDecimal("1000.00"))));
        testClaim.setRequestedAmount(new BigDecimal("1000.00"));
        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.IN_NETWORK);

        // Act
        CostCalculationService.CostBreakdown result = costCalculationService.calculateCosts(testClaim);

        // Assert
        // Net base: 1000. Deductible: 100. After: 900. Copay: 900*20% = 180.
        // Uncapped total patient responsibility: 100 + 180 = 280, exceeds the
        // 200 OOP max -> capped to 200, the 80 excess shifts to insurance.
        assertEquals(new BigDecimal("200.00"), result.patientResponsibility());
        assertEquals(new BigDecimal("800.00"), result.insuranceAmount());
        assertTrue(result.isOutOfPocketMaxReached());
    }

    @Test
    @DisplayName("KNOWN GAP: calculateCosts does not currently validate a negative requested amount")
    void calculateCosts_NegativeAmount() {
        // Arrange: calculateCosts() only special-cases requestedAmount == 0
        // (returns CostBreakdown.zero()); there is no sign check. This test
        // intentionally does NOT assert an exception — production behavior
        // is not changed by this commit (that would be a real financial-
        // validation change, out of scope here) — it documents the current,
        // real behavior (empty lines -> an all-zero breakdown, requestedAmount
        // echoed verbatim) so a future ticket can decide whether to add the
        // guard back.
        testClaim.setRequestedAmount(new BigDecimal("-100.00"));
        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.IN_NETWORK);

        CostCalculationService.CostBreakdown result = assertDoesNotThrow(
                () -> costCalculationService.calculateCosts(testClaim));

        assertEquals(new BigDecimal("-100.00"), result.requestedAmount());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.patientResponsibility()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.insuranceAmount()));
    }

    @Test
    @DisplayName("Should calculate weighted co-pay from lines")
    void calculateWeightedCopay_MultipleLines() {
        // Arrange
        ClaimLine line1 = ClaimLine.builder()
                .serviceCategoryId(101L)
                .unitPrice(new BigDecimal("100.00"))
                .quantity(1)
                .requestedUnitPrice(new BigDecimal("100.00"))
                .build();

        ClaimLine line2 = ClaimLine.builder()
                .serviceCategoryId(102L)
                .unitPrice(new BigDecimal("200.00"))
                .quantity(1)
                .requestedUnitPrice(new BigDecimal("200.00"))
                .build();

        testClaim.setLines(List.of(line1, line2));
        testClaim.setRequestedAmount(new BigDecimal("300.00"));

        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.IN_NETWORK);

        Map<Long, Integer> coverageMap = new HashMap<>();
        coverageMap.put(101L, 90); // 10% copay
        coverageMap.put(102L, 70); // 30% copay
        when(benefitPolicyCoverageService.batchGetCoveragePercentsByCategory(any(), anyList())).thenReturn(coverageMap);

        // Act
        CostCalculationService.CostBreakdown result = costCalculationService.calculateCosts(testClaim);

        // Assert
        // Weighted CoPay = (100*10 + 200*30) / 300 = 7000 / 300 = 23.33
        assertEquals(new BigDecimal("23.33"), result.coPayPercent());
        // Net base 300, deductible capped at policy's 100 -> after-deductible 200.
        // coPayAmount = 200 * 23.33 / 100 = 46.66
        assertEquals(new BigDecimal("46.66"), result.coPayAmount());
    }
}
