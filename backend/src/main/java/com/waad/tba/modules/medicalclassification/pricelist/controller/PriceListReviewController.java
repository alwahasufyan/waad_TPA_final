package com.waad.tba.modules.medicalclassification.pricelist.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medicalclassification.pricelist.dto.PriceListImportLineDto;
import com.waad.tba.modules.medicalclassification.pricelist.dto.ReviewDecisionDto;
import com.waad.tba.modules.medicalclassification.pricelist.dto.ReviewSummaryDto;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportLineRepository;
import com.waad.tba.modules.medicalclassification.pricelist.service.ReviewService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Medical Classification Workspace API (MC-2): critical-queue browsing,
 * per-line/bulk decisions, and the explicit audited "Approve Remaining" (A5).
 * Every approval feeds the WAAD medical dictionary (owner directive).
 */
@RestController
@RequestMapping("/api/v1/classification/imports/{importId}/review")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Medical Classification — Review Workspace", description = "Critical-queue review + Approve Remaining (MC-2)")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
public class PriceListReviewController {

    private static final Set<String> QUEUES =
            Set.of("UNKNOWN", "LOW_CONFIDENCE", "DUPLICATE", "GUARD");

    private final ReviewService reviewService;
    private final PriceListImportLineRepository lineRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final MedicalServiceRepository serviceRepository;

    @GetMapping("/summary")
    @Operation(summary = "Workspace header: progress, queue breakdown, Approve-Remaining gate, knowledge counter")
    public ResponseEntity<ApiResponse<ReviewSummaryDto>> summary(@PathVariable Long importId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.summary(importId)));
    }

    @GetMapping("/queue/{queue}")
    @Operation(summary = "Browse one critical-queue tab (UNKNOWN / LOW_CONFIDENCE / DUPLICATE / GUARD)")
    public ResponseEntity<ApiResponse<Page<PriceListImportLineDto>>> queue(
            @PathVariable Long importId,
            @PathVariable String queue,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        String q = queue.toUpperCase();
        if (!QUEUES.contains(q)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Unknown queue: " + queue));
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<PriceListImportLine> lines = lineRepository.findQueue(importId, q, pageable);
        return ResponseEntity.ok(ApiResponse.success(lines.map(PriceListImportLineDto::from)));
    }

    @PostMapping("/lines/{lineId}/decide")
    @Operation(summary = "Decide one line (APPROVE with category/service, or REJECT)")
    public ResponseEntity<ApiResponse<PriceListImportLineDto>> decide(
            @PathVariable Long importId,
            @PathVariable Long lineId,
            @RequestBody ReviewDecisionDto decision,
            Authentication authentication) {
        PriceListImportLine line = reviewService.decide(importId, lineId, decision, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                PriceListImportLineDto.from(line), "Decision recorded", "تم تسجيل القرار وإضافته للقاموس"));
    }

    @PostMapping("/lines/decide-bulk")
    @Operation(summary = "Apply one decision to selected lines")
    public ResponseEntity<ApiResponse<Integer>> decideBulk(
            @PathVariable Long importId,
            @RequestBody ReviewDecisionDto decision,
            Authentication authentication) {
        int done = reviewService.decideBulk(importId, decision.getLineIds(), decision, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(done, "Bulk decision applied", "تم تطبيق القرار على " + done + " سطرًا"));
    }

    @PostMapping("/approve-remaining")
    @Operation(summary = "A5: explicitly approve the hidden high-confidence majority — only when the critical queue is empty")
    public ResponseEntity<ApiResponse<Integer>> approveRemaining(
            @PathVariable Long importId, Authentication authentication) {
        int approved = reviewService.approveRemaining(importId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                approved, "Remaining approved", "تم اعتماد " + approved + " سطرًا (اعتماد المتبقي)"));
    }

    @PostMapping("/finish")
    @Operation(summary = "MC-4A: finish review = Approve Remaining (A5) + auto-create DRAFT version + run A10 validation — one user action",
            description = "Requires an empty critical queue. Returns the created version id for navigation to the report.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> finishReview(
            @PathVariable Long importId,
            @RequestParam(value = "contractId", required = false) Long contractId,
            Authentication authentication) {
        var result = reviewService.finishReview(importId, contractId, authentication.getName());
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("bulkApproved", result.bulkApproved());
        data.put("versionId", result.versionId());
        data.put("contractId", result.contractId());
        String msgAr = result.versionId() != null
                ? "اكتملت المراجعة (اعتُمد " + result.bulkApproved() + " موثوقًا) وجُهّز تقرير النسخة"
                : "اكتملت المراجعة — لا يوجد عقد نشط للمرفق؛ اختر العقد لإنشاء النسخة";
        return ResponseEntity.ok(ApiResponse.success(data, "Review finished", msgAr));
    }

    // ── pickers for the decision panel ──────────────────────────────────────

    @GetMapping("/pickers/categories")
    @Operation(summary = "Active WAAD categories for the classification picker")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> categories(@PathVariable Long importId) {
        List<Map<String, Object>> out = categoryRepository.findAll().stream()
                .filter(c -> c.isActive() && !c.isDeleted())
                .map(c -> Map.<String, Object>of(
                        "id", c.getId(),
                        "code", c.getCode(),
                        "name", c.getName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/pickers/services")
    @Operation(summary = "Search catalog services (name/code) for the map-to-service picker")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> services(
            @PathVariable Long importId,
            @RequestParam("q") String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        List<Map<String, Object>> out = serviceRepository.findAll().stream()
                .filter(s -> !s.isDeleted())
                .filter(s -> contains(s.getName(), q) || contains(s.getNameAr(), q)
                        || contains(s.getNameEn(), q) || contains(s.getCode(), q))
                .limit(20)
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(),
                        "code", s.getCode(),
                        "name", s.getName(),
                        "categoryId", s.getCategoryId() == null ? 0L : s.getCategoryId()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
