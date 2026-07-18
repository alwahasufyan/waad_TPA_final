import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

/**
 * useReportEngine — the shared, read-only reporting engine.
 *
 * Centralises the behaviour every WAAD report needs so a new report only has to
 * supply its filters, columns and two fetchers:
 *
 *  - fetchPage(appliedFilters, { page, pageSize, sort })  → { rows, total, summary }
 *  - fetchAll(appliedFilters, { sort })                    → rows[]   (FULL filtered
 *      result — used by Excel export so the file matches the filter, not the page)
 *
 * Filtering / pagination / sorting are ALL server-side. Nothing here mutates
 * data; there are no create/edit/delete operations by design.
 *
 * @param {Object}   cfg
 * @param {Function} cfg.fetchPage   required server page fetcher
 * @param {Function} [cfg.fetchAll]  optional full-result fetcher for export
 * @param {Object}   [cfg.initialFilters]
 * @param {{key:string,dir:'asc'|'desc'}|null} [cfg.initialSort]
 * @param {number}   [cfg.pageSize=25]
 * @param {boolean}  [cfg.autoLoad=true]  fetch immediately on mount
 */
export default function useReportEngine({
  fetchPage,
  fetchAll,
  initialFilters = {},
  initialSort = null,
  pageSize: initialPageSize = 25,
  autoLoad = true
}) {
  // draft = what the user is editing; applied = what the server is querying.
  const [draftFilters, setDraftFilters] = useState(initialFilters);
  const [appliedFilters, setAppliedFilters] = useState(initialFilters);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(initialPageSize);
  const [sort, setSort] = useState(initialSort);

  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [summary, setSummary] = useState(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [lastRefreshed, setLastRefreshed] = useState(null);

  const hasLoadedRef = useRef(false);
  const reqIdRef = useRef(0); // guards against out-of-order responses

  const load = useCallback(
    async (filters, pageArg, pageSizeArg, sortArg) => {
      const reqId = ++reqIdRef.current;
      setLoading(true);
      setError(null);
      try {
        const result = (await fetchPage(filters, { page: pageArg, pageSize: pageSizeArg, sort: sortArg })) || {};
        if (reqId !== reqIdRef.current) return; // a newer request superseded this one
        setRows(Array.isArray(result.rows) ? result.rows : []);
        setTotal(Number.isFinite(result.total) ? result.total : (result.rows?.length ?? 0));
        setSummary(result.summary ?? null);
        setLastRefreshed(new Date());
        hasLoadedRef.current = true;
      } catch (err) {
        if (reqId !== reqIdRef.current) return;
        setError(err?.response?.data?.message || err?.message || 'تعذّر تحميل بيانات التقرير');
        setRows([]);
        setTotal(0);
        setSummary(null);
      } finally {
        if (reqId === reqIdRef.current) setLoading(false);
      }
    },
    [fetchPage]
  );

  // Initial load
  useEffect(() => {
    if (autoLoad && !hasLoadedRef.current) load(appliedFilters, 0, pageSize, sort);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoLoad]);

  const applyFilters = useCallback(() => {
    setAppliedFilters(draftFilters);
    setPage(0);
    load(draftFilters, 0, pageSize, sort);
  }, [draftFilters, pageSize, sort, load]);

  const clearFilters = useCallback(() => {
    setDraftFilters(initialFilters);
    setAppliedFilters(initialFilters);
    setPage(0);
    load(initialFilters, 0, pageSize, sort);
  }, [initialFilters, pageSize, sort, load]);

  const refresh = useCallback(() => {
    load(appliedFilters, page, pageSize, sort);
  }, [appliedFilters, page, pageSize, sort, load]);

  const changePage = useCallback(
    (nextPage) => {
      setPage(nextPage);
      load(appliedFilters, nextPage, pageSize, sort);
    },
    [appliedFilters, pageSize, sort, load]
  );

  const changePageSize = useCallback(
    (nextSize) => {
      setPageSize(nextSize);
      setPage(0);
      load(appliedFilters, 0, nextSize, sort);
    },
    [appliedFilters, sort, load]
  );

  const changeSort = useCallback(
    (key) => {
      const nextSort = sort?.key === key ? { key, dir: sort.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' };
      setSort(nextSort);
      setPage(0);
      load(appliedFilters, 0, pageSize, nextSort);
    },
    [appliedFilters, pageSize, sort, load]
  );

  const setDraftFilter = useCallback((key, value) => {
    setDraftFilters((prev) => ({ ...prev, [key]: value }));
  }, []);

  /** Fetch the ENTIRE filtered result set (for export). Falls back to current rows. */
  const fetchAllForExport = useCallback(async () => {
    if (typeof fetchAll === 'function') {
      return (await fetchAll(appliedFilters, { sort })) || [];
    }
    return rows;
  }, [fetchAll, appliedFilters, sort, rows]);

  const isEmpty = useMemo(() => !loading && !error && rows.length === 0 && hasLoadedRef.current, [loading, error, rows.length]);

  return {
    // filter state
    draftFilters,
    appliedFilters,
    setDraftFilter,
    setDraftFilters,
    applyFilters,
    clearFilters,
    // data
    rows,
    total,
    summary,
    // paging / sorting
    page,
    pageSize,
    sort,
    changePage,
    changePageSize,
    changeSort,
    // status
    loading,
    error,
    isEmpty,
    lastRefreshed,
    hasLoaded: hasLoadedRef.current,
    // actions
    refresh,
    fetchAllForExport
  };
}
