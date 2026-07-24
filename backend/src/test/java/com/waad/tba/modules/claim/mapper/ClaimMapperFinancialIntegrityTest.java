package com.waad.tba.modules.claim.mapper;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimUpdateDto;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.claim.service.CoverageEngineService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.provider.dto.EffectivePriceResponseDto;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CLAIMS-FINANCIAL-INTEGRITY-2 — regression tests for the discount/refusal
 * conflation and claim/line reconciliation bugs found on claim 901
 * (CLM-P001-000017):
 *
 * - a provider contract discount must never be persisted/treated as a refusal;
 * - patient copay must never be touched by the provider discount;
 * - companyShareBeforeDiscount and providerDiscountAmount must be persisted as
 *   their own explicit fields;
 * - the provider discount percent must be read dynamically from the active
 *   ProviderContract, never hardcoded;
 * - claim-level totals must be an exact sum of the (now-authoritative)
 *   per-line fields, so the claim/line/UI can never diverge again;
 * - a material per-line balance mismatch must block the save with a
 *   BusinessRuleException, not silently persist corrupted numbers.
 */
class ClaimMapperFinancialIntegrityTest {

    private final ProviderContractService providerContractService = mock(ProviderContractService.class);
    private final BenefitPolicyRepository benefitPolicyRepository = mock(BenefitPolicyRepository.class);
    private final MedicalCategoryRepository medicalCategoryRepository = mock(MedicalCategoryRepository.class);
    private final ProviderContractPricingItemRepository pricingItemRepository = mock(ProviderContractPricingItemRepository.class);
    private final ProviderContractRepository providerContractRepository = mock(ProviderContractRepository.class);
    private final ClaimBatchRepository claimBatchRepository = mock(ClaimBatchRepository.class);
    private final CoverageEngineService coverageEngineService = mock(CoverageEngineService.class);

    private final ClaimMapper mapper = new ClaimMapper(
            providerContractService, benefitPolicyRepository, medicalCategoryRepository,
            pricingItemRepository, providerContractRepository, claimBatchRepository, coverageEngineService);

    private Claim baseClaim() {
        Member member = Member.builder().id(5386L).build();
        return Claim.builder()
                .id(901L)
                .member(member)
                .providerId(1L)
                .serviceDate(LocalDate.now())
                .status(ClaimStatus.DRAFT)
                .fullCoverage(false)
                .build();
    }

    /** Requested 500.00, 80% coverage, no system/manual rejection — mirrors claim 901's real line. */
    private CoverageResult noCapResult() {
        return CoverageResult.builder()
                .serviceCode("1234")
                .serviceName("Voluptas et ipsa si")
                .effectiveUnitPrice(new BigDecimal("500.00"))
                .effectiveTotal(new BigDecimal("500.00"))
                .requestedTotal(new BigDecimal("500.00"))
                .coveragePercent(80)
                .systemRefusedAmount(BigDecimal.ZERO)
                .priceRefused(BigDecimal.ZERO)
                .limitRefused(BigDecimal.ZERO)
                .manualRefusedAmount(BigDecimal.ZERO)
                .build();
    }

    private void stubPricingResolution() {
        when(pricingItemRepository.findById(3L)).thenReturn(Optional.of(
                ProviderContractPricingItem.builder().id(3L).serviceCode("1234").contractPrice(new BigDecimal("500.00")).build()));
        when(providerContractService.getEffectivePrice(any(), any(), any())).thenReturn(
                EffectivePriceResponseDto.builder().hasContract(true).contractPrice(new BigDecimal("500.00")).pricingItemId(3L).build());
        when(coverageEngineService.evaluateLine(any(), any(), anyMap())).thenReturn(noCapResult());
    }

    private ClaimLineDto lineDto() {
        return ClaimLineDto.builder()
                .pricingItemId(3L)
                .serviceCode("1234")
                .quantity(1)
                .build();
    }

    private ProviderContract activeContractWithDiscount(String discountPercent) {
        return ProviderContract.builder()
                .id(1L)
                .discountPercent(new BigDecimal(discountPercent))
                .build();
    }

