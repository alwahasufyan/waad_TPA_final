package com.waad.tba.common.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * File Upload Result DTO
 * 
 * Contains information about uploaded file
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResult {
    
    /**
     * Unique file identifier (used for download/delete)
     */
    private String fileKey;
    
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
    
    /**
     * Storage folder path
     */
    private String folder;
    
    /**
     * Full storage path
     */
    private String filePath;
    
    /**
     * Public URL (if available)
     */
    private String url;
    
    /**
     * Upload timestamp
     */
    private LocalDateTime uploadedAt;
    
    /**
     * Uploader user ID
     */
    private Long uploadedBy;
}
