package com.waad.tba.modules.providercontract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.exception.ValidationException;
import com.waad.tba.modules.medicalclassification.engine.service.CatalogKnowledgeService;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceChangeAuditRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListVersionRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.providercontract.dto.ContractPriceEditDtos.AddServiceRequest;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;

/**
 * CLASSIFICATION-PRICE-LIST-FULL-STABILIZATION-1: protects the exact request
 * contract the frontend Add-Service dialog depends on (categoryId/price/reason
 * — not medicalCategoryId/basePrice/contractPrice/notes), so a future payload
 * drift regresses here instead of silently breaking every Add-Service submit.
 */
@ExtendWith(MockitoExtension.class)
class ContractPriceEditServiceAddServiceTest {

    @Mock
    private ProviderContractRepository contractRepository;
    @Mock
    private ProviderContractPricingItemRepository pricingItemRepository;
    @Mock
    private PriceChangeAuditRepository auditRepository;
    @Mock
    private PriceListVersionRepository versionRepository;
    @Mock
    private MedicalCategoryRepository categoryRepository;
    @Mock
    private MedicalServiceRepository serviceRepository;
    @Mock
    private CatalogKnowledgeService knowledgeService;

    private ContractPriceEditService service;

    private ProviderContract contract;
    private MedicalCategory category;

    @BeforeEach
    void setUp() {
        service = new ContractPriceEditService(contractRepository, pricingItemRepository, auditRepository,
                versionRepository, categoryRepository, serviceRepository, knowledgeService);

        contract = ProviderContract.builder().id(1L).build();
        category = MedicalCategory.builder().id(7L).name("Imaging").build();
    }

    @Test
    void addService_withCategoryIdPriceReason_succeeds() {
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));
        when(pricingItemRepository.save(any(ProviderContractPricingItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AddServiceRequest req = new AddServiceRequest("SVC-1", "CT Brain", 7L, null,
                new BigDecimal("150.00"), "provider requested addition");

        ProviderContractPricingItem saved = service.addService(1L, req, "tester");

        assertThat(saved.getServiceName()).isEqualTo("CT Brain");
        assertThat(saved.getBasePrice()).isEqualByComparingTo("150.00");
        assertThat(saved.getContractPrice()).isEqualByComparingTo("150.00");
        assertThat(saved.getCurrency()).isEqualTo("LYD");
        assertThat(saved.getMedicalCategory()).isEqualTo(category);
    }

    @Test
    void addService_withoutPrice_throwsValidation_beforeSavingAnyItem() {
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        AddServiceRequest req = new AddServiceRequest("SVC-1", "CT Brain", 7L, null, null, "reason");

        assertThatThrownBy(() -> service.addService(1L, req, "tester"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void addService_withoutCategoryId_throwsValidation() {
        AddServiceRequest req = new AddServiceRequest("SVC-1", "CT Brain", null, null,
                new BigDecimal("100.00"), "reason");
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.addService(1L, req, "tester"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void addService_withoutReason_throwsValidation() {
        AddServiceRequest req = new AddServiceRequest("SVC-1", "CT Brain", 7L, null,
                new BigDecimal("100.00"), "  ");
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.addService(1L, req, "tester"))
                .isInstanceOf(ValidationException.class);
    }
}
