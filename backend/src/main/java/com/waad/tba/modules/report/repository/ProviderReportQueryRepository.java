package com.waad.tba.modules.report.repository;

import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListVersion;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.report.dto.ProviderReportFilter;
import com.waad.tba.modules.report.dto.ProviderReportRowDto;
import com.waad.tba.modules.report.dto.ProviderReportSummaryDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only, dynamic-JPQL query layer for the Providers report.
 *
 * Design notes:
 *  - Entities are never returned; only {@link ProviderReportRowDto} projections.
 *  - Filters are appended only when present → no null-parameter or empty-IN pitfalls.
 *  - Price-list facts come from correlated subqueries (single query, no N+1).
 *  - Sort is restricted to an allow-list; unknown columns fall back to name ASC.
 *  - Rows, count and summary share the SAME WHERE, so the summary always
 *    reconciles with the filtered result count.
 */
@Repository
public class ProviderReportQueryRepository {

    @PersistenceContext
    private EntityManager em;

    private static final ProviderContract.ContractStatus CONTRACT_ACTIVE = ProviderContract.ContractStatus.ACTIVE;

    // sortBy (API) → JPQL path allow-list. Anything else is rejected → default.
    private static final Map<String, String> SORTABLE = Map.of(
            "name", "p.name",
            "code", "p.licenseNumber",
            "providerType", "p.providerType",
            "city", "p.city",
            "active", "p.active",
            "contractStartDate", "p.contractStartDate",
            "contractEndDate", "p.contractEndDate",
            "updatedAt", "p.updatedAt");

    private static final String ROW_SELECT =
            "SELECT new com.waad.tba.modules.report.dto.ProviderReportRowDto(" +
            " p.id, p.licenseNumber, p.name, p.providerType, p.city, p.active, p.networkStatus," +
            " p.contractStartDate, p.contractEndDate, p.updatedAt," +
            " (SELECT COUNT(v) FROM PriceListVersion v WHERE v.providerId = p.id AND v.status = :activeStatus)," +
            " (SELECT MAX(v2.versionNo) FROM PriceListVersion v2 WHERE v2.providerId = p.id AND v2.status = :activeStatus)," +
            " (SELECT MAX(c.contractCode) FROM ModernProviderContract c WHERE c.provider.id = p.id AND c.status = :contractActiveStatus)" +
            ") FROM Provider p";

    private static final String SUMMARY_SELECT =
            "SELECT COUNT(p)," +
            " SUM(CASE WHEN p.active = true THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN p.active = false OR p.active IS NULL THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN (p.active = true AND (p.contractStartDate IS NULL OR p.contractStartDate <= :today)" +
            "   AND (p.contractEndDate IS NULL OR p.contractEndDate >= :today)) THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN (SELECT COUNT(v) FROM PriceListVersion v WHERE v.providerId = p.id AND v.status = :activeStatus) > 0 THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN (p.contractEndDate IS NOT NULL AND p.contractEndDate < :today) THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN (p.contractEndDate IS NOT NULL AND p.contractEndDate >= :today AND p.contractEndDate <= :expirySoon) THEN 1 ELSE 0 END)" +
            " FROM Provider p";

    // ── public API ────────────────────────────────────────────────────────────

    public Page<ProviderReportRowDto> findPage(
            ProviderReportFilter f, List<Long> providerIds, LocalDate today, LocalDate expirySoonDate, Pageable pageable) {

        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(f, providerIds, today, expirySoonDate, params);

        // rows: SELECT always references :activeStatus + :contractActiveStatus (subqueries)
        Map<String, Object> rowParams = new HashMap<>(params);
        rowParams.putIfAbsent("activeStatus", PriceListVersion.Status.ACTIVE);
        rowParams.putIfAbsent("contractActiveStatus", CONTRACT_ACTIVE);

        TypedQuery<ProviderReportRowDto> query =
                em.createQuery(ROW_SELECT + where + orderBy(pageable.getSort()), ProviderReportRowDto.class);
        applyParams(query, rowParams);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<ProviderReportRowDto> content = query.getResultList();

        Query countQuery = em.createQuery("SELECT COUNT(p) FROM Provider p" + where);
        applyParams(countQuery, params);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(content, pageable, total);
    }

    /** Full filtered result for export (bounded by the caller via maxRows). */
    public List<ProviderReportRowDto> findAll(
            ProviderReportFilter f, List<Long> providerIds, LocalDate today, LocalDate expirySoonDate, int maxRows) {

        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(f, providerIds, today, expirySoonDate, params);
        params.putIfAbsent("activeStatus", PriceListVersion.Status.ACTIVE);
        params.putIfAbsent("contractActiveStatus", CONTRACT_ACTIVE);

        TypedQuery<ProviderReportRowDto> query =
                em.createQuery(ROW_SELECT + where + " ORDER BY p.name ASC", ProviderReportRowDto.class);
        applyParams(query, params);
        query.setMaxResults(maxRows);
        return query.getResultList();
    }

