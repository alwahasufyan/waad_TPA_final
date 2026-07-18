package com.waad.tba.modules.report.service;

import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.report.dto.ProviderReportFilter;
import com.waad.tba.modules.report.dto.ProviderReportResponseDto;
import com.waad.tba.modules.report.dto.ProviderReportRowDto;
import com.waad.tba.modules.report.dto.ProviderReportSummaryDto;
import com.waad.tba.modules.report.repository.ProviderReportQueryRepository;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only Providers report service. Resolves the caller's data scope, runs the
 * projection/aggregate queries, and derives the contract status. It contains NO
 * mutating operation and reuses the existing authorization / reviewer-isolation
 * services (no second authorization model).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderReportService {

    public static final int MAX_EXPORT_ROWS = 50_000;
    public static final int DEFAULT_EXPIRING_SOON_DAYS = 30;

    private final ProviderReportQueryRepository repository;
    private final AuthorizationService authorizationService;
    private final ReviewerProviderIsolationService reviewerIsolation;

    @Transactional(readOnly = true)
    public ProviderReportResponseDto getReport(ProviderReportFilter filter, int expiringSoonDays, Pageable pageable) {
        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(clampDays(expiringSoonDays));
        List<Long> scope = resolveScope();

        Page<ProviderReportRowDto> page = repository.findPage(filter, scope, today, soon, pageable);
        page.getContent().forEach(row -> row.setContractStatus(deriveContractStatus(row, today, soon)));

        ProviderReportSummaryDto summary = repository.findSummary(filter, scope, today, soon);

        return ProviderReportResponseDto.builder()
                .rows(ProviderReportResponseDto.RowsPage.builder()
                        .content(page.getContent())
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalElements(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .build())
                .summary(summary)
                .appliedFilters(describeAppliedFilters(filter, expiringSoonDays))
                .generatedAt(OffsetDateTime.now().toString())
                .build();
    }

    /** Full filtered result for export; bounded to protect memory. */
    @Transactional(readOnly = true)
    public List<ProviderReportRowDto> getExportRows(ProviderReportFilter filter, int expiringSoonDays) {
        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(clampDays(expiringSoonDays));
        List<Long> scope = resolveScope();

        long count = repository.count(filter, scope, today, soon);
        if (count > MAX_EXPORT_ROWS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "نتيجة الفلترة كبيرة جداً للتصدير (" + count + " صف). يرجى تضييق الفلاتر (الحد الأقصى " + MAX_EXPORT_ROWS + ").");
        }

        List<ProviderReportRowDto> rows = repository.findAll(filter, scope, today, soon, MAX_EXPORT_ROWS);
        rows.forEach(row -> row.setContractStatus(deriveContractStatus(row, today, soon)));
        return rows;
    }

    // ── scope ───────────────────────────────────────────────────────────────
    // null  → no restriction (admins / accountant / finance)
    // list  → only these provider ids (provider user = own; reviewer = assigned)
    private List<Long> resolveScope() {
        User user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "غير مصرح");
        }
        if (authorizationService.isProvider(user)) {
            Long pid = user.getProviderId();
            return pid != null ? List.of(pid) : List.of(); // empty → no rows
        }
        if (reviewerIsolation.isSubjectToIsolation(user)) {
            return reviewerIsolation.getAllowedProviderIds(user); // may be empty
        }
        return null; // full access
    }

    // ── derivation ────────────────────────────────────────────────────────────
    private String deriveContractStatus(ProviderReportRowDto r, LocalDate today, LocalDate soon) {
        if (r.getActive() == null || !r.getActive()) {
            return "INACTIVE";
        }
        LocalDate start = r.getContractStartDate();
        LocalDate end = r.getContractEndDate();
        if (start == null && end == null) {
            return "NONE";
        }
        if (end != null && end.isBefore(today)) {
            return "EXPIRED";
        }
        if (start != null && start.isAfter(today)) {
            return "FUTURE";
        }
        if (end != null && !end.isBefore(today) && !end.isAfter(soon)) {
            return "EXPIRING_SOON";
        }
        return "ACTIVE";
    }

    public Map<String, Object> describeAppliedFilters(ProviderReportFilter f, int expiringSoonDays) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (f.getProviderId() != null) m.put("providerId", f.getProviderId());
        if (f.getSearch() != null) m.put("search", f.getSearch());
        if (f.getName() != null) m.put("name", f.getName());
        if (f.getCode() != null) m.put("code", f.getCode());
        if (f.getProviderType() != null) m.put("providerType", f.getProviderType().name());
        if (f.getCity() != null) m.put("city", f.getCity());
        if (f.getActive() != null) m.put("active", f.getActive());
        if (f.getHasActiveContract() != null) m.put("hasActiveContract", f.getHasActiveContract());
        if (f.getHasActivePriceList() != null) m.put("hasActivePriceList", f.getHasActivePriceList());
        if (f.getExpired() != null) m.put("expired", f.getExpired());
        if (f.getExpiringSoon() != null) m.put("expiringSoon", f.getExpiringSoon());
        if (f.getContractStartFrom() != null) m.put("contractStartFrom", f.getContractStartFrom().toString());
        if (f.getContractStartTo() != null) m.put("contractStartTo", f.getContractStartTo().toString());
        if (f.getContractEndFrom() != null) m.put("contractEndFrom", f.getContractEndFrom().toString());
        if (f.getContractEndTo() != null) m.put("contractEndTo", f.getContractEndTo().toString());
        m.put("expiringSoonDays", clampDays(expiringSoonDays));
        return m;
    }

    private int clampDays(int days) {
        if (days <= 0) return DEFAULT_EXPIRING_SOON_DAYS;
        return Math.min(days, 365);
    }

    /** Safe parse of the providerType query param → enum (null when blank/invalid). */
    public static Provider.ProviderType parseProviderType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Provider.ProviderType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
