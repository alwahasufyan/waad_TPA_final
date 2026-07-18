package com.waad.tba.modules.report.export;

import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.report.dto.ProviderReportRowDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Streaming (SXSSF) Excel export for the Providers report. Arabic headers, typed
 * numeric/date cells, formula-injection-safe text, report title + generation
 * time + applied filters. The caller supplies the full filtered result (bounded).
 */
@Component
public class ProviderReportExcelExporter {

    private static final String[] HEADERS = {
            "رقم العقد", "اسم المزود", "النوع", "المدينة", "مستوى الشبكة", "الحالة",
            "بداية العقد", "نهاية العقد", "حالة العقد", "قائمة أسعار نشطة", "رقم النسخة النشطة", "آخر تحديث"
    };

    private static final Map<Provider.ProviderType, String> TYPE_AR = Map.of(
            Provider.ProviderType.HOSPITAL, "مستشفى",
            Provider.ProviderType.CLINIC, "عيادة",
            Provider.ProviderType.LAB, "مختبر",
            Provider.ProviderType.PHARMACY, "صيدلية",
            Provider.ProviderType.RADIOLOGY, "أشعة");

    private static final Map<Provider.NetworkTier, String> TIER_AR = Map.of(
            Provider.NetworkTier.IN_NETWORK, "داخل الشبكة",
            Provider.NetworkTier.OUT_OF_NETWORK, "خارج الشبكة",
            Provider.NetworkTier.PREFERRED, "مفضل");

    public static String contractStatusAr(String s) {
        if (s == null) return "";
        return switch (s) {
            case "ACTIVE" -> "نشط";
            case "EXPIRING_SOON" -> "قارب على الانتهاء";
            case "EXPIRED" -> "منتهي";
            case "FUTURE" -> "مستقبلي";
            case "INACTIVE" -> "غير نشط";
            default -> "لا يوجد";
        };
    }

    public byte[] export(List<ProviderReportRowDto> rows, String reportTitle, Map<String, Object> appliedFilters) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("مقدمو الخدمة");
            sheet.setRightToLeft(true);

            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            int r = 0;

            // Title
            Row titleRow = sheet.createRow(r++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(reportTitle);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            // Generation time
            Row genRow = sheet.createRow(r++);
            genRow.createCell(0).setCellValue("تاريخ التوليد: "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            // Applied filters
            if (appliedFilters != null && !appliedFilters.isEmpty()) {
                StringBuilder fb = new StringBuilder("الفلاتر: ");
                appliedFilters.forEach((k, v) -> fb.append(k).append('=').append(v).append("  "));
                sheet.createRow(r++).createCell(0).setCellValue(sanitize(fb.toString()));
            }
            r++; // blank spacer

            // Header
            Row headerRow = sheet.createRow(r++);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            // Data
            for (ProviderReportRowDto row : rows) {
                Row dataRow = sheet.createRow(r++);
                int c = 0;
                text(dataRow.createCell(c++), row.getActiveContractCode());
                text(dataRow.createCell(c++), row.getName());
                text(dataRow.createCell(c++), row.getProviderType() == null ? "" : TYPE_AR.getOrDefault(row.getProviderType(), row.getProviderType().name()));
                text(dataRow.createCell(c++), row.getCity());
                text(dataRow.createCell(c++), row.getNetworkStatus() == null ? "" : TIER_AR.getOrDefault(row.getNetworkStatus(), row.getNetworkStatus().name()));
                text(dataRow.createCell(c++), Boolean.TRUE.equals(row.getActive()) ? "نشط" : "غير نشط");
                date(dataRow.createCell(c++), row.getContractStartDate(), dateStyle);
                date(dataRow.createCell(c++), row.getContractEndDate(), dateStyle);
                text(dataRow.createCell(c++), contractStatusAr(row.getContractStatus()));
                text(dataRow.createCell(c++), Boolean.TRUE.equals(row.getHasActivePriceList()) ? "نعم" : "لا");
                number(dataRow.createCell(c++), row.getActivePriceListVersionNo());
                dateTime(dataRow.createCell(c++), row.getUpdatedAt(), dateStyle);
            }

            for (int c = 0; c < HEADERS.length; c++) {
                sheet.setColumnWidth(c, 20 * 256);
            }

            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("تعذّر إنشاء ملف Excel لتقرير مقدمي الخدمة", e);
        }
    }

    // ── typed cell writers ─────────────────────────────────────────────────────
    private void text(Cell cell, String value) {
        cell.setCellValue(sanitize(value == null ? "" : value));
    }

    private void number(Cell cell, Integer value) {
        if (value != null) cell.setCellValue(value.doubleValue());
    }

    private void date(Cell cell, LocalDate value, CellStyle style) {
        if (value != null) {
            cell.setCellValue(Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(style);
        }
    }

    private void dateTime(Cell cell, LocalDateTime value, CellStyle style) {
        if (value != null) {
            cell.setCellValue(Date.from(value.atZone(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(style);
        }
    }

    /** Excel/CSV formula-injection guard: neutralise a leading = + - @ tab/CR. */
    private String sanitize(String value) {
        if (value == null || value.isEmpty()) return value;
        char c = value.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + value;
        }
        return value;
    }
}
