package com.waad.tba.modules.claim.mapper;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimUpdateDto;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.claim.service.CoverageEngineService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.provider.dto.EffectivePriceResponseDto;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import org.junit.jupiter.api.Test;

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
 * PROVIDER-PORTAL-DATA-1 — regression tests for the two bugs found during the
 * Phase 3A/3B live smoke test:
 * 1. The requested amount was calculated from the frontend-supplied unitPrice
 *    (always 0 in practice, since the provider portal never sent one) instead
 *    of the backend-resolved, authoritative contract price.
 * 2. A claim line with no resolvable pricing source (no medicalServiceId, no
 *    pricingItemId, no free-text-allowed code) silently proceeded instead of
 *    being rejected with a specific Arabic message.
 */
class ClaimMapperPricingContractTest {

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
        Member member = Member.builder().id(5050L).build();
        return Claim.builder()
                .id(1L)
                .member(member)
                .providerId(1L)
                .serviceDate(LocalDate.now())
                .status(ClaimStatus.DRAFT)
                .fullCoverage(false)
                .build();
    }

    private CoverageResult fakeCoverageResult() {
        return CoverageResult.builder()
                .serviceCode("MCE-DF6C1000")
                .serviceName("طبيب")
                .effectiveUnitPrice(new BigDecimal("100.00"))
                .effectiveTotal(new BigDecimal("100.00"))
                .coveragePercent(80)
                .systemRefusedAmount(BigDecimal.ZERO)
                .build();
    }

    @Test
    void validPricingItem_resolvesAuthoritativeContractPrice_notFrontendZero() {
        when(providerContractRepository.findActiveContractByProvider(1L)).thenReturn(Optional.empty());
        when(pricingItemRepository.findById(2L)).thenReturn(Optional.of(
                ProviderContractPricingItem.builder().id(2L).serviceCode("MCE-DF6C1000").contractPrice(new BigDecimal("100.00")).build()));
        when(providerContractService.getEffectivePrice(any(), any(), any())).thenReturn(
                EffectivePriceResponseDto.builder().hasContract(true).contractPrice(new BigDecimal("100.00")).pricingItemId(2L).build());
        when(coverageEngineService.evaluateLine(any(), any(), anyMap())).thenReturn(fakeCoverageResult());

        Claim claim = baseClaim();
        ClaimLineDto line = ClaimLineDto.builder()
                .pricingItemId(2L)
                // Simulates the OLD, buggy frontend payload: no unitPrice sent at all.
                .unitPrice(null)
                .quantity(1)
                .build();

        mapper.updateEntityFromDto(claim, ClaimUpdateDto.builder().lines(List.of(line)).build(), null);

        // Must be 100.00 (the resolved contract price), NOT 0.00 (the old bug).
        assertEquals(0, new BigDecimal("100.00").compareTo(claim.getRequestedAmount()));
    }

    @Test
    void lineWithNoResolvablePricingSource_isRejectedWithArabicMessage() {
        when(providerContractRepository.findActiveContractByProvider(1L)).thenReturn(Optional.empty());

        Claim claim = baseClaim();
        ClaimLineDto line = ClaimLineDto.builder()
                .medicalServiceId(null)
                .pricingItemId(null)
                .serviceCode(null)
                .quantity(1)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> mapper.updateEntityFromDto(claim, ClaimUpdateDto.builder().lines(List.of(line)).build(), null));

        assertEquals("تعذر استخدام هذه الخدمة لأن ربطها بسعر العقد غير مكتمل. يرجى مراجعة مسؤول العقود أو اختيار خدمة أخرى.",
                ex.getMessageAr());
    }
}
