package com.waad.tba.modules.medicalclassification.engine.service;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryResolutionServiceTest {

    @Mock
    private MedicalCategoryRepository repository;

    @InjectMocks
    private CategoryResolutionService service;

    @Test
    void resolvesOfficialCategoryByCode() {
        MedicalCategory lab = official(901L, "CAT-LAB", "تحاليل و مختبرات");
        when(repository.findByCodeAndClassificationEnabledTrueAndActiveTrueAndDeletedFalse("CAT-LAB"))
                .thenReturn(Optional.of(lab));

        assertThat(service.resolveCategoryId("CAT-LAB - تحاليل و مختبرات"))
                .contains(901L);
    }

    @Test
    void rejectsLegacyContextAndBenefitCodes() {
        when(repository.findByCodeAndClassificationEnabledTrueAndActiveTrueAndDeletedFalse("CAT023"))
                .thenReturn(Optional.empty());
        when(repository.findByClassificationEnabledTrueAndActiveTrueAndDeletedFalse())
                .thenReturn(List.of(official(901L, "CAT-LAB", "تحاليل و مختبرات")));

        assertThat(service.resolveCategoryId("CAT023 - تصنيف قديم")).isEmpty();
        assertThat(service.resolveCategoryId("OUTPATIENT")).isEmpty();
        assertThat(service.resolveCategoryId("INPATIENT")).isEmpty();
        assertThat(service.resolveCategoryId("BEN-EVACUATION")).isEmpty();
    }

    @Test
    void fallsBackOnlyToOfficialCanonicalArabicName() {
        when(repository.findByClassificationEnabledTrueAndActiveTrueAndDeletedFalse())
                .thenReturn(List.of(official(902L, "CAT-PSYCH-SESS", "الطب النفسي ( جلسات )")));

        assertThat(service.resolveCategoryId("الطب النفسي (جلسات)"))
                .contains(902L);
        assertThat(service.resolveCategoryId("الطب النفسي العام")).isEmpty();
    }

    private static MedicalCategory official(Long id, String code, String name) {
        return MedicalCategory.builder()
                .id(id)
                .code(code)
                .name(name)
                .nameAr(name)
                .active(true)
                .deleted(false)
                .classificationEnabled(true)
                .build();
    }
}
