package com.waad.tba.common.excel.service;

import com.waad.tba.common.excel.dto.ExcelLookupData;
import com.waad.tba.common.excel.dto.ExcelTemplateColumn;
import com.waad.tba.common.excel.dto.ExcelTemplateColumn.ColumnType;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Unified service for generating Excel templates and parsing imports
 * 
 * KEY PRINCIPLES:
 * 1. System is source of truth for columns and structure
 * 2. Templates must be system-generated only
 * 3. No external Excel formats supported
 * 4. All templates include metadata, validation, and lookup sheets
 */
@Slf4j
@Service
public class ExcelTemplateService {
    
    private static final String TEMPLATE_VERSION = "1.0.0";
    private static final String METADATA_SHEET = "Metadata";
    private static final String DATA_SHEET = "Data";
    
    /**
     * Generate Excel template workbook
     * 
     * @param moduleName Module name (e.g., "Members", "Providers")
     * @param columns Column definitions
     * @param lookups Optional lookup/reference data sheets
     * @return Excel workbook as byte array
     */
    public byte[] generateTemplate(
            String moduleName,
            List<ExcelTemplateColumn> columns,
            List<ExcelLookupData> lookups
    ) throws IOException {
        log.info("[ExcelTemplate] Generating template for module: {}", moduleName);
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle requiredHeaderStyle = createRequiredHeaderStyle(workbook);
        CellStyle exampleStyle = createExampleStyle(workbook);
        CellStyle lookupHeaderStyle = createLookupHeaderStyle(workbook);
        
        // 1. Create metadata sheet
        createMetadataSheet(workbook, moduleName, columns);
        
        // 2. Create main data sheet
        createDataSheet(workbook, columns, headerStyle, requiredHeaderStyle, exampleStyle);
        
        // 3. Create lookup sheets
        if (lookups != null && !lookups.isEmpty()) {
            for (ExcelLookupData lookup : lookups) {
                createLookupSheet(workbook, lookup, lookupHeaderStyle);
            }
        }
        
        // Write to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        log.info("[ExcelTemplate] Template generated successfully: {} columns, {} lookup sheets", 
                columns.size(), lookups != null ? lookups.size() : 0);
        
