package com.waad.tba.common.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * File Upload DTO
 * 
 * Request parameters for file upload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadDto {
    
    /**
     * Target folder (claims, preauth, members, providers, etc.)
     */
    private String folder;
    
    /**
     * Optional description
     */
    private String description;
    
    /**
     * Entity type (CLAIM, PREAUTH, MEMBER, etc.)
     */
    private String entityType;
    
    /**
     * Entity ID (claim ID, preauth ID, etc.)
     */
    private Long entityId;
}
