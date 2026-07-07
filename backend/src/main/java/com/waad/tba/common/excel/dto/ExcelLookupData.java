package com.waad.tba.common.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents lookup/reference data to be included in template as separate sheet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelLookupData {
    
    /**
     * Sheet name for this lookup data
     */
    private String sheetName;
    
    /**
     * Sheet name in Arabic
     */
    private String sheetNameAr;
    
    /**
     * Column headers
     */
    private List<String> headers;
    
    /**
     * Data rows (each row is a list of cell values)
     */
    private List<List<String>> data;
    
    /**
     * Description of this lookup sheet
     */
    private String description;
    
    /**
     * Description in Arabic
     */
    private String descriptionAr;
}