        return outputStream.toByteArray();
    }
    
    /**
     * Generate Excel template with context data (DTO-based, NOT Entity)
     * 
     * CRITICAL ARCHITECTURAL RULE:
     * This method accepts ONLY DTOs, never JPA Entities.
     * This prevents LazyInitializationException outside transactions.
     * 
     * @param moduleName Module name
     * @param columns Column definitions
     * @param lookups Optional lookup sheets
     * @param contextData DTO containing pre-extracted context (e.g., ContractTemplateContext)
     * @return Excel workbook as byte array
     */
    public byte[] generateTemplateWithContext(
            String moduleName,
            List<ExcelTemplateColumn> columns,
            List<ExcelLookupData> lookups,
            Object contextData
    ) throws IOException {
        log.info("[ExcelTemplate] Generating template with context for module: {}", moduleName);
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle requiredHeaderStyle = createRequiredHeaderStyle(workbook);
        CellStyle exampleStyle = createExampleStyle(workbook);
        CellStyle lookupHeaderStyle = createLookupHeaderStyle(workbook);
        CellStyle contextStyle = createContextDataStyle(workbook);
        
        // 1. Create metadata sheet
        createMetadataSheet(workbook, moduleName, columns);
        
        // 2. Create main data sheet with context info
        createDataSheetWithContext(workbook, columns, headerStyle, requiredHeaderStyle, 
                                   exampleStyle, contextStyle, contextData);
        
        // 3. Create lookup sheets
        if (lookups != null && !lookups.isEmpty()) {
            for (ExcelLookupData lookup : lookups) {
                createLookupSheet(workbook, lookup, lookupHeaderStyle);
            }
        }
        
        // Write to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        log.info("[ExcelTemplate] Template with context generated successfully");
        
        return outputStream.toByteArray();
    }
    

    
    /**
     * Create metadata sheet with template information
     */
    private void createMetadataSheet(XSSFWorkbook workbook, String moduleName, List<ExcelTemplateColumn> columns) {
        Sheet sheet = workbook.createSheet(METADATA_SHEET);
        
        int rowNum = 0;
        
        // Header
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("TBA-WAAD System - Excel Import Template");
        
        // Module
        Row moduleRow = sheet.createRow(rowNum++);
        moduleRow.createCell(0).setCellValue("Module / النموذج:");
        moduleRow.createCell(1).setCellValue(moduleName);
        
        // Version
        Row versionRow = sheet.createRow(rowNum++);
        versionRow.createCell(0).setCellValue("Template Version / إصدار القالب:");
        versionRow.createCell(1).setCellValue(TEMPLATE_VERSION);
        
        // Warning
        rowNum++;
        Row warningRow = sheet.createRow(rowNum++);
        warningRow.createCell(0).setCellValue("⚠️ تحذير / WARNING:");
        rowNum++;
        Row warning1 = sheet.createRow(rowNum++);
        warning1.createCell(0).setCellValue("Only files downloaded from this system are accepted.");
        Row warning2 = sheet.createRow(rowNum++);
        warning2.createCell(0).setCellValue("يتم قبول الملفات المُنزَّلة من هذا النظام فقط.");
        
        // Column definitions
        rowNum++;
        Row columnHeaderRow = sheet.createRow(rowNum++);
        columnHeaderRow.createCell(0).setCellValue("Column Name");
        columnHeaderRow.createCell(1).setCellValue("Arabic Name");
        columnHeaderRow.createCell(2).setCellValue("Type");
        columnHeaderRow.createCell(3).setCellValue("Required");
        columnHeaderRow.createCell(4).setCellValue("Description");
        
        for (ExcelTemplateColumn column : columns) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(column.getName());
            row.createCell(1).setCellValue(column.getNameAr() != null ? column.getNameAr() : "");
            row.createCell(2).setCellValue(column.getType().toString());
            row.createCell(3).setCellValue(column.isRequired() ? "YES" : "NO");
            row.createCell(4).setCellValue(column.getDescription() != null ? column.getDescription() : "");
        }
        
        // Auto-size columns
        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }
        
        // Hide metadata sheet from users (they should work in Data sheet)
        workbook.setSheetHidden(workbook.getSheetIndex(sheet), true);
    }
    
    /**
     * Create main data entry sheet with headers and validation
     */
    private void createDataSheet(
            XSSFWorkbook workbook,
            List<ExcelTemplateColumn> columns,
            CellStyle headerStyle,
            CellStyle requiredHeaderStyle,
            CellStyle exampleStyle
    ) {
        Sheet sheet = workbook.createSheet(DATA_SHEET);
        
        // Create header row
        Row headerRow = sheet.createRow(0);
        
        // Create example row
        Row exampleRow = sheet.createRow(1);
        
        for (int i = 0; i < columns.size(); i++) {
            ExcelTemplateColumn column = columns.get(i);
            
            // Header cell
            Cell headerCell = headerRow.createCell(i);
            String headerText = column.getName();
            if (column.getNameAr() != null) {
                headerText = column.getNameAr() + "\n" + column.getName();
            }
            if (column.isRequired()) {
                headerText = "* " + headerText;
            }
            headerCell.setCellValue(headerText);
            headerCell.setCellStyle(column.isRequired() ? requiredHeaderStyle : headerStyle);
            
            // Example cell
            Cell exampleCell = exampleRow.createCell(i);
            if (column.getExample() != null) {
                exampleCell.setCellValue(column.getExample());
                exampleCell.setCellStyle(exampleStyle);
            }
            
            // Set column width
            sheet.setColumnWidth(i, column.getWidth() * 256);
            
            // Add data validation for enums
            if (column.getType() == ColumnType.ENUM && column.getAllowedValues() != null && !column.getAllowedValues().isEmpty()) {
                addDropdownValidation(sheet, i, column.getAllowedValues());
            }
            
            // Add comment with description
            if (column.getDescription() != null || column.getDescriptionAr() != null) {
                addCellComment(workbook, sheet, headerCell, column);
            }
        }
        
        // Freeze header row
        sheet.createFreezePane(0, 1);
    }
    
    /**
     * Create lookup/reference data sheet
     */
    private void createLookupSheet(XSSFWorkbook workbook, ExcelLookupData lookup, CellStyle headerStyle) {
        String sheetName = lookup.getSheetName();
        if (sheetName == null || sheetName.length() > 31) {
            sheetName = "Lookup_" + workbook.getNumberOfSheets();
        }
        
        Sheet sheet = workbook.createSheet(sheetName);
        
        int rowNum = 0;
        
        // Add description if available
        if (lookup.getDescriptionAr() != null || lookup.getDescription() != null) {
            Row descRow = sheet.createRow(rowNum++);
            String desc = lookup.getDescriptionAr() != null ? lookup.getDescriptionAr() : lookup.getDescription();
            descRow.createCell(0).setCellValue(desc);
            rowNum++;
        }
        
        // Create header row
        if (lookup.getHeaders() != null && !lookup.getHeaders().isEmpty()) {
            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < lookup.getHeaders().size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(lookup.getHeaders().get(i));
                cell.setCellStyle(headerStyle);
            }
        }
        
        // Create data rows
        if (lookup.getData() != null) {
            for (List<String> dataRow : lookup.getData()) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < dataRow.size(); i++) {
                    row.createCell(i).setCellValue(dataRow.get(i));
                }
            }
        }
        
        // Auto-size columns
        if (lookup.getHeaders() != null) {
            for (int i = 0; i < lookup.getHeaders().size(); i++) {
                sheet.autoSizeColumn(i);
            }
        }
    }
    
    /**
     * Add dropdown validation to a column
     */
    private void addDropdownValidation(Sheet sheet, int columnIndex, List<String> allowedValues) {
        DataValidationHelper validationHelper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = validationHelper.createExplicitListConstraint(
                allowedValues.toArray(new String[0])
        );
        
        // Apply to rows 2-10000 (skip header and example)
        CellRangeAddressList addressList = new CellRangeAddressList(2, 10000, columnIndex, columnIndex);
        DataValidation validation = validationHelper.createValidation(constraint, addressList);
        
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("Invalid Value", "Please select a value from the dropdown list.");
        
        sheet.addValidationData(validation);
    }
    
    /**
     * Add comment/tooltip to header cell
     */
    private void addCellComment(XSSFWorkbook workbook, Sheet sheet, Cell cell, ExcelTemplateColumn column) {
        CreationHelper factory = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        
        ClientAnchor anchor = factory.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 3);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 3);
        
        Comment comment = drawing.createCellComment(anchor);
        
        StringBuilder text = new StringBuilder();
        if (column.getDescriptionAr() != null) {
            text.append(column.getDescriptionAr()).append("\n\n");
        }
        if (column.getDescription() != null) {
            text.append(column.getDescription());
        }
        
        RichTextString richText = factory.createRichTextString(text.toString());
        comment.setString(richText);
        comment.setAuthor("TBA-WAAD System");
        
        cell.setCellComment(comment);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CELL STYLES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // Background color
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Border
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Font
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        
        // Alignment
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        
        return style;
    }
    
    private CellStyle createRequiredHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // Background color (yellow for required)
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Border
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Font
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.DARK_RED.getIndex());
        style.setFont(font);
        
        // Alignment
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        
        return style;
    }
    
    private CellStyle createExampleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // Background color
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Border
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Font
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        
        return style;
    }
    
    private CellStyle createLookupHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // Background color
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Border
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Font
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        
        // Alignment
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        return style;
    }
    
    private CellStyle createContextDataStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // Light green background for context pre-filled data
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Border
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Font
        Font font = workbook.createFont();
        font.setBold(false);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        
        return style;
    }
    
    /**
     * Create data sheet with context information (DTO-based)
     * Context data is extracted from DTO using simple getters (no lazy loading risk)
     */
    private void createDataSheetWithContext(
            XSSFWorkbook workbook,
            List<ExcelTemplateColumn> columns,
            CellStyle headerStyle,
            CellStyle requiredHeaderStyle,
            CellStyle exampleStyle,
            CellStyle contextStyle,
            Object contextData
    ) {
        Sheet sheet = workbook.createSheet(DATA_SHEET);
        
        // Extract context info safely from DTO (no Entity access)
        String contextInfo = extractContextInfo(contextData);
        
        int rowNum = 0;
        
        // Add context info row (green background)
        if (contextInfo != null && !contextInfo.isEmpty()) {
            Row contextRow = sheet.createRow(rowNum++);
            Cell contextCell = contextRow.createCell(0);
            contextCell.setCellValue(contextInfo);
            contextCell.setCellStyle(contextStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, columns.size() - 1));
            rowNum++; // Empty row
        }
        
        // Create header row
        Row headerRow = sheet.createRow(rowNum++);
        
        // Create example row
        Row exampleRow = sheet.createRow(rowNum++);
        
        for (int i = 0; i < columns.size(); i++) {
            ExcelTemplateColumn column = columns.get(i);
            
            // Header cell
            Cell headerCell = headerRow.createCell(i);
            String headerText = column.getName();
            if (column.getNameAr() != null) {
                headerText = column.getNameAr() + "\n" + column.getName();
            }
            if (column.isRequired()) {
                headerText = "* " + headerText;
            }
            headerCell.setCellValue(headerText);
            headerCell.setCellStyle(column.isRequired() ? requiredHeaderStyle : headerStyle);
            
            // Example cell
            Cell exampleCell = exampleRow.createCell(i);
            if (column.getExample() != null) {
                exampleCell.setCellValue(column.getExample());
                exampleCell.setCellStyle(exampleStyle);
            }
            
            // Set column width
            sheet.setColumnWidth(i, column.getWidth() * 256);
            
            // Add data validation for enums
            if (column.getType() == ColumnType.ENUM && column.getAllowedValues() != null && !column.getAllowedValues().isEmpty()) {
                addDropdownValidation(sheet, i, column.getAllowedValues());
            }
            
            // Add comment with description
            if (column.getDescription() != null || column.getDescriptionAr() != null) {
                addCellComment(workbook, sheet, headerCell, column);
            }
        }
        
        // Freeze header row
        int freezeRow = contextInfo != null && !contextInfo.isEmpty() ? 3 : 1;
        sheet.createFreezePane(0, freezeRow);
    }
    
    /**
     * Extract context info from DTO (safe, no lazy loading)
     * Uses simple String concatenation, no reflection on Entities
     */
    private String extractContextInfo(Object contextData) {
        if (contextData == null) {
            return null;
        }
        
        try {
            // Check if there's a custom display method
            try {
                java.lang.reflect.Method displayMethod = contextData.getClass().getMethod("getContextDisplay");
                return (String) displayMethod.invoke(contextData);
            } catch (NoSuchMethodException ignored) {
                // Use toString() if no custom method
                return contextData.toString();
            }
        } catch (Exception e) {
            log.warn("[ExcelTemplate] Could not extract context info: {}", e.getMessage());
            return null;
        }
    }
}
