package com.waad.tba.modules.report.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.report.dto.ProviderReportFilter;
import com.waad.tba.modules.report.dto.ProviderReportResponseDto;
import com.waad.tba.modules.report.dto.ProviderReportRowDto;
import com.waad.tba.modules.report.export.ProviderReportExcelExporter;
import com.waad.tba.modules.report.service.ProviderReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Providers report — READ-ONLY. No create/update/delete/approve endpoints exist
 * under this path by design. Standardized on /api/v1/reports/*.
 */
@RestController
@RequestMapping("/api/v1/reports/providers")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
public class ProviderReportController {

    private final ProviderReportService service;
    private final ProviderReportExcelExporter excelExporter;

    @GetMapping
    public ResponseEntity<ApiResponse<ProviderReportResponseDto>> getReport(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String providerType,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean hasActiveContract,
            @RequestParam(required = false) Boolean hasActivePriceList,
            @RequestParam(required = false) Boolean expired,
            @RequestParam(required = false) Boolean expiringSoon,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractStartFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractStartTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractEndFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractEndTo,
            @RequestParam(defaultValue = "30") int expiringSoonDays,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        ProviderReportFilter filter = buildFilter(providerId, search, name, code, providerType, city, active,
                hasActiveContract, hasActivePriceList, expired, expiringSoon,
                contractStartFrom, contractStartTo, contractEndFrom, contractEndTo);

        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy));

        ProviderReportResponseDto result = service.getReport(filter, expiringSoonDays, pageable);
        return ResponseEntity.ok(ApiResponse.success("تم توليد تقرير مقدمي الخدمة", result));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String providerType,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean hasActiveContract,
            @RequestParam(required = false) Boolean hasActivePriceList,
            @RequestParam(required = false) Boolean expired,
            @RequestParam(required = false) Boolean expiringSoon,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractStartFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractStartTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractEndFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractEndTo,
            @RequestParam(defaultValue = "30") int expiringSoonDays) {

        ProviderReportFilter filter = buildFilter(providerId, search, name, code, providerType, city, active,
                hasActiveContract, hasActivePriceList, expired, expiringSoon,
                contractStartFrom, contractStartTo, contractEndFrom, contractEndTo);

        List<ProviderReportRowDto> rows = service.getExportRows(filter, expiringSoonDays);
        byte[] bytes = excelExporter.export(rows, "تقرير مقدمي الخدمة", service.describeAppliedFilters(filter, expiringSoonDays));

        String filename = "تقرير_مقدمي_الخدمة_" + LocalDate.now() + ".xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"providers-report.xlsx\"; filename*=UTF-8''" + encoded);
        return new ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private ProviderReportFilter buildFilter(
            Long providerId, String search, String name, String code, String providerType, String city, Boolean active,
            Boolean hasActiveContract, Boolean hasActivePriceList, Boolean expired, Boolean expiringSoon,
            LocalDate contractStartFrom, LocalDate contractStartTo, LocalDate contractEndFrom, LocalDate contractEndTo) {
        return ProviderReportFilter.builder()
                .providerId(providerId)
                .search(blankToNull(search))
                .name(blankToNull(name))
                .code(blankToNull(code))
                .providerType(ProviderReportService.parseProviderType(providerType))
                .city(blankToNull(city))
                .active(active)
                .hasActiveContract(hasActiveContract)
                .hasActivePriceList(hasActivePriceList)
                .expired(expired)
                .expiringSoon(expiringSoon)
                .contractStartFrom(contractStartFrom)
                .contractStartTo(contractStartTo)
                .contractEndFrom(contractEndFrom)
                .contractEndTo(contractEndTo)
                .build();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static int clampSize(int size) {
        if (size <= 0) return 25;
        return Math.min(size, 200);
    }
}