    @Test
    @DisplayName("7) 500/coverage80/copay20/discount10 -> patient=100, companyBeforeDiscount=400, discount=40, companyShare=360, refused=0")
    void fullWorkedExample_matchesExpectedBusinessFormula() {
        stubPricingResolution();
        when(providerContractRepository.findActiveContractByProvider(1L))
                .thenReturn(Optional.of(activeContractWithDiscount("10.00")));

        Claim claim = baseClaim();
        mapper.updateEntityFromDto(claim, ClaimUpdateDto.builder().lines(List.of(lineDto())).build(), null);

        ClaimLine line = claim.getLines().get(0);
        assertEquals(0, new BigDecimal("100.00").compareTo(line.getPatientShare()));
        assertEquals(0, new BigDecimal("400.00").compareTo(line.getCompanyShareBeforeDiscount()));
        assertEquals(0, new BigDecimal("40.00").compareTo(line.getProviderDiscountAmount()));
        assertEquals(0, new BigDecimal("360.00").compareTo(line.getCompanyShare()));
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getRefusedAmount()));

        // Claim-level totals must be the exact sum of the (only) line.
        assertEquals(0, new BigDecimal("100.00").compareTo(claim.getPatientCoPay()));
        assertEquals(0, new BigDecimal("360.00").compareTo(claim.getNetProviderAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(claim.getRefusedAmount()));
    }

    @Test
    @DisplayName("8) provider discount disabled/null -> providerDiscountAmount=0, companyShare=companyShareBeforeDiscount")
    void providerDiscountDisabled_zeroDiscountAmount() {
        stubPricingResolution();
        when(providerContractRepository.findActiveContractByProvider(1L)).thenReturn(Optional.empty());

        Claim claim = baseClaim();
        mapper.updateEntityFromDto(claim, ClaimUpdateDto.builder().lines(List.of(lineDto())).build(), null);

        ClaimLine line = claim.getLines().get(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getProviderDiscountAmount()));
        assertEquals(0, new BigDecimal("400.00").compareTo(line.getCompanyShareBeforeDiscount()));
        assertEquals(0, new BigDecimal("400.00").compareTo(line.getCompanyShare()));
    }

    @Test
    @DisplayName("9) provider discount 5% is read dynamically from the active contract, not hardcoded")
    void providerDiscountFivePercent_isDynamic() {
        stubPricingResolution();
        when(providerContractRepository.findActiveContractByProvider(1L))
                .thenReturn(Optional.of(activeContractWithDiscount("5.00")));

        Claim claim = baseClaim();
        mapper.updateEntityFromDto(claim, ClaimUpdateDto.builder().lines(List.of(lineDto())).build(), null);

        ClaimLine line = claim.getLines().get(0);
        // 400 * 5% = 20.00, companyShare = 400 - 20 = 380.00
        assertEquals(0, new BigDecimal("20.00").compareTo(line.getProviderDiscountAmount()));
        assertEquals(0, new BigDecimal("380.00").compareTo(line.getCompanyShare()));
    }

    @Test
    @DisplayName("12) claim-level totals for a new claim reconcile exactly with its lines")
    void claimLevelTotals_reconcileWithLines() {
        stubPricingResolution();
        when(providerContractRepository.findActiveContractByProvider(1L))
                .thenReturn(Optional.of(activeContractWithDiscount("10.00")));

        Claim claim = baseClaim();
        mapper.updateEntityFromDto(claim, ClaimUpdateDto.builder().lines(List.of(lineDto())).build(), null);

        ClaimLine line = claim.getLines().get(0);
        assertEquals(0, line.getCompanyShare().compareTo(claim.getNetProviderAmount()));
        assertEquals(0, line.getPatientShare().compareTo(claim.getPatientCoPay()));
        assertEquals(0, line.getRefusedAmount().compareTo(claim.getRefusedAmount()));
    }

    @Test
    @DisplayName("11) a material per-line balance mismatch throws BusinessRuleException instead of silently persisting")
    void materialBalanceMismatch_throwsBusinessRuleException() throws Exception {
        ClaimLine brokenLine = ClaimLine.builder()
                .serviceCode("1234")
                .serviceName("Broken line")
                .unitPrice(new BigDecimal("500.00"))
                .totalPrice(new BigDecimal("500.00"))
                .quantity(1)
                .requestedTotal(new BigDecimal("500.00"))
                .companyShareBeforeDiscount(new BigDecimal("400.00"))
                .providerDiscountAmount(new BigDecimal("40.00"))
                .refusedAmount(BigDecimal.ZERO)
                // Should be 360.00 (400 - 40 - 0) but is deliberately wrong by 50.00 — material.
                .companyShare(new BigDecimal("310.00"))
                .patientShare(new BigDecimal("100.00"))
                .build();

        Method validate = ClaimMapper.class.getDeclaredMethod("validateLineBalances", List.class);
        validate.setAccessible(true);

        Exception thrown = assertThrows(Exception.class, () -> {
            try {
                validate.invoke(mapper, List.of(brokenLine));
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (Exception) e.getCause();
            }
        });
        org.junit.jupiter.api.Assertions.assertInstanceOf(BusinessRuleException.class, thrown);
    }

    @Test
    @DisplayName("10) a tiny rounding-only diff (<= tolerance) is absorbed, not blocked")
    void tinyRoundingDiff_isAbsorbedNotBlocked() throws Exception {
        ClaimLine almostBalancedLine = ClaimLine.builder()
                .serviceCode("1234")
                .serviceName("Rounding-only line")
                .unitPrice(new BigDecimal("500.00"))
                .totalPrice(new BigDecimal("500.00"))
                .quantity(1)
                .requestedTotal(new BigDecimal("500.00"))
                .companyShareBeforeDiscount(new BigDecimal("400.00"))
                .providerDiscountAmount(new BigDecimal("40.00"))
                .refusedAmount(BigDecimal.ZERO)
                // Off by 0.01 — pure rounding, within the 0.02 tolerance.
                .companyShare(new BigDecimal("359.99"))
                .patientShare(new BigDecimal("100.00"))
                .build();

        Method validate = ClaimMapper.class.getDeclaredMethod("validateLineBalances", List.class);
        validate.setAccessible(true);
        validate.invoke(mapper, List.of(almostBalancedLine));

        // Absorbed into companyShare so the ledger reconciles exactly.
        assertEquals(0, new BigDecimal("360.00").compareTo(almostBalancedLine.getCompanyShare()));
    }
}
