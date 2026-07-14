package com.waad.tba.modules.providercontract.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.waad.tba.modules.providercontract.service.PriceListExcelTemplateService;

@ExtendWith(MockitoExtension.class)
class ProviderContractPricingExcelControllerTest {

    @Mock private PriceListExcelTemplateService templateService;
    @InjectMocks private ProviderContractPricingExcelController controller;

    @Test
    void legacyDirectImportIsGoneAndNeverInvokesPricingWriter() {
        var response = controller.importPriceList(7L,
                new MockMultipartFile("file", "prices.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody().getMessageAr()).contains("إيقاف الاستيراد المباشر");
        verifyNoInteractions(templateService);
    }
}
