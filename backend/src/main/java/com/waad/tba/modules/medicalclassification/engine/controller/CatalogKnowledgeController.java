package com.waad.tba.modules.medicalclassification.engine.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medicalclassification.engine.service.CatalogKnowledgeService;
import com.waad.tba.modules.medicaltaxonomy.entity.ServiceAlias;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * MC-6 Lite — minimal technical/admin surface over the WAAD medical-service
 * learning loop (aliases + classification history built by MC-2/MC-4C). No
 * dashboard, no charts — just enough visibility to audit and correct the
 * dictionary the classification engine consults on every import.
 */
@RestController
@RequestMapping("/api/v1/medical-classification/knowledge")
@RequiredArgsConstructor
@Tag(name = "Medical Classification — Knowledge Base", description = "MC-6 Lite: inspect and correct the learned service dictionary")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
public class CatalogKnowledgeController {

    private final CatalogKnowledgeService knowledgeService;

    public record ManualAliasRequest(@NotBlank String aliasText, String locale) {
    }

    public record MatchInspectionRequest(String rawName, String rawNameAlt) {
    }

    @GetMapping("/services/{serviceId}")
    @Operation(summary = "View one service's learned knowledge: aliases + classification-history count")
    public ResponseEntity<ApiResponse<CatalogKnowledgeService.ServiceKnowledgeView>> viewService(
            @PathVariable Long serviceId) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeService.viewService(serviceId)));
    }

    @GetMapping("/inspect-match")
    @Operation(summary = "Inspect whether a raw name/alt would auto-match from learned knowledge, and to which service")
    public ResponseEntity<ApiResponse<CatalogKnowledgeService.MatchInspection>> inspectMatch(
            @RequestParam(value = "rawName", required = false) String rawName,
            @RequestParam(value = "rawNameAlt", required = false) String rawNameAlt) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeService.inspectMatch(rawName, rawNameAlt)));
    }

    @PostMapping("/services/{serviceId}/aliases")
    @Operation(summary = "Manually add a learned alias for a catalog service (MANUAL source, audited)")
    public ResponseEntity<ApiResponse<ServiceAlias>> addAlias(
            @PathVariable Long serviceId,
            @org.springframework.web.bind.annotation.RequestBody ManualAliasRequest req,
            Authentication auth) {
        ServiceAlias alias = knowledgeService.addManualAlias(serviceId, req.aliasText(), req.locale(), auth.getName());
        return ResponseEntity.ok(ApiResponse.success(alias, "Alias added", "أُضيف المرادف إلى قاموس وعد الطبي"));
    }

    @PostMapping("/aliases/{aliasId}/deactivate")
    @Operation(summary = "Deactivate a learned alias (kept for audit; stops feeding future auto-matching)")
    public ResponseEntity<ApiResponse<Void>> deactivateAlias(@PathVariable Long aliasId, Authentication auth) {
        knowledgeService.deactivateAlias(aliasId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(null, "Alias deactivated", "تم إيقاف المرادف"));
    }
}
