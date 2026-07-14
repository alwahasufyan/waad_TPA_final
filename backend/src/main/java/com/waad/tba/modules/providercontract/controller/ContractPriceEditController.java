package com.waad.tba.modules.providercontract.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.providercontract.dto.ContractPriceEditDtos.*;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.service.ContractPriceEditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MC-4C simplified direct price editing (2026-07-12). Every operation updates
 * the ACTIVE price list in place with a mandatory audit entry — none creates a
 * new price-list version. Row-level actions for the contract price list tab.
 */
@RestController
@RequestMapping("/api/v1/provider-contracts/{contractId}/pricing")
@RequiredArgsConstructor
@Tag(name = "Contract Price Edits (MC-4C)", description = "Direct audited price-list edits — no new version")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTANT')")
public class ContractPriceEditController {

    private final ContractPriceEditService editService;

    @PostMapping("/items/{itemId}/price-correction")
    @Operation(summary = "Correct one service price directly (audited, no new version)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> correctPrice(
            @PathVariable("contractId") Long contractId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody PriceCorrectionRequest req,
            Authentication auth) {
        ProviderContractPricingItem item = editService.correctPrice(contractId, itemId, req, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(view(item), "Price corrected", "تم تعديل السعر وتسجيله في السجل"));
    }

    @PostMapping("/items")
    @Operation(summary = "Add one service directly to the active price list (audited, no new version)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addService(
            @PathVariable("contractId") Long contractId,
            @Valid @RequestBody AddServiceRequest req,
            Authentication auth) {
        ProviderContractPricingItem item = editService.addService(contractId, req, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(view(item), "Service added", "تمت إضافة الخدمة إلى القائمة السارية"));
    }

    @PostMapping("/items/{itemId}/deactivate")
    @Operation(summary = "Deactivate one service in the active price list (audited, no new version)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivate(
            @PathVariable("contractId") Long contractId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody DeactivateServiceRequest req,
            Authentication auth) {
        ProviderContractPricingItem item = editService.deactivateService(contractId, itemId, req, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(view(item), "Service deactivated", "تم إيقاف الخدمة"));
    }

    @PostMapping("/items/{itemId}/reactivate")
    @Operation(summary = "Reactivate one service in the active price list (audited, no new version)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reactivate(
            @PathVariable("contractId") Long contractId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody ReactivateServiceRequest req,
            Authentication auth) {
        ProviderContractPricingItem item = editService.reactivateService(contractId, itemId, req, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(view(item), "Service reactivated", "تمت إعادة تفعيل الخدمة"));
    }

    @PostMapping("/items/{itemId}/classification")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
    @Operation(summary = "Correct one service classification/code directly (audited, no new version)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> correctClassification(
            @PathVariable("contractId") Long contractId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody ClassificationCorrectionRequest req,
            Authentication auth) {
        ProviderContractPricingItem item = editService.correctClassification(contractId, itemId, req, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(view(item), "Classification corrected", "تم تعديل التصنيف/الكود"));
    }

    @GetMapping("/audit")
    @Operation(summary = "Audit trail of all direct edits for this contract")
    public ResponseEntity<ApiResponse<List<AuditEntry>>> audit(@PathVariable("contractId") Long contractId) {
        return ResponseEntity.ok(ApiResponse.success(editService.auditTrail(contractId)));
    }

    private static Map<String, Object> view(ProviderContractPricingItem item) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("serviceCode", item.getServiceCode());
        m.put("serviceName", item.getServiceName());
        m.put("contractPrice", item.getContractPrice());
        // Use the eager denormalized categoryName column — never touch the LAZY
        // medicalCategory proxy here (the transaction is already closed).
        m.put("categoryName", item.getCategoryName());
        m.put("active", item.getActive());
        return m;
    }
}
