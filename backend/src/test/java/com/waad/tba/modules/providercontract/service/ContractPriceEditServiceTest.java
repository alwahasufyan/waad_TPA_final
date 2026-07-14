package com.waad.tba.modules.providercontract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.exception.ValidationException;
import com.waad.tba.modules.medicalclassification.engine.service.CatalogKnowledgeService;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceChangeAudit;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListVersion;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceChangeAuditRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListVersionRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.providercontract.dto.ContractPriceEditDtos.AddServiceRequest;
import com.waad.tba.modules.providercontract.dto.ContractPriceEditDtos.ClassificationCorrectionRequest;
import com.waad.tba.modules.providercontract.dto.ContractPriceEditDtos.DeactivateServiceRequest;
import com.waad.tba.modules.providercontract.dto.ContractPriceEditDtos.PriceCorrectionRequest;
import com.waad.tba.modules.providercontract.dto.ContractPriceEditDtos.ReactivateServiceRequest;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;

@ExtendWith(MockitoExtension.class)
class ContractPriceEditServiceTest {

    @Mock private ProviderContractRepository contractRepository;
    @Mock private ProviderContractPricingItemRepository pricingItemRepository;
    @Mock private PriceChangeAuditRepository auditRepository;
    @Mock private PriceListVersionRepository versionRepository;
    @Mock private MedicalCategoryRepository categoryRepository;
    @Mock private MedicalServiceRepository serviceRepository;
    @Mock private CatalogKnowledgeService knowledgeService;
    @InjectMocks private ContractPriceEditService service;

    private ProviderContract contract;
    private ProviderContractPricingItem item;

    @BeforeEach
    void setUp() {
        Provider provider = Provider.builder().id(9L).name("Provider").licenseNumber("P-9").build();
        contract = ProviderContract.builder().id(7L).provider(provider)
                .contractCode("PC-7").active(true)
                .status(ProviderContract.ContractStatus.ACTIVE).build();
        item = ProviderContractPricingItem.builder().id(11L).contract(contract)
                .serviceCode("LAB-1").serviceName("Lab test").categoryName("Lab")
                .basePrice(new BigDecimal("20.00")).contractPrice(new BigDecimal("12.00"))
                .currency("LYD").unit("service").active(true).versionId(3L).build();
        lenient().when(versionRepository.findByContractIdAndStatus(7L, PriceListVersion.Status.ACTIVE))
                .thenReturn(Optional.of(PriceListVersion.builder().id(3L).build()));
    }