    public long count(ProviderReportFilter f, List<Long> providerIds, LocalDate today, LocalDate expirySoonDate) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(f, providerIds, today, expirySoonDate, params);
        Query countQuery = em.createQuery("SELECT COUNT(p) FROM Provider p" + where);
        applyParams(countQuery, params);
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    public ProviderReportSummaryDto findSummary(
            ProviderReportFilter f, List<Long> providerIds, LocalDate today, LocalDate expirySoonDate) {

        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(f, providerIds, today, expirySoonDate, params);
        // SUMMARY_SELECT always references these three params:
        params.put("today", today);
        params.put("expirySoon", expirySoonDate);
        params.put("activeStatus", PriceListVersion.Status.ACTIVE);

        Query query = em.createQuery(SUMMARY_SELECT + where);
        applyParams(query, params);
        Object[] r = (Object[]) query.getResultList().get(0);

        long total = toLong(r[0]);
        long active = toLong(r[1]);
        long inactive = toLong(r[2]);
        long withActiveContracts = toLong(r[3]);
        long withActivePriceLists = toLong(r[4]);
        long expired = toLong(r[5]);
        long expiringSoon = toLong(r[6]);

        return ProviderReportSummaryDto.builder()
                .totalProviders(total)
                .activeProviders(active)
                .inactiveProviders(inactive)
                .withActiveContracts(withActiveContracts)
                .withoutActiveContracts(total - withActiveContracts)
                .withActivePriceLists(withActivePriceLists)
                .withoutActivePriceLists(total - withActivePriceLists)
                .expiredContracts(expired)
                .expiringSoonContracts(expiringSoon)
                .build();
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private String buildWhere(
            ProviderReportFilter f, List<Long> providerIds, LocalDate today, LocalDate expirySoonDate, Map<String, Object> params) {

        StringBuilder w = new StringBuilder(" WHERE 1 = 1");

        if (f.getProviderId() != null) {
            w.append(" AND p.id = :providerId");
            params.put("providerId", f.getProviderId());
        }
        if (StringUtils.hasText(f.getSearch())) {
            w.append(" AND (LOWER(p.name) LIKE :search OR LOWER(p.licenseNumber) LIKE :search OR LOWER(COALESCE(p.city, '')) LIKE :search)");
            params.put("search", like(f.getSearch()));
        }
        if (StringUtils.hasText(f.getName())) {
            w.append(" AND LOWER(p.name) LIKE :name");
            params.put("name", like(f.getName()));
        }
        if (StringUtils.hasText(f.getCode())) {
            w.append(" AND LOWER(p.licenseNumber) LIKE :code");
            params.put("code", like(f.getCode()));
        }
        if (f.getProviderType() != null) {
            w.append(" AND p.providerType = :providerType");
            params.put("providerType", f.getProviderType());
        }
        if (StringUtils.hasText(f.getCity())) {
            w.append(" AND LOWER(COALESCE(p.city, '')) LIKE :city");
            params.put("city", like(f.getCity()));
        }
        if (f.getActive() != null) {
            w.append(" AND p.active = :active");
            params.put("active", f.getActive());
        }
        if (f.getContractStartFrom() != null) {
            w.append(" AND p.contractStartDate >= :cStartFrom");
            params.put("cStartFrom", f.getContractStartFrom());
        }
        if (f.getContractStartTo() != null) {
            w.append(" AND p.contractStartDate <= :cStartTo");
            params.put("cStartTo", f.getContractStartTo());
        }
        if (f.getContractEndFrom() != null) {
            w.append(" AND p.contractEndDate >= :cEndFrom");
            params.put("cEndFrom", f.getContractEndFrom());
        }
        if (f.getContractEndTo() != null) {
            w.append(" AND p.contractEndDate <= :cEndTo");
            params.put("cEndTo", f.getContractEndTo());
        }
        if (f.getExpired() != null) {
            params.put("today", today);
            w.append(f.getExpired()
                    ? " AND (p.contractEndDate IS NOT NULL AND p.contractEndDate < :today)"
                    : " AND (p.contractEndDate IS NULL OR p.contractEndDate >= :today)");
        }
        if (f.getExpiringSoon() != null) {
            params.put("today", today);
            params.put("expirySoon", expirySoonDate);
            String cond = "(p.contractEndDate IS NOT NULL AND p.contractEndDate >= :today AND p.contractEndDate <= :expirySoon)";
            w.append(f.getExpiringSoon() ? " AND " + cond : " AND NOT " + cond);
        }
        if (f.getHasActiveContract() != null) {
            params.put("today", today);
            String cond = "(p.active = true AND (p.contractStartDate IS NULL OR p.contractStartDate <= :today)"
                    + " AND (p.contractEndDate IS NULL OR p.contractEndDate >= :today))";
            w.append(f.getHasActiveContract() ? " AND " + cond : " AND NOT " + cond);
        }
        if (f.getHasActivePriceList() != null) {
            params.put("activeStatus", PriceListVersion.Status.ACTIVE);
            String ex = "EXISTS (SELECT 1 FROM PriceListVersion v WHERE v.providerId = p.id AND v.status = :activeStatus)";
            w.append(f.getHasActivePriceList() ? " AND " + ex : " AND NOT " + ex);
        }
        if (providerIds != null) {
            // scope restriction; empty list → sentinel so the report returns no rows
            w.append(" AND p.id IN :providerIds");
            params.put("providerIds", providerIds.isEmpty() ? List.of(-1L) : providerIds);
        }
        return w.toString();
    }

    private String orderBy(Sort sort) {
        if (sort != null && sort.isSorted()) {
            Sort.Order order = sort.iterator().next();
            String path = SORTABLE.get(order.getProperty());
            if (path != null) {
                return " ORDER BY " + path + (order.isAscending() ? " ASC" : " DESC");
            }
        }
        return " ORDER BY p.name ASC";
    }

    private static void applyParams(Query query, Map<String, Object> params) {
        for (Map.Entry<String, Object> e : params.entrySet()) {
            query.setParameter(e.getKey(), e.getValue());
        }
    }

    private static String like(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    // Reserved for future sortable-field validation callers.
    public static boolean isSortable(String field) {
        return SORTABLE.containsKey(field);
    }

    public static List<String> sortableFields() {
        return new ArrayList<>(SORTABLE.keySet());
    }
}
