package com.waad.tba.common.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a column definition in an Excel template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelTemplateColumn {
    
    /**
     * Column name (header)
     */
    private String name;
    
    /**
     * Arabic column name
     */
    private String nameAr;
    
    /**
     * Column type (TEXT, NUMBER, DATE, BOOLEAN, ENUM)
     */
    private ColumnType type;
    
    /**
     * Whether this column is mandatory
     */
    private boolean required;
    
    /**
     * Example value for this column
     */
    private String example;
    
    /**
     * Allowed values (for dropdowns/enums)
     */
    private List<String> allowedValues;
    
    /**
     * Description/tooltip
     */
    private String description;
    
    /**
     * Description in Arabic
     */
    private String descriptionAr;
    
    /**
     * Max length (for text)
     */
    private Integer maxLength;
    
    /**
     * Column width in Excel (in characters)
     */
    @Builder.Default
    private int width = 20;
    
    public enum ColumnType {
        TEXT,
        NUMBER,
        DATE,
        BOOLEAN,
        ENUM
    }
}
