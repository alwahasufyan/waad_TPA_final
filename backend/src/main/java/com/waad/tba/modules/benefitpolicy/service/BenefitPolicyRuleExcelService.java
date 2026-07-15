package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ValidationException;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleImportPreviewDto;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleImportPreviewDto.Row;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleImportPreviewDto.RowError;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenefitPolicyRuleExcelService {

    static final String SHEET_NAME = "قواعد التغطية";
    static final String MARKER = "WAAD-BENEFIT-RULES-V1";
    static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final int DATA_START_ROW = 2;

    private final BenefitPolicyRepository policyRepository;
    private final BenefitPolicyRuleRepository ruleRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public byte[] generateTemplate(Long policyId) {
        BenefitPolicy policy = requirePolicy(policyId);
        List<MedicalCategory> categories = officialCategories();
        Map<Long, BenefitPolicyRule> existing = new LinkedHashMap<>();
        ruleRepository.findByBenefitPolicyId(policyId).forEach(rule -> {
            if (rule.getMedicalCategory() != null) existing.put(rule.getMedicalCategory().getId(), rule);
        });

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.setRightToLeft(true);
            String[] headers = {"رمز التصنيف", "اسم التصنيف", "نسبة التغطية %", "سقف المبلغ (د.ل)",
                    "سقف المرات", "فترة الانتظار (يوم)", "يتطلب موافقة مسبقة", "ملاحظات"};
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i == 1 || i == 7 ? 9500 : 5200);
            }
            org.apache.poi.ss.usermodel.Row marker = sheet.createRow(1);
            marker.createCell(0).setCellValue(MARKER);
            marker.createCell(1).setCellValue("policyId=" + policyId);
            marker.setZeroHeight(true);

            int index = DATA_START_ROW;
            for (MedicalCategory category : categories) {
                BenefitPolicyRule rule = existing.get(category.getId());
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(index++);
                row.createCell(0).setCellValue(category.getCode());
                row.createCell(1).setCellValue(displayName(category));
                if (rule != null) {
                    if (rule.getCoveragePercent() != null) row.createCell(2).setCellValue(rule.getCoveragePercent());
                    if (rule.getAmountLimit() != null) row.createCell(3).setCellValue(rule.getAmountLimit().doubleValue());
                    if (rule.getTimesLimit() != null) row.createCell(4).setCellValue(rule.getTimesLimit());
                    row.createCell(5).setCellValue(Optional.ofNullable(rule.getWaitingPeriodDays()).orElse(0));
                    row.createCell(6).setCellValue(rule.isRequiresPreApproval() ? "نعم" : "لا");
                    if (rule.getNotes() != null) row.createCell(7).setCellValue(rule.getNotes());
                }
            }
            sheet.createFreezePane(0, DATA_START_ROW);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessRuleException("تعذر إنشاء قالب قواعد المنافع");
        }
    }

    @Transactional(readOnly = true)
    public BenefitPolicyRuleImportPreviewDto preview(Long policyId, MultipartFile file) {
        requirePolicy(policyId);
        byte[] bytes = validatedBytes(file);
        return parse(policyId, bytes);
    }

    @Transactional
    public BenefitPolicyRuleImportPreviewDto apply(Long policyId, MultipartFile file, String expectedHash, String actor) {
        BenefitPolicy policy = requirePolicy(policyId);
        byte[] bytes = validatedBytes(file);
        BenefitPolicyRuleImportPreviewDto preview = parse(policyId, bytes);
        if (expectedHash == null || !expectedHash.equals(preview.fileHash())) {
            throw new ValidationException("الملف تغير بعد المعاينة. أعد المعاينة قبل التطبيق");
        }
        if (!preview.valid()) {
            throw new ValidationException("لا يمكن تطبيق الملف قبل معالجة أخطاء المعاينة");
        }

        Map<String, MedicalCategory> categories = officialCategoryMap();
        for (Row row : preview.rows()) {
            MedicalCategory category = categories.get(row.categoryCode());
            BenefitPolicyRule rule = ruleRepository
                    .findByBenefitPolicyIdAndMedicalCategoryId(policyId, category.getId())
                    .orElseGet(() -> BenefitPolicyRule.builder()
                            .benefitPolicy(policy)
                            .medicalCategory(category)
                            .build());
            rule.setCoveragePercent(row.coveragePercent());
            rule.setAmountLimit(row.amountLimit());
            rule.setTimesLimit(row.timesLimit());
            rule.setWaitingPeriodDays(row.waitingPeriodDays());
            rule.setRequiresPreApproval(row.requiresPreApproval());
            rule.setNotes(row.notes());
            rule.setActive(true);
            rule.setDeleted(false);
            ruleRepository.save(rule);
        }
        auditLogService.createAuditLog("BENEFIT_RULES_EXCEL_APPLIED", "BenefitPolicy", policyId,
                "Excel rules applied: hash=" + preview.fileHash() + ", rows=" + preview.validRows()
                        + ", created=" + preview.createCount() + ", updated=" + preview.updateCount(),
                null, actor, null, null);
        log.info("Benefit policy rules Excel applied: policyId={}, actor={}, hash={}, created={}, updated={}",
                policyId, actor, preview.fileHash(), preview.createCount(), preview.updateCount());
        return preview;
    }

    private BenefitPolicyRuleImportPreviewDto parse(Long policyId, byte[] bytes) {
        List<Row> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        Map<String, MedicalCategory> categories = officialCategoryMap();
        Map<Long, BenefitPolicyRule> existing = new LinkedHashMap<>();
        ruleRepository.findByBenefitPolicyId(policyId).forEach(rule -> {
            if (rule.getMedicalCategory() != null) existing.put(rule.getMedicalCategory().getId(), rule);
        });
        int total = 0;
        int creates = 0;
        int updates = 0;
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Set<String> seenCodes = new HashSet<>();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                errors.add(new RowError(0, "sheet", "ورقة «قواعد التغطية» غير موجودة"));
                return result(bytes, total, creates, updates, rows, errors);
            }
            String marker = formatter.formatCellValue(sheet.getRow(1).getCell(0));
            String policyMarker = formatter.formatCellValue(sheet.getRow(1).getCell(1));
            if (!MARKER.equals(marker) || !policyMarker.equals("policyId=" + policyId)) {
                errors.add(new RowError(0, "template", "القالب لا يخص هذه الوثيقة أو ليس قالب Waad المعتمد"));
                return result(bytes, total, creates, updates, rows, errors);
            }
            for (int i = DATA_START_ROW; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row excelRow = sheet.getRow(i);
                if (excelRow == null) continue;
                String code = text(excelRow, 0, formatter).toUpperCase(Locale.ROOT);
                String coverageText = text(excelRow, 2, formatter);
                String amountText = text(excelRow, 3, formatter);
                String timesText = text(excelRow, 4, formatter);
                String waitingText = text(excelRow, 5, formatter);
                String approvalText = text(excelRow, 6, formatter);
                String notes = text(excelRow, 7, formatter);
                boolean hasEditableValue = !coverageText.isBlank() || !amountText.isBlank() || !timesText.isBlank()
                        || !waitingText.isBlank() || !approvalText.isBlank() || !notes.isBlank();
                if (code.isBlank() || !hasEditableValue) continue;
                total++;
                int rowNumber = i + 1;
                MedicalCategory category = categories.get(code);
                if (category == null) {
                    errors.add(new RowError(rowNumber, "categoryCode", "رمز التصنيف غير رسمي أو غير نشط: " + code));
                    continue;
                }
                if (!seenCodes.add(code)) {
                    errors.add(new RowError(rowNumber, "categoryCode",
                            "\u0631\u0645\u0632 \u0627\u0644\u062a\u0635\u0646\u064a\u0641 \u0645\u0643\u0631\u0631 \u0641\u064a \u0645\u0644\u0641 \u0627\u0644\u0627\u0633\u062a\u064a\u0631\u0627\u062f: " + code));
                    continue;
                }
                try {
                    Integer coverage = requiredInteger(coverageText, "نسبة التغطية", 0, 100);
                    BigDecimal amount = optionalDecimal(amountText, "سقف المبلغ");
                    Integer times = optionalInteger(timesText, "سقف المرات", 0, Integer.MAX_VALUE);
                    Integer waiting = waitingText.isBlank() ? 0 : requiredInteger(waitingText, "فترة الانتظار", 0, 36500);
                    boolean requires = parseYesNo(approvalText, rowNumber);
                    if (notes.length() > 500) throw new IllegalArgumentException("الملاحظات تتجاوز 500 حرف");
                    boolean update = existing.containsKey(category.getId());
                    if (update) updates++; else creates++;
                    rows.add(new Row(rowNumber, code, displayName(category), coverage, amount, times, waiting,
                            requires, notes.isBlank() ? null : notes, update ? "UPDATE" : "CREATE"));
                } catch (IllegalArgumentException exception) {
                    errors.add(new RowError(rowNumber, "row", exception.getMessage()));
                }
            }
        } catch (Exception exception) {
            errors.add(new RowError(0, "file", "تعذر قراءة ملف Excel: " + exception.getMessage()));
        }
        if (total == 0 && errors.isEmpty()) {
            errors.add(new RowError(0, "rows", "لا توجد قواعد معدلة أو جديدة في الملف"));
        }
        return result(bytes, total, creates, updates, rows, errors);
    }

    private BenefitPolicyRuleImportPreviewDto result(byte[] bytes, int total, int creates, int updates,
            List<Row> rows, List<RowError> errors) {
        return new BenefitPolicyRuleImportPreviewDto(hash(bytes), total, rows.size(), creates, updates,
                List.copyOf(rows), List.copyOf(errors));
    }

    private BenefitPolicy requirePolicy(Long policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessRuleException("وثيقة المنافع غير موجودة: " + policyId));
    }

    private List<MedicalCategory> officialCategories() {
        List<MedicalCategory> categories = new ArrayList<>(
                categoryRepository.findByClassificationEnabledTrueAndActiveTrueAndDeletedFalse());
        categories.removeIf(category -> category.getCode() == null || !category.getCode().startsWith("CAT-"));
        categories.sort(Comparator.comparing(MedicalCategory::getCode));
        return categories;
    }

    private Map<String, MedicalCategory> officialCategoryMap() {
        Map<String, MedicalCategory> result = new LinkedHashMap<>();
        officialCategories().forEach(category -> result.put(category.getCode().toUpperCase(Locale.ROOT), category));
        return result;
    }

    private byte[] validatedBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ValidationException("يرجى اختيار ملف Excel");
        if (file.getSize() > MAX_FILE_BYTES) throw new ValidationException("حجم ملف Excel يتجاوز 5 ميجابايت");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) throw new ValidationException("الصيغة المدعومة هي .xlsx فقط");
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw new ValidationException("تعذر قراءة ملف Excel", exception);
        }
    }

    private static String text(org.apache.poi.ss.usermodel.Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static Integer requiredInteger(String text, String label, int min, int max) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException(label + " مطلوب");
        try {
            String numeric = text.replaceAll("[^0-9+\\-.,]", "").replace(",", "").trim();
            BigDecimal number = new BigDecimal(numeric);
            int value = number.intValueExact();
            if (value < min || value > max) throw new IllegalArgumentException(label + " خارج النطاق المسموح");
            return value;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(label + " يجب أن يكون رقمًا صحيحًا");
        }
    }

    private static Integer optionalInteger(String text, String label, int min, int max) {
        return text == null || text.isBlank() ? null : requiredInteger(text, label, min, max);
    }

    private static BigDecimal optionalDecimal(String text, String label) {
        if (text == null || text.isBlank()) return null;
        try {
            BigDecimal value = new BigDecimal(text.replace(",", ""));
            if (value.signum() < 0) throw new IllegalArgumentException(label + " لا يمكن أن يكون سالبًا");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " يجب أن يكون رقمًا صالحًا");
        }
    }

    private static boolean parseYesNo(String text, int rowNumber) {
        if (text == null || text.isBlank() || "لا".equals(text) || "NO".equalsIgnoreCase(text)
                || "FALSE".equalsIgnoreCase(text)) return false;
        if ("نعم".equals(text) || "YES".equalsIgnoreCase(text) || "TRUE".equalsIgnoreCase(text)) return true;
        throw new IllegalArgumentException("قيمة الموافقة المسبقة غير صحيحة في الصف " + rowNumber + "؛ استخدم نعم أو لا");
    }

    private static String displayName(MedicalCategory category) {
        if (category.getNameAr() != null && !category.getNameAr().isBlank()) return category.getNameAr();
        if (category.getName() != null && !category.getName().isBlank()) return category.getName();
        return category.getCode();
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
