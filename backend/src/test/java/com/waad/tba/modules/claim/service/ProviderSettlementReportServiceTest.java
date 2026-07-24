package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.modules.claim.dto.ProviderSettlementReportDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;

/**
 * CLAIMS-FINANCIAL-SOURCE-OF-TRUTH-1: proves the settlement report no longer
 * double-discounts. `claim.getNetProviderAmount()` is already net of the
 * provider's contract discount (applied once, at approval time) — the report
 * must display it as-is, not subtract the same discount percent again.
 */
@ExtendWith(MockitoExtension.class)
class ProviderSettlementReportServiceTest {

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private ProviderContractRepository contractRepository;

    @InjectMocks
    private ProviderSettlementReportService service;

    @Test
    void generateReport_doesNotDoubleDiscount_actualProviderShareEqualsNetProvider() {
        Provider provider = Provider.builder().id(1L).name("Test Provider").build();

        Claim claim = Claim.builder()
                .id(1151L)
                .providerId(1L)
                .status(ClaimStatus.APPROVED)
                .serviceDate(LocalDate.now())
                .requestedAmount(new BigDecimal("200"))
                .approvedAmount(new BigDecimal("135"))
                .netProviderAmount(new BigDecimal("135"))
                .patientCoPay(new BigDecimal("50"))
                .refusedAmount(BigDecimal.ZERO)
                .build();
        ClaimLine line = ClaimLine.builder()
                .id(29L)
                .claim(claim)
                .quantity(2)
                .unitPrice(new BigDecimal("100"))
                .requestedUnitPrice(new BigDecimal("100"))
                .requestedQuantity(2)
                .totalPrice(new BigDecimal("200"))
                .approvedAmount(new BigDecimal("135"))
                .refusedAmount(BigDecimal.ZERO)
                .build();
        claim.setLines(List.of(line));

        ProviderContract contract = ProviderContract.builder()
                .id(5L)
                .discountPercent(new BigDecimal("10"))
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(claimRepository.findForSettlementReport(eq(1L), any(), anyList(), any(), any()))
                .thenReturn(List.of(claim));
        when(contractRepository.findActiveContractByProvider(1L)).thenReturn(Optional.of(contract));

        ProviderSettlementReportDto report = service.generateReport(
                1L, null, null, null, null, null, null, null);

        // netProviderAmount (135) is already net of the 10% contract discount —
        // actualProviderShare must equal it exactly, not 135 * 0.9 = 121.5.
        assertThat(report.getNetProviderAmount()).isEqualByComparingTo(new BigDecimal("135"));
        assertThat(report.getActualProviderShare()).isEqualByComparingTo(new BigDecimal("135"));
        // Discount shown for information only, grossed back up: 135 / 0.9 = 150; 150-135=15.
        assertThat(report.getContractDiscountAmount()).isEqualByComparingTo(new BigDecimal("15"));
        assertThat(report.getContractDiscountPercent()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void generateReport_zeroDiscount_noDiscountAmountAndFullShare() {
        Provider provider = Provider.builder().id(2L).name("No Discount Provider").build();

        Claim claim = Claim.builder()
                .id(2000L)
                .providerId(2L)
                .status(ClaimStatus.APPROVED)
                .serviceDate(LocalDate.now())
                .requestedAmount(new BigDecimal("100"))
                .approvedAmount(new BigDecimal("80"))
                .netProviderAmount(new BigDecimal("80"))
                .patientCoPay(new BigDecimal("20"))
                .refusedAmount(BigDecimal.ZERO)
                .build();
        claim.setLines(List.of());

        when(providerRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(claimRepository.findForSettlementReport(eq(2L), any(), anyList(), any(), any()))
                .thenReturn(List.of(claim));
        when(contractRepository.findActiveContractByProvider(2L)).thenReturn(Optional.empty());

        ProviderSettlementReportDto report = service.generateReport(
                2L, null, null, null, null, null, null, null);

        assertThat(report.getActualProviderShare()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(report.getContractDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
