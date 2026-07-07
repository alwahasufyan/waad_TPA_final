package com.waad.tba.common.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * File Download DTO
 * 
 * Response for file download requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDownloadDto {
    
    /**
     * File content
     */
    private byte[] content;
    
    /**
     * Original filename
     */
    private String fileName;
    
    /**
     * File MIME type
     */
    private String contentType;
    
    /**
     * File size in bytes
     */
    private Long size;
}
