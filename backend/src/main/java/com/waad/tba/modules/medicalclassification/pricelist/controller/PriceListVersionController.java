package com.waad.tba.modules.medicalclassification.pricelist.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medicalclassification.pricelist.dto.PriceListImportLineDto;
import com.waad.tba.modules.medicalclassification.pricelist.dto.VersionComparisonDto;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListValidationFinding;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListVersion;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListVersionRepository;
import com.waad.tba.modules.medicalclassification.pricelist.service.FinancialValidationService;
import com.waad.tba.modules.medicalclassification.pricelist.service.PriceListVersionService;
import com.waad.tba.modules.medicalclassification.pricelist.service.VersionComparisonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Provider Price List Versions (MC-3) — the FINANCIAL ARTIFACT lifecycle:
 * draft → (A10 validation gate) → approve on the A11 comparison report →
 * publish (immutable ACTIVE, previous version superseded).
 *
 * RBAC segregation of duties: reviewers (MEDICAL_REVIEWER) create drafts and
 * handle findings; ONLY SUPER_ADMIN / ACCOUNTANT approve & publish.
 */
@RestController
@RequestMapping("/api/v1/classification/versions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Medical Classification — Price List Versions", description = "Versioning + Financial Validation + publish gate (MC-3)")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER','ACCOUNTANT')")
public class PriceListVersionController {

    private final PriceListVersionService versionService;
    private final FinancialValidationService validationService;
    private final VersionComparisonService comparisonService;
    private final PriceListVersionRepository versionRepository;
    private final com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository pricingItemRepository;

