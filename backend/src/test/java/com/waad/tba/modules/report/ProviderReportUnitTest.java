package com.waad.tba.modules.report;

import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.report.dto.ProviderReportRowDto;
import com.waad.tba.modules.report.export.ProviderReportExcelExporter;
import com.waad.tba.modules.report.repository.ProviderReportQueryRepository;
import com.waad.tba.modules.report.service.ProviderReportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for the Providers report that run without a database:
 * provider-type parsing, the sort allow-list, and the Excel export (structure +
 * formula-injection sanitization). DB-backed filter/summary/scope behaviour is
 * exercised by integration tests / CI.
 */
class ProviderReportUnitTest {

    // #5/#6 — sort allow-list rejects unknown columns
    @Test
    void sortAllowListAcceptsKnownRejectsUnknown() {
        assertTrue(ProviderReportQueryRepository.isSortable("name"));
        assertTrue(ProviderReportQueryRepository.isSortable("code"));
        assertTrue(ProviderReportQueryRepository.isSortable("updatedAt"));
        assertFalse(ProviderReportQueryRepository.isSortable("id"));
        assertFalse(ProviderReportQueryRepository.isSortable("password"));
        assertFalse(ProviderReportQueryRepository.isSortable("; DROP TABLE providers"));
        assertTrue(ProviderReportQueryRepository.sortableFields().contains("contractEndDate"));
    }

    // providerType query param is parsed safely (invalid → null, never throws)
    @Test
    void parseProviderTypeIsSafe() {
        assertEquals(Provider.ProviderType.HOSPITAL, ProviderReportService.parseProviderType("HOSPITAL"));
        assertEquals(Provider.ProviderType.CLINIC, ProviderReportService.parseProviderType("clinic"));
        assertNull(ProviderReportService.parseProviderType("NOT_A_TYPE"));
        assertNull(ProviderReportService.parseProviderType(""));
        assertNull(ProviderReportService.parseProviderType(null));
    }

    // #13/#15 — export produces a valid workbook and neutralises formula injection
    @Test
    void exportSanitizesFormulaInjectionAndBuildsWorkbook() throws Exception {
        ProviderReportExcelExporter exporter = new ProviderReportExcelExporter();

        ProviderReportRowDto malicious = row("=HYPERLINK(\"http://x\")", "P-1");
        malicious.setContractStatus("ACTIVE");
        ProviderReportRowDto normal = row("مستشفى النور", "P-2");
        normal.setContractStatus("EXPIRED");

        byte[] bytes = exporter.export(List.of(malicious, normal), "تقرير مقدمي الخدمة", Map.of("active", true));
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertTrue(sheet.isRightToLeft(), "sheet should be RTL");

            // locate the header row (Arabic "اسم المزود" in column 1)
            int headerRowIdx = -1;
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null && row.getCell(1) != null && "اسم المزود".equals(row.getCell(1).getStringCellValue())) {
                    headerRowIdx = i;
                    break;
                }
            }
            assertTrue(headerRowIdx >= 0, "Arabic header row present");

            Row firstData = sheet.getRow(headerRowIdx + 1);
            String nameCell = firstData.getCell(1).getStringCellValue();
            assertTrue(nameCell.startsWith("'="), "formula-leading text must be prefixed with a quote: " + nameCell);
        }
    }

    private ProviderReportRowDto row(String name, String code) {
        return new ProviderReportRowDto(
                1L, code, name, Provider.ProviderType.HOSPITAL, "طرابلس", true,
                Provider.NetworkTier.IN_NETWORK, LocalDate.now().minusYears(1), LocalDate.now().plusMonths(6),
                null, 3L, 2, "CN-" + code);
    }
}
