package com.waad.tba.modules.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Excel Preview Row DTO
 * 
 * Represents a single row from Excel file for preview purposes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelPreviewRowDto {

    /**
     * Row number (1-based)
     */
    private Integer rowNumber;

    /**
     * Cell values in order of columns
     */
    private List<String> values;
}
