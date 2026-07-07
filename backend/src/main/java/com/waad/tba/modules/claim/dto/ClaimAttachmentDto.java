package com.waad.tba.modules.claim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimAttachmentDto {
    private Long id;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private String attachmentType;
    private LocalDateTime createdAt;
}
