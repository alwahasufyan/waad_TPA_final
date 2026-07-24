package com.waad.tba.modules.claim.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.dto.ClaimAttachmentDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimAttachment;
import com.waad.tba.modules.claim.entity.ClaimAttachmentType;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.service.ClaimAttachmentService;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.file.AttachmentFileTypePolicy;
import com.waad.tba.common.file.FileResourceUtils;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Claim Attachment Controller
 * 
 * REST endpoints for managing claim attachments
 * 
 * Endpoints:
 * - POST   /api/claims/{id}/attachments           - Upload attachment
 * - GET    /api/claims/{id}/attachments           - List all attachments
 * - GET    /api/claims/{id}/attachments/{attId}   - Download attachment
 * - DELETE /api/claims/{id}/attachments/{attId}   - Delete attachment
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ClaimAttachmentController {
    
    private final ClaimAttachmentService attachmentService;
    private final ClaimRepository claimRepository;
    private final ProviderContextGuard providerContextGuard;
    private final ReviewerProviderIsolationService reviewerIsolationService;
    private final AuthorizationService authorizationService;

    /**
     * DOCUMENTS-IDOR-1: verify the claim this attachment/request is scoped to actually
     * belongs to the caller's own provider. A PROVIDER_STAFF user is rejected (403) for
     * any other provider's claim; reviewer/admin roles are unaffected (existing
     * ProviderContextGuard#validateProviderAccess semantics — no-op for non-providers).
     *
     * CLAIM-REVIEW-SECURITY-1: ProviderContextGuard is a no-op for MEDICAL_REVIEWER, so
     * it alone does not stop a reviewer from reading/deleting attachments on a claim from
     * a provider they aren't assigned to. Also enforce reviewer-provider isolation here
     * (no-op for SUPER_ADMIN/ADMIN and for PROVIDER_STAFF, which is already handled above).
     */
    private void assertClaimBelongsToCaller(Long claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", claimId));
        providerContextGuard.validateProviderAccess(claim.getProviderId());
        reviewerIsolationService.validateReviewerAccess(authorizationService.getCurrentUser(), claim.getProviderId());
    }
    
    /**
     * Upload an attachment to a claim
     * 
     * @param claimId Claim ID
     * @param file File to upload
     * @param attachmentType Type of attachment (INVOICE, MEDICAL_REPORT, etc.)
     * @return Uploaded attachment details
     */
    @PostMapping("/{claimId}/attachments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<ClaimAttachmentDto>> uploadAttachment(
            @PathVariable("claimId") Long claimId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("attachmentType") ClaimAttachmentType attachmentType) {
        
        log.info("Upload attachment request: claimId={}, type={}, filename={}", 
                 claimId, attachmentType, file.getOriginalFilename());
        
        try {
            ClaimAttachment attachment = attachmentService.uploadAttachment(claimId, file, attachmentType);
            ClaimAttachmentDto dto = ClaimAttachmentDto.builder()
                .id(attachment.getId())
                .fileName(attachment.getOriginalFileName())
                .fileUrl(attachment.getFileUrl())
                .fileType(attachment.getFileType())
                .fileSize(attachment.getFileSize())
                .attachmentType(attachment.getAttachmentType() != null ? attachment.getAttachmentType().name() : null)
                .createdAt(attachment.getCreatedAt())
                .build();
            return ResponseEntity.ok(ApiResponse.success("Attachment uploaded successfully", dto));
            
        } catch (RuntimeException e) {
            log.error("Failed to upload attachment for claim {}: {}", claimId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Failed to upload attachment: " + e.getMessage()));
        }
    }
    
    /**
     * Get all attachments for a claim
     * 
     * @param claimId Claim ID
     * @return List of attachments
     */
    @GetMapping("/{claimId}/attachments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ClaimAttachmentDto>> getClaimAttachments(@PathVariable("claimId") Long claimId) {
        log.info("📋 Get attachments for claim ID: {}", claimId);

        assertClaimBelongsToCaller(claimId);

        List<ClaimAttachment> attachments = attachmentService.getClaimAttachments(claimId);
        
        log.info("✅ Found {} attachments for claim {}", attachments.size(), claimId);
        
        // Convert to DTOs to avoid lazy loading issues
        List<ClaimAttachmentDto> dtos = attachments.stream()
            .map(att -> {
                ClaimAttachmentDto dto = ClaimAttachmentDto.builder()
                    .id(att.getId())
                    .fileName(att.getOriginalFileName() != null ? att.getOriginalFileName() : att.getFileName())
                    .fileUrl(att.getFileUrl())
                    .fileType(att.getFileType())
                    .fileSize(att.getFileSize())
                    .attachmentType(att.getAttachmentType() != null ? att.getAttachmentType().name() : null)
                    .createdAt(att.getCreatedAt())
                    .build();
                
                // 🔍 DEBUG: Log each attachment ID and details
                log.debug("  → Attachment {}: {} ({})", dto.getId(), dto.getFileName(), dto.getFileType());
                
                return dto;
            })
            .toList();
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * Download a specific attachment
     * 
     * @param claimId Claim ID (for path consistency)
     * @param attachmentId Attachment ID
     * @return File content as Resource
     */
    @GetMapping("/{claimId}/attachments/{attachmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable("claimId") Long claimId,
            @PathVariable("attachmentId") Long attachmentId,
            @RequestParam(value = "inline", required = false, defaultValue = "false") boolean inline) {

        log.info("📥 Download attachment request: claimId={}, attachmentId={}, inline={}", claimId, attachmentId, inline);

        assertClaimBelongsToCaller(claimId);

        ClaimAttachment attachment;
        try {
            attachment = attachmentService.getAttachment(attachmentId);
        } catch (RuntimeException e) {
            log.warn("Attachment {} not found: {}", attachmentId, e.getMessage());
            return ResponseEntity.notFound().build();
        }

        // ⚠️ CRITICAL: Verify attachment belongs to claim (security + data integrity)
        if (!attachment.getClaim().getId().equals(claimId)) {
            log.warn("❌ SECURITY: Attachment {} does NOT belong to claim {}. Actual claim: {}",
                     attachmentId, claimId, attachment.getClaim().getId());
            return ResponseEntity.notFound().build();
        }

        log.info("✅ Attachment verified. FileKey: {}, FileName: {}",
                 attachment.getFileKey(), attachment.getOriginalFileName());

        byte[] fileContent;
        try {
            fileContent = attachmentService.downloadAttachment(attachmentId);
        } catch (RuntimeException e) {
            // DOCUMENTS-INTEGRITY-1: the file record exists but the actual bytes
            // couldn't be read from storage (deleted from disk, permissions,
            // corrupt path, etc.) — this is a real server-side problem, not a
            // "not found" from the caller's point of view. Surfaced as 500 so
            // it's visible/actionable instead of silently looking like a 404.
            log.error("❌ Failed to read stored file for attachment {} (claim {}): {}",
                    attachmentId, claimId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        ByteArrayResource resource = new ByteArrayResource(fileContent);

        log.info("✅ Attachment downloaded successfully. Size: {} bytes", fileContent.length);

        // DOCUMENTS-INTEGRITY-1: never let a null/blank/garbage stored
        // fileType blow up MediaType.parseMediaType() into a misleading 404 —
        // fall back to a generic binary type, which still downloads fine.
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(attachment.getFileType());
        } catch (Exception e) {
            log.warn("Attachment {} has an invalid stored fileType '{}', falling back to octet-stream",
                    attachmentId, attachment.getFileType());
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }

        // Only PDF/JPEG/PNG are ever served inline — Word/Excel always force a
        // real download regardless of the `inline` request, since browsers
        // cannot render them and a forced "preview" would just look broken.
        boolean serveInline = inline && AttachmentFileTypePolicy.isInlinePreviewable(attachment.getFileType());
        String disposition = serveInline
                ? FileResourceUtils.buildInlineContentDisposition(attachment.getOriginalFileName())
                : FileResourceUtils.buildAttachmentContentDisposition(attachment.getOriginalFileName());

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentLength(fileContent.length)
                .body(resource);
    }
    
    /**
     * Delete an attachment
     * 
     * @param claimId Claim ID (for path consistency)
     * @param attachmentId Attachment ID
     * @return Success message
     */
    @DeleteMapping("/{claimId}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'PROVIDER_STAFF')")
    public ResponseEntity<String> deleteAttachment(
            @PathVariable("claimId") Long claimId,
            @PathVariable("attachmentId") Long attachmentId) {
        
        log.info("Delete attachment: claimId={}, attachmentId={}", claimId, attachmentId);

        assertClaimBelongsToCaller(claimId);

        try {
            attachmentService.deleteAttachment(attachmentId);
            return ResponseEntity.ok("Attachment deleted successfully");
            
        } catch (RuntimeException e) {
            log.error("Failed to delete attachment {}: {}", attachmentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to delete attachment: " + e.getMessage());
        }
    }
    
    /**
     * Get attachment count for a claim
     * 
     * @param claimId Claim ID
     * @return Number of attachments
     */
    @GetMapping("/{claimId}/attachments/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getAttachmentCount(@PathVariable("claimId") Long claimId) {
        log.info("Get attachment count for claim ID: {}", claimId);

        assertClaimBelongsToCaller(claimId);

        long count = attachmentService.countAttachments(claimId);
        return ResponseEntity.ok(count);
    }
}

