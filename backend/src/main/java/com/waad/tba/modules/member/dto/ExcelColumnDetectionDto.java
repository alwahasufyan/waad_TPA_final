package com.waad.tba.modules.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Excel Column Detection DTO
 * 
 * Contains detected columns from Excel file with mapping suggestions
 * and preview data for user verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelColumnDetectionDto {

    /**
     * Original filename
     */
    private String fileName;

    /**
     * Sheet name
     */
    private String sheetName;

    /**
     * Total number of rows in sheet
     */
    private Integer totalRows;

    /**
     * Total number of columns detected
     */
    private Integer totalColumns;

    /**
     * Header row number (0-based, typically 0)
     */
    private Integer headerRowNumber;

    /**
     * List of detected column names/headers
     */
    private List<String> columnHeaders;

    /**
     * Suggested mappings for each column
     * Key: column index, Value: suggestion
     */
    private List<ExcelMappingSuggestionDto> suggestions;

    /**
     * Preview rows (typically first 3-5 data rows after header)
     * For user to verify mapping correctness
     */
    private List<ExcelPreviewRowDto> previewRows;

    /**
     * Required fields that are missing from suggestions
     * These must be manually mapped by user
     */
    private List<String> missingRequiredFields;

    /**
     * Overall confidence score (average of all suggestions)
     */
    private Double overallConfidence;

    /**
     * Number of auto-accepted mappings
     */
    private Integer autoAcceptedCount;

    /**
     * Number of suggestions requiring manual review
     */
    private Integer manualReviewCount;

    /**
     * Warnings or issues detected
     * Examples: "Duplicate columns", "Empty columns", "Invalid data format"
     */
    private List<String> warnings;
}