    @PostMapping("/from-import/{importId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
    @Operation(summary = "Create a DRAFT version from a REVIEW_COMPLETE import (runs A10 validation immediately)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createDraft(
            @PathVariable("importId") Long importId,
            @RequestParam(value = "contractId", required = false) Long contractId,
            Authentication auth) {
        PriceListVersion v = versionService.createDraftFromImport(importId, contractId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(
                headerOf(v), "Draft version created", "أُنشئت النسخة v" + v.getVersionNo() + " (مسودة) وشُغّل التحقق المالي"));
    }

    @PostMapping("/exception/draft/{contractId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
    @Operation(summary = "Create a PATCH draft for MC-4C exception edits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPatchDraft(
            @PathVariable("contractId") Long contractId,
            Authentication auth) {
        PriceListVersion v = versionService.createPatchDraft(contractId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(
                headerOf(v), "Patch Draft created", "أُنشئت نسخة استثنائية v" + v.getVersionNo() + " (مسودة)"));
    }

    @PostMapping("/{versionId}/exception/record")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
    @Operation(summary = "Record a single price change on a PATCH draft")
    public ResponseEntity<ApiResponse<Void>> recordPriceChange(
            @PathVariable("versionId") Long versionId,
            @RequestParam("pricingItemId") Long pricingItemId,
            @RequestParam("newPrice") java.math.BigDecimal newPrice,
            @RequestParam("reason") String reason,
            Authentication auth) {
        versionService.recordPriceChange(versionId, pricingItemId, newPrice, reason, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(null, "Price updated", "صُدقت وعدلت التسعيرة بنجاح"));
    }

    @PostMapping("/{versionId}/exception/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTANT')")
    @Operation(summary = "Publish an approved PATCH draft after its financial gate is green")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publishPatch(
            @PathVariable("versionId") Long versionId,
            Authentication auth) {
        PriceListVersion v = versionService.applyPatchDraft(versionId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(
                headerOf(v), "Patch Published", "تم تفعيل التعديلات الاستثنائية بنجاح"));
    }

    @PostMapping("/{versionId}/exception/add")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
    @Operation(summary = "Add a catalog service to a PATCH draft")
    public ResponseEntity<ApiResponse<Void>> addServiceException(@PathVariable Long versionId,
            @RequestBody Map<String, Object> body, Authentication auth) {
        Long serviceId = Long.valueOf(body.get("medicalServiceId").toString());
        BigDecimal price = new BigDecimal(body.get("price").toString());
        String reason = body.get("reason") == null ? null : body.get("reason").toString();
        versionService.addServiceException(versionId, serviceId, price, reason, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(null, "Service added", "أضيفت الخدمة إلى مسودة التعديل"));
    }

    @PostMapping("/{versionId}/exception/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
    @Operation(summary = "Deactivate a service in a PATCH draft")
    public ResponseEntity<ApiResponse<Void>> deactivateServiceException(@PathVariable Long versionId,
            @RequestParam Long pricingItemId, @RequestParam String reason, Authentication auth) {
        versionService.deactivateServiceException(versionId, pricingItemId, reason, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(null, "Service deactivated", "أوقفت الخدمة في مسودة التعديل"));
    }

    @PostMapping("/{versionId}/rollback")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTANT')")
    @Operation(summary = "Create a governed ROLLBACK draft from a historical version")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rollback(
            @PathVariable("versionId") Long versionId, Authentication auth) {
        PriceListVersion v = versionService.createRollbackDraft(versionId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(headerOf(v), "Rollback draft created",
                "أُنشئت مسودة استرجاع خاضعة للفحص والاعتماد قبل النشر"));
    }

    @GetMapping("/{versionId}/price-change-audit")
    @Operation(summary = "MC-4C price-change audit trail for a version")
    public ResponseEntity<ApiResponse<List<com.waad.tba.modules.medicalclassification.pricelist.entity.PriceChangeAudit>>> priceChangeAudit(
            @PathVariable("versionId") Long versionId) {
        versionService.getVersion(versionId);
        return ResponseEntity.ok(ApiResponse.success(versionService.priceChangeAudit(versionId)));
    }

    @GetMapping
    @Operation(summary = "Versions of a contract (newest first)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam("contractId") Long contractId) {
        List<Map<String, Object>> out = versionRepository
                .findByContractIdOrderByVersionNoDesc(contractId).stream()
                .map(PriceListVersionController::headerOf)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/contract/{contractId}/summary")
    @Operation(summary = "MC-4B: contract price-list card — active version + brief history (D4) + draft indicator",
            description = "Consumed by the contract's read-only قائمة الأسعار tab (design review §4).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> contractSummary(@PathVariable("contractId") Long contractId) {
        List<PriceListVersion> versions = versionRepository.findByContractIdOrderByVersionNoDesc(contractId);

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        PriceListVersion active = versions.stream()
                .filter(v -> v.getStatus() == PriceListVersion.Status.ACTIVE)
                .findFirst().orElse(null);
        if (active != null) {
            Map<String, Object> card = headerOf(active);
            var stats = pricingItemRepository.findByContractIdAndActiveTrue(contractId);
            card.put("serviceCount", stats.size());
            card.put("totalValue", stats.stream()
                    .map(i -> i.getContractPrice() == null ? java.math.BigDecimal.ZERO : i.getContractPrice())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
            out.put("activeVersion", card);
        }
        versions.stream()
                .filter(v -> v.getStatus() == PriceListVersion.Status.DRAFT)
                .findFirst()
                .ifPresent(d -> out.put("draft", headerOf(d)));
        // D4: brief history only — version, date, status
        out.put("history", versions.stream()
                .filter(v -> v.getStatus() != PriceListVersion.Status.DRAFT)
                .map(v -> Map.<String, Object>of(
                        "id", v.getId(),
                        "versionNo", v.getVersionNo(),
                        "status", v.getStatus().name(),
                        "date", v.getPublishedAt() != null ? v.getPublishedAt().toString()
                                : String.valueOf(v.getCreatedAt())))
                .toList());
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/{versionId}/comparison")
    @Operation(summary = "A11: the approval artifact — statistical comparison vs the previous version + gate state")
    public ResponseEntity<ApiResponse<VersionComparisonDto>> comparison(@PathVariable("versionId") Long versionId) {
        return ResponseEntity.ok(ApiResponse.success(comparisonService.compare(versionId)));
    }

    @GetMapping("/{versionId}/findings")
    @Operation(summary = "A10 findings (all statuses — audit included)")
    public ResponseEntity<ApiResponse<List<PriceListValidationFinding>>> findings(@PathVariable("versionId") Long versionId) {
        return ResponseEntity.ok(ApiResponse.success(validationService.findings(versionId)));
    }

    @PostMapping("/{versionId}/validate")
    @Operation(summary = "Re-run the Financial Validation Engine (DRAFT only)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validate(
            @PathVariable("versionId") Long versionId, Authentication auth) {
        FinancialValidationService.GateState gate = validationService.validate(versionId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "openBlockers", gate.getOpenBlockers(),
                "openWarnings", gate.getOpenWarnings(),
                "publishGateOpen", gate.isOpen())));
    }

    @PostMapping("/{versionId}/findings/{findingId}/resolve")
    @Operation(summary = "Mark a finding RESOLVED (after fixing the underlying line)")
    public ResponseEntity<ApiResponse<PriceListValidationFinding>> resolve(
            @PathVariable("versionId") Long versionId, @PathVariable("findingId") Long findingId,
            @RequestBody(required = false) Map<String, String> body, Authentication auth) {
        String note = body == null ? null : body.get("note");
        return ResponseEntity.ok(ApiResponse.success(
                validationService.resolve(versionId, findingId, note, auth.getName())));
    }

    @PostMapping("/{versionId}/findings/{findingId}/waive")
    @Operation(summary = "WAIVE a WARNING with an audited note (blockers can never be waived)")
    public ResponseEntity<ApiResponse<PriceListValidationFinding>> waive(
            @PathVariable("versionId") Long versionId, @PathVariable("findingId") Long findingId,
            @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                validationService.waive(versionId, findingId, body.get("note"), auth.getName())));
    }

    @PatchMapping("/{versionId}/lines/{lineId}/price")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
    @Operation(summary = "Blocker-fix path: adjust an approved line's price while the version is DRAFT (audited)")
    public ResponseEntity<ApiResponse<PriceListImportLineDto>> fixPrice(
            @PathVariable("versionId") Long versionId, @PathVariable("lineId") Long lineId,
            @RequestBody Map<String, Object> body, Authentication auth) {
        PriceListVersion v = versionService.getVersion(versionId);
        BigDecimal price = body.get("price") == null ? null : new BigDecimal(body.get("price").toString());
        String note = body.get("note") == null ? null : body.get("note").toString();
        var line = versionService.fixLinePrice(v.getSourceImportId(), lineId, price, note, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(
                PriceListImportLineDto.from(line), "Price fixed", "عُدّل السعر وسُجّل التعديل — أعد تشغيل التحقق"));
    }

    @PostMapping("/{versionId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTANT')")
    @Operation(summary = "Approve the version ON the comparison report (segregated duty)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
            @PathVariable("versionId") Long versionId, Authentication auth) {
        PriceListVersion v = versionService.approve(versionId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(headerOf(v), "Version approved", "اعتُمدت النسخة على تقرير المقارنة"));
    }

    @PostMapping("/{versionId}/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTANT')")
    @Operation(summary = "Publish: A10 gate re-checked, rows inserted (version-tagged), previous version superseded — immutable afterwards")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(
            @PathVariable("versionId") Long versionId, Authentication auth) {
        PriceListVersion v = versionService.publish(versionId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(
                headerOf(v), "Version published", "نُشرت النسخة v" + v.getVersionNo() + " وأصبحت المرجع المالي النافذ"));
    }

    @PostMapping("/{versionId}/archive")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
    @Operation(summary = "Archive a DRAFT (published versions are immutable)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> archive(
            @PathVariable("versionId") Long versionId, Authentication auth) {
        PriceListVersion v = versionService.archiveDraft(versionId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(headerOf(v), "Draft archived", "أُرشفت المسودة"));
    }

    private static Map<String, Object> headerOf(PriceListVersion v) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("versionNo", v.getVersionNo());
        m.put("status", v.getStatus().name());
        m.put("sourceType", v.getSourceType() == null ? null : v.getSourceType().name());
        m.put("contractId", v.getContractId());
        m.put("providerId", v.getProviderId());
        m.put("sourceImportId", v.getSourceImportId());
        m.put("effectiveFrom", v.getEffectiveFrom());
        m.put("effectiveTo", v.getEffectiveTo());
        m.put("approvedBy", v.getApprovedBy());
        m.put("approvedAt", v.getApprovedAt());
        m.put("publishedBy", v.getPublishedBy());
        m.put("publishedAt", v.getPublishedAt());
        m.put("notes", v.getNotes());
        return m;
    }
}
