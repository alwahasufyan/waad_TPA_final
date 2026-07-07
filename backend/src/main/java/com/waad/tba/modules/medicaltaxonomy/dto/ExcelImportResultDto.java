package com.waad.tba.modules.medicaltaxonomy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of Excel import operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportResultDto {
    
    /**
     * Success flag
     */
    private Boolean success;
    
    /**
     * Summary of import operation
     */
    private ImportSummary summary;
    
    /**
     * User-friendly message
     */
    private String message;
    
    /**
     * Import summary details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportSummary {
        /**
         * Total rows processed
         */
        private Integer total;
        
        /**
         * Number of rows successfully inserted
         */
        private Integer inserted;
        
        /**
         * Number of rows successfully updated
         */
        private Integer updated;
        
        /**
         * Number of rows skipped (duplicates)
         */
        private Integer skipped;
        
        /**
         * Number of rows failed
         */
        private Integer failed;
        
        /**
         * List of errors encountered
         */
        @Builder.Default
        private List<ImportError> errors = new ArrayList<>();
    }
    
    /**
     * Import error details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportError {
        /**
         * Row number in Excel (1-based)
         */
        private Integer row;
        
        /**
         * Column name/index
         */
        private String column;
        
        /**
         * Cell value that caused error
         */
        private String value;
        
        /**
         * Error message
         */
        private String error;
    }
}
