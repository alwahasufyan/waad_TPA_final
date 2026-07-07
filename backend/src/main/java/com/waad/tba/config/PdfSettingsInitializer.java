package com.waad.tba.config;

import com.waad.tba.modules.pdf.entity.PdfCompanySettings;
import com.waad.tba.modules.pdf.repository.PdfCompanySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(60)
@RequiredArgsConstructor
@Slf4j
public class PdfSettingsInitializer implements CommandLineRunner {

    private final PdfCompanySettingsRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[PdfSettingsInitializer] Checking PDF settings...");
        try {
            if (repository.count() == 0) {
                log.info("[PdfSettingsInitializer] No PDF settings found. Initializing defaults...");
                PdfCompanySettings defaults = createDefaultSettings();
                repository.save(defaults);
                log.info("[PdfSettingsInitializer] Default PDF settings initialized.");
            } else {
                log.info("[PdfSettingsInitializer] PDF settings already exist.");
            }
        } catch (Exception e) {
            log.error("[PdfSettingsInitializer] Failed to initialize PDF settings: {}", e.getMessage());
        }
    }

    private PdfCompanySettings createDefaultSettings() {
        return PdfCompanySettings.builder()
            .companyName("نظام وعد الطبي")
            .address("الرياض، المملكة العربية السعودية")
            .phone("+966 XX XXX XXXX")
            .email("info@waad-system.com")
            .footerText("جميع الحقوق محفوظة © 2026 - نظام وعد الطبي")
            .footerTextEn("All Rights Reserved © 2026 - Waad Medical System")
            .headerColor("#1976d2")
            .footerColor("#757575")
            .pageSize("A4")
            .marginTop(20)
            .marginBottom(20)
            .marginLeft(20)
            .marginRight(20)
            .isActive(true)
            .claimReportTitle("نظام وعد الطبي")
            .claimReportPrimaryColor("#005f6b")
            .claimReportIntro("نحيطكم علماً بأننا قد انتهينا من مراجعة المطالبات المالية المقدمة من طرفكم والمشار إليها في الدفعة رقم ({batchCode})، وقد تمت المراجعة الفنية والمالية وفق المعايير المعتمدة، وكانت النتائج كالتالي:")
            .claimReportFooterNote("يرجى التكرم بمراجعة التفاصيل والملاحظات المرفقة، وفي حال وجود أي اعتراض يرجى مراسلتنا في غضون أسبوعين من تاريخه.")
            .claimReportSigRightTop("والسلام عليكم")
            .claimReportSigRightBottom("قسم المراجعة والتدقيق")
            .claimReportSigLeftTop("")
            .claimReportSigLeftBottom("إدارة الحسابات")
            .build();
    }
}
