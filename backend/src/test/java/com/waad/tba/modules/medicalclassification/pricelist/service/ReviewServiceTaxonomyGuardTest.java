package com.waad.tba.modules.medicalclassification.pricelist.service;

import com.waad.tba.common.exception.ValidationException;
import com.waad.tba.modules.medicalclassification.engine.service.CatalogKnowledgeService;
import com.waad.tba.modules.medicalclassification.pricelist.dto.ReviewDecisionDto;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImport;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportLineRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportRepository;
import com.waad.tba.modules.medicalclassification.repository.CatalogClassificationHistoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTaxonomyGuardTest {

    @Mock PriceListImportRepository importRepository;
    @Mock PriceListImportLineRepository lineRepository;
    @Mock CatalogKnowledgeService knowledge;
    @Mock CatalogClassificationHistoryRepository historyRepository;
    @Mock MedicalCategoryRepository categoryRepository;
    @Mock PriceListVersionService versionService;
    @Mock ProviderContractRepository contractRepository;

    @InjectMocks ReviewService service;

    @Test
    void reviewerCannotApproveLegacyOrNonOfficialCategory() {
        PriceListImport imp = PriceListImport.builder()
                .id(10L)
                .status(PriceListImport.Status.CLASSIFIED)
                .build();
        PriceListImportLine line = PriceListImportLine.builder()
                .id(20L)
                .importId(10L)
                .rawName("خدمة تجريبية")
                .reviewStatus(PriceListImportLine.ReviewStatus.NEEDS_REVIEW)
                .build();
        when(importRepository.findById(10L)).thenReturn(Optional.of(imp));
        when(lineRepository.findById(20L)).thenReturn(Optional.of(line));
        when(categoryRepository.findByIdAndClassificationEnabledTrueAndActiveTrueAndDeletedFalse(999L))
                .thenReturn(Optional.empty());

        ReviewDecisionDto decision = ReviewDecisionDto.builder()
                .action(ReviewDecisionDto.Action.APPROVE)
                .categoryId(999L)
                .build();

        assertThatThrownBy(() -> service.decide(10L, 20L, decision, "reviewer"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("CAT");
    }
}
