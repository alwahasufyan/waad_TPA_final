package com.waad.tba.modules.medicalclassification.pricelist.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.medicalclassification.engine.service.ClassificationEngineClient;
import com.waad.tba.modules.medicalclassification.pricelist.dto.PriceListImportDto;
import com.waad.tba.modules.medicalclassification.pricelist.dto.PriceListImportLineDto;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImport;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportLineRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportRepository;
import com.waad.tba.modules.medicalclassification.pricelist.service.ImportOrchestrationService;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Medical Classification Engine — price-list imports (MC-1).
 *
 * Scope guard (owner condition #3): this controller exposes ONLY the import
 * & staging layer (upload / status / staged lines / cancel). Review decisions
 * and publishing are deliberately absent until MC-2/MC-3.
 */
@RestController
@RequestMapping("/api/v1/classification/imports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Medical Classification — Imports", description = "Provider price-list import & staging (MC-1)")
@PreAuthorize("isAuthenticated()")
public class PriceListImportController {

    private final ImportOrchestrationService orchestrationService;
    private final PriceListImportRepository importRepository;
    private final PriceListImportLineRepository lineRepository;
    private final ProviderRepository providerRepository;
    private final ClassificationEngineClient engineClient;
    private final com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListVersionRepository versionRepository;
    private final AuthorizationService authorizationService;
    private final ReviewerProviderIsolationService reviewerIsolationService;

    // WAAD-PRICELIST-REVIEWER-ISOLATION-1: MEDICAL_REVIEWER may upload,
    // list, view, and cancel price-list imports, but only for providers
    // they're actually assigned to (medical_reviewer_providers) — previously
    // providerId was trusted as-is from the request/path with no check at
    // all, letting a reviewer upload or cancel an import for any provider.
    // SUPER_ADMIN/WAAD_ADMIN are unaffected (validateReviewerAccess is a
    // no-op for non-isolated roles).

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Upload a provider price list for classification",
            description = "Idempotent by SHA-256: re-uploading the same file for the same provider is rejected "
                    + "while a non-terminal import exists. Classification runs asynchronously.")
    public ResponseEntity<ApiResponse<PriceListImportDto>> upload(
            @RequestParam("providerId") Long providerId,
            @RequestParam(value = "contractId", required = false) Long contractId,
            @RequestParam(value = "hint", required = false) String hint,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        reviewerIsolationService.validateReviewerAccess(authorizationService.getCurrentUser(), providerId);

        PriceListImport imp = orchestrationService.createImport(
                providerId, contractId, hint, file, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                toDto(imp), "Import registered", "تم استلام الملف وبدأت المعالجة"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "List imports (newest first)")
    public ResponseEntity<ApiResponse<Page<PriceListImportDto>>> list(
            @RequestParam(value = "providerId", required = false) Long providerId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size) {

        User currentUser = authorizationService.getCurrentUser();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));

        if (providerId != null) {
            reviewerIsolationService.validateReviewerAccess(currentUser, providerId);
            return ResponseEntity.ok(ApiResponse.success(
                    importRepository.findByProviderIdOrderByIdDesc(providerId, pageable).map(this::toDto)));
        }

        if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            List<Long> allowedProviderIds = reviewerIsolationService.getAllowedProviderIds(currentUser);
            if (allowedProviderIds.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.success(Page.<PriceListImport>empty(pageable).map(this::toDto)));
            }
            return ResponseEntity.ok(ApiResponse.success(
                    importRepository.findByProviderIdInOrderByIdDesc(allowedProviderIds, pageable).map(this::toDto)));
        }

        return ResponseEntity.ok(ApiResponse.success(
                importRepository.findAllByOrderByIdDesc(pageable).map(this::toDto)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Get one import with full provenance")
    public ResponseEntity<ApiResponse<PriceListImportDto>> get(@PathVariable Long id) {
        PriceListImport imp = importRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Import not found: " + id));
        reviewerIsolationService.validateReviewerAccess(authorizationService.getCurrentUser(), imp.getProviderId());
        return ResponseEntity.ok(ApiResponse.success(toDto(imp)));
    }

    @GetMapping("/{id}/lines")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Browse staged lines (read-only in MC-1)",
            description = "Optional reviewStatus filter: PENDING_BULK / NEEDS_REVIEW / APPROVED / REJECTED")
    public ResponseEntity<ApiResponse<Page<PriceListImportLineDto>>> lines(
            @PathVariable Long id,
            @RequestParam(value = "reviewStatus", required = false) PriceListImportLine.ReviewStatus reviewStatus,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {

        PriceListImport imp = importRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Import not found: " + id));
        reviewerIsolationService.validateReviewerAccess(authorizationService.getCurrentUser(), imp.getProviderId());
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<PriceListImportLine> lines = reviewStatus == null
                ? lineRepository.findByImportIdOrderByRowNoAsc(id, pageable)
                : lineRepository.findByImportIdAndReviewStatusOrderByRowNoAsc(id, reviewStatus, pageable);
        return ResponseEntity.ok(ApiResponse.success(lines.map(PriceListImportLineDto::from)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Cancel an import (UPLOADED/CLASSIFIED/IN_REVIEW only)")
    public ResponseEntity<ApiResponse<PriceListImportDto>> cancel(
            @PathVariable Long id, Authentication authentication) {
        PriceListImport existing = importRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Import not found: " + id));
        reviewerIsolationService.validateReviewerAccess(authorizationService.getCurrentUser(), existing.getProviderId());

        PriceListImport imp = orchestrationService.cancel(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                toDto(imp), "Import cancelled", "تم إلغاء الاستيراد"));
    }

    @GetMapping("/engine/health")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Classification engine availability probe")
    public ResponseEntity<ApiResponse<String>> engineHealth() {
        String problem = engineClient.healthProblem();
        return problem == null
                ? ResponseEntity.ok(ApiResponse.success("OK"))
                : ResponseEntity.ok(ApiResponse.error(problem));
    }

    private PriceListImportDto toDto(PriceListImport imp) {
        String providerName = providerRepository.findById(imp.getProviderId())
                .map(p -> p.getName())
                .orElse(null);
        var version = versionRepository.findFirstBySourceImportIdOrderByIdDesc(imp.getId()).orElse(null);
        return PriceListImportDto.from(imp, providerName,
                version == null ? null : version.getId(),
                version == null ? null : version.getStatus().name());
    }
}