    @Test
    void correction_changesCurrentPriceAndCapturesCompleteBeforeAfterAudit() {
        when(pricingItemRepository.findById(11L)).thenReturn(Optional.of(item));
        when(pricingItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.correctPrice(7L, 11L, new PriceCorrectionRequest(new BigDecimal("15.00"), "signed amendment"), "accountant");

        assertThat(item.getContractPrice()).isEqualByComparingTo("15.00");
        ArgumentCaptor<PriceChangeAudit> audit = ArgumentCaptor.forClass(PriceChangeAudit.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getProviderId()).isEqualTo(9L);
        assertThat(audit.getValue().getContractId()).isEqualTo(7L);
        assertThat(audit.getValue().getPricingItemId()).isEqualTo(11L);
        assertThat(audit.getValue().getChangeType()).isEqualTo(PriceChangeAudit.ChangeType.PRICE_CORRECTION);
        assertThat(audit.getValue().getReason()).isEqualTo("signed amendment");
        assertThat(audit.getValue().getChangedBy()).isEqualTo("accountant");
        assertThat(audit.getValue().getCreatedAt()).isNotNull();
        assertThat(audit.getValue().getOldPrice()).isEqualByComparingTo("12.00");
        assertThat(audit.getValue().getNewPrice()).isEqualByComparingTo("15.00");
        assertThat(audit.getValue().getBeforeState()).contains("price=12.00", "active=true", "serviceCode=LAB-1");
        assertThat(audit.getValue().getAfterState()).contains("price=15.00", "active=true", "serviceCode=LAB-1");
    }

    @Test
    void correction_withoutReasonDoesNotMutateOrAudit() {
        when(pricingItemRepository.findById(11L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.correctPrice(7L, 11L,
                new PriceCorrectionRequest(new BigDecimal("15.00"), " "), "accountant"))
                .isInstanceOf(ValidationException.class);

        assertThat(item.getContractPrice()).isEqualByComparingTo("12.00");
        verify(pricingItemRepository, never()).save(any());
        verify(auditRepository, never()).save(any());
    }

    @Test
    void auditFailurePropagatesSoTransactionalMutationCannotCommit() {
        when(pricingItemRepository.findById(11L)).thenReturn(Optional.of(item));
        when(pricingItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditRepository.save(any())).thenThrow(new IllegalStateException("audit unavailable"));

        assertThatThrownBy(() -> service.correctPrice(7L, 11L,
                new PriceCorrectionRequest(new BigDecimal("15.00"), "signed amendment"), "accountant"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
    }

    @Test
    void addDeactivateReactivateAndClassificationEachWriteTheExpectedAuditType() {
        MedicalCategory category = MedicalCategory.builder().id(4L).name("Laboratory").build();
        when(contractRepository.findById(7L)).thenReturn(Optional.of(contract));
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
        when(pricingItemRepository.save(any())).thenAnswer(invocation -> {
            ProviderContractPricingItem saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(12L);
            return saved;
        });

        ProviderContractPricingItem added = service.addService(7L,
                new AddServiceRequest("LAB-2", "Second lab", 4L, null,
                        new BigDecimal("18.00"), "new signed item"), "accountant");
        assertThat(added.getVersionId()).isEqualTo(3L);

        when(pricingItemRepository.findById(11L)).thenReturn(Optional.of(item));
        service.deactivateService(7L, 11L, new DeactivateServiceRequest("temporarily removed"), "accountant");
        assertThat(item.getActive()).isFalse();
        service.reactivateService(7L, 11L, new ReactivateServiceRequest("restored by amendment"), "accountant");
        assertThat(item.getActive()).isTrue();
        service.correctClassification(7L, 11L,
                new ClassificationCorrectionRequest("LAB-1A", "Lab test corrected", 4L, "medical correction"),
                "reviewer");

        ArgumentCaptor<PriceChangeAudit> audits = ArgumentCaptor.forClass(PriceChangeAudit.class);
        verify(auditRepository, org.mockito.Mockito.times(4)).save(audits.capture());
        assertThat(audits.getAllValues()).extracting(PriceChangeAudit::getChangeType).containsExactly(
                PriceChangeAudit.ChangeType.ADD_SERVICE,
                PriceChangeAudit.ChangeType.DEACTIVATE_SERVICE,
                PriceChangeAudit.ChangeType.REACTIVATE_SERVICE,
                PriceChangeAudit.ChangeType.CLASSIFICATION_CORRECTION);
        assertThat(audits.getAllValues()).allSatisfy(audit -> {
            assertThat(audit.getBeforeState()).isNotNull();
            assertThat(audit.getAfterState()).isNotBlank();
            assertThat(audit.getReason()).isNotBlank();
            assertThat(audit.getChangedBy()).isNotBlank();
            assertThat(audit.getProviderId()).isEqualTo(9L);
            assertThat(audit.getContractId()).isEqualTo(7L);
            assertThat(audit.getPricingItemId()).isNotNull();
        });
    }

    @Test
    void pricingItemUsesJpaOptimisticVersionForStaleUpdateProtection() throws Exception {
        Field field = ProviderContractPricingItem.class.getDeclaredField("rowVersion");
        assertThat(field.getAnnotation(jakarta.persistence.Version.class)).isNotNull();
    }
}
