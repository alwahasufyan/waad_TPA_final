package com.waad.tba.modules.medicaltaxonomy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medicaltaxonomy.dto.MedicalServiceLookupDto;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medical-services")
@RequiredArgsConstructor
@Tag(name = "Medical Services Lookup", description = "Canonical service lookup for selectors")
public class MedicalServiceLookupController {

    private static final int MAX_LOOKUP_RESULTS = 50;

    private final MedicalServiceRepository medicalServiceRepository;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MedicalServiceLookupController.class);

    @GetMapping("/lookup")
    @Operation(summary = "Lookup active medical services by code, name, or category")
    public ResponseEntity<ApiResponse<List<MedicalServiceLookupDto>>> lookup(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "categoryId", required = false) Long categoryId) {

        // MC-4C requirement: this endpoint must ALWAYS return 200 with a list
        // (empty when nothing matches) — never a 500 — for empty/short/Arabic/
        // English queries. Any lookup failure degrades to an empty result.
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        try {
            List<MedicalServiceLookupDto> results = medicalServiceRepository
                    .lookupServices(normalizedQuery, categoryId)
                    .stream()
                    .limit(MAX_LOOKUP_RESULTS)
                    .map(this::toDto)
                    .toList();
            return ResponseEntity.ok(ApiResponse.success(results));
        } catch (Exception e) {
            log.warn("[LOOKUP] Medical-service lookup failed for q='{}', categoryId={} — returning empty list: {}",
                    normalizedQuery, categoryId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
    }

    private MedicalServiceLookupDto toDto(MedicalServiceRepository.MedicalServiceLookupProjection projection) {
        return MedicalServiceLookupDto.builder()
                .id(projection.getId())
                .code(projection.getCode())
                .name(projection.getName())
                .nameAr(projection.getNameAr())
                .nameEn(projection.getNameEn())
                .categoryId(projection.getCategoryId())
                .categoryName(projection.getCategoryName())
                .categoryNameAr(projection.getCategoryNameAr())
                .categoryNameEn(projection.getCategoryNameEn())
                .build();
    }
}
