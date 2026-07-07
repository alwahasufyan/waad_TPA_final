package com.waad.tba.modules.claim.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimAttachment;
import com.waad.tba.modules.claim.entity.ClaimAttachmentType;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.entity.ClaimType;
import com.waad.tba.modules.claim.entity.ClaimType.AttachmentCategory;

import lombok.extern.slf4j.Slf4j;

/**
 * Attachment Rules Service.
 * 
 * Validates that claims have required attachments before submission.
 * Each ClaimType defines which attachment categories are required.
 * 
 * RULES:
 * 1. Claims cannot transition to SUBMITTED without required attachments
 * 2. Required attachments vary by ClaimType
 * 3. Pre-approval document required for certain claim types (e.g., SURGERY)
 * 4. Missing attachments generate blocking errors
 * 5. Optional attachments generate warnings only
 * 
 * @since Phase 7 - Operational Completeness
 */
@Slf4j
@Service
public class AttachmentRulesService {
    
    /**
     * Validate if a claim has all required attachments for submission.
     * 
     * @param claim The claim to validate
     * @param claimType The type of claim (determines required attachments)
     * @return ValidationResult with errors and warnings
     */
    public AttachmentValidationResult validateForSubmission(Claim claim, ClaimType claimType) {
        if (claimType == null) {
            claimType = ClaimType.GENERAL; // Default to general rules
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        Set<AttachmentCategory> presentCategories = extractCategories(claim.getAttachments());
        Set<AttachmentCategory> requiredCategories = claimType.getRequiredAttachments();
        Set<AttachmentCategory> optionalCategories = claimType.getOptionalAttachments();
        
        // Check required attachments
        Set<AttachmentCategory> missingRequired = new HashSet<>(requiredCategories);
        missingRequired.removeAll(presentCategories);
        
        for (AttachmentCategory missing : missingRequired) {
            errors.add("Required attachment missing: " + missing.name() + " (" + missing.getArabicLabel() + ")");
        }
        
        // Check optional attachments - just warn
        Set<AttachmentCategory> missingOptional = new HashSet<>(optionalCategories);
        missingOptional.removeAll(presentCategories);
        
        for (AttachmentCategory missing : missingOptional) {
            warnings.add("Recommended attachment not provided: " + missing.name() + " (" + missing.getArabicLabel() + ")");
        }
        
        // Check for pre-authorization requirement (ARCHITECTURAL UPDATE 2026-01-15)
        if (claimType.requiresPreApproval() && claim.getPreAuthorization() == null) {
            errors.add("Pre-authorization is required for " + claimType.name() + " claims but none is linked");
        }
        
        // Check attachment count
        if (claim.getAttachments() == null || claim.getAttachments().isEmpty()) {
            errors.add("At least one attachment is required to submit a claim");
        }
        
        boolean valid = errors.isEmpty();
        
        log.debug("Attachment validation for claim {}: valid={}, errors={}, warnings={}", 
            claim.getId(), valid, errors.size(), warnings.size());
        
        return new AttachmentValidationResult(valid, claimType, errors, warnings, presentCategories, missingRequired);
    }
    
    /**
     * Check if a claim can transition to a specific status based on attachments.
     * 
     * @param claim The claim
     * @param targetStatus The target status
     * @param claimType The claim type
     * @return true if attachments are sufficient for the transition
     */
    public boolean canTransitionTo(Claim claim, ClaimStatus targetStatus, ClaimType claimType) {
        // Only check attachments when moving to SUBMITTED
        if (targetStatus != ClaimStatus.SUBMITTED) {
            return true;
        }
        
        AttachmentValidationResult result = validateForSubmission(claim, claimType);
        return result.valid();
    }
    
    /**
     * Get the attachment requirements for a claim type.
     * 
     * @param claimType The claim type
     * @return AttachmentRequirements with required and optional categories
     */
    public AttachmentRequirements getRequirements(ClaimType claimType) {
        if (claimType == null) {
            claimType = ClaimType.GENERAL;
        }
        
        return new AttachmentRequirements(
            claimType,
            claimType.getRequiredAttachments(),
            claimType.getOptionalAttachments(),
            claimType.requiresPreApproval()
        );
    }
    
    /**
     * Categorize an attachment based on its explicit type or file name.
     * Priority: 1) Explicit attachmentType set by user, 2) Filename pattern matching
     * 
     * @param attachment The attachment to categorize
     * @return AttachmentCategory based on explicit type or inferred from filename
     */
    public AttachmentCategory categorizeAttachment(ClaimAttachment attachment) {
        if (attachment == null) {
            return AttachmentCategory.OTHER;
        }
        
        // PRIORITY 1: Use explicit attachmentType if set by user
        if (attachment.getAttachmentType() != null) {
            return mapExplicitTypeToCategory(attachment.getAttachmentType());
        }
        
        // PRIORITY 2: Fallback to filename pattern matching
        if (attachment.getFileName() == null) {
            return AttachmentCategory.OTHER;
        }
        
        String fileName = attachment.getFileName().toLowerCase();
        
        // Pattern matching for common file naming conventions
        if (fileName.contains("medical") || fileName.contains("report") || fileName.contains("تقرير")) {
            return AttachmentCategory.MEDICAL_REPORT;
        }
        if (fileName.contains("lab") || fileName.contains("result") || fileName.contains("مختبر")) {
            return AttachmentCategory.LAB_RESULTS;
        }
        if (fileName.contains("radiology") || fileName.contains("xray") || fileName.contains("أشعة")) {
            return AttachmentCategory.RADIOLOGY_REPORT;
        }
        if (fileName.contains("prescription") || fileName.contains("rx") || fileName.contains("وصفة")) {
            return AttachmentCategory.PRESCRIPTION;
        }
        if (fileName.contains("discharge") || fileName.contains("خروج")) {
            return AttachmentCategory.DISCHARGE_SUMMARY;
        }
        if (fileName.contains("bill") || fileName.contains("invoice") || fileName.contains("فاتورة")) {
            return AttachmentCategory.ITEMIZED_BILL;
        }
        if (fileName.contains("emergency") || fileName.contains("er_") || fileName.contains("طوارئ")) {
            return AttachmentCategory.ER_REPORT;
        }
        if (fileName.contains("referral") || fileName.contains("إحالة")) {
            return AttachmentCategory.REFERRAL;
        }
        if (fileName.contains("dental") || fileName.contains("أسنان")) {
            return AttachmentCategory.DENTAL_REPORT;
        }
        if (fileName.contains("eye") || fileName.contains("vision") || fileName.contains("عيون")) {
            return AttachmentCategory.EYE_EXAM_REPORT;
        }
        if (fileName.contains("preapproval") || fileName.contains("pre-approval") || fileName.contains("موافقة")) {
            return AttachmentCategory.PREAPPROVAL;
        }
        if (fileName.contains("surgery") || fileName.contains("surgical") || fileName.contains("جراح")) {
            return AttachmentCategory.SURGICAL_REPORT;
        }
        if (fileName.contains("ultrasound") || fileName.contains("سونار")) {
            return AttachmentCategory.ULTRASOUND_REPORT;
        }
        if (fileName.contains("therapy") || fileName.contains("physio") || fileName.contains("علاج")) {
            return AttachmentCategory.THERAPY_REPORT;
        }
        
        return AttachmentCategory.OTHER;
    }
    
    /**
     * Map explicit ClaimAttachmentType to AttachmentCategory.
     * This ensures user-selected attachment types are properly recognized.
     * 
     * @param type The explicit attachment type set by user
     * @return Corresponding AttachmentCategory
     */
    private AttachmentCategory mapExplicitTypeToCategory(ClaimAttachmentType type) {
        return switch (type) {
            case MEDICAL_REPORT -> AttachmentCategory.MEDICAL_REPORT;
            case INVOICE -> AttachmentCategory.ITEMIZED_BILL;
            case PRESCRIPTION -> AttachmentCategory.PRESCRIPTION;
            case LAB_RESULT -> AttachmentCategory.LAB_RESULTS;
            case XRAY -> AttachmentCategory.RADIOLOGY_REPORT;
            case OTHER -> AttachmentCategory.OTHER;
        };
    }
    
    /**
     * Extract attachment categories from a list of attachments.
     */
    private Set<AttachmentCategory> extractCategories(List<ClaimAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Set.of();
        }
        
        Set<AttachmentCategory> categories = new HashSet<>();
        for (ClaimAttachment attachment : attachments) {
            categories.add(categorizeAttachment(attachment));
        }
        return categories;
    }
    
    // ==================== Record Types ====================
    
    /**
     * Result of attachment validation.
     */
    public record AttachmentValidationResult(
        boolean valid,
        ClaimType claimType,
        List<String> errors,
        List<String> warnings,
        Set<AttachmentCategory> presentCategories,
        Set<AttachmentCategory> missingRequired
    ) {
        /**
         * Get user-friendly error message combining all errors.
         */
        public String getErrorMessage() {
            if (errors.isEmpty()) {
                return null;
            }
            return "Missing required attachments: " + String.join(", ", errors);
        }
    }
    
    /**
     * Attachment requirements for a claim type.
     */
    public record AttachmentRequirements(
        ClaimType claimType,
        Set<AttachmentCategory> required,
        Set<AttachmentCategory> optional,
        boolean requiresPreApproval
    ) {}
}
