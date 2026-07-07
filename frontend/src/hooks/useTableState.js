/**
 * useTableState - Custom Hook for Generic Table State Management
 *
 * Manages all table state including:
 * - Pagination (page, pageSize)
 * - Sorting (orderBy, orderDirection)
 * - Filtering (column filters)
 * - Row selection (optional)
 *
 * Can be used with any table component for consistent state management.
 *
 * @example
 * const tableState = useTableState({
 *   initialPageSize: 10,
 *   defaultSort: { field: 'createdAt', direction: 'desc' }
 * });
 */

import { useState, useCallback, useMemo } from 'react';

/**
 * @typedef {Object} TableStateConfig
 * @property {number} [initialPageSize=10] - Default page size
 * @property {Object} [defaultSort] - Default sorting configuration
 * @property {string} defaultSort.field - Field to sort by
 * @property {'asc'|'desc'} defaultSort.direction - Sort direction
 * @property {Object} [initialFilters={}] - Initial column filters
 */

/**
 * @param {TableStateConfig} config
 * @returns {Object} Table state and control functions
 */
export const useTableState = (config = {}) => {
  const { initialPageSize = 10, defaultSort = null, initialFilters = {} } = config;

  // ========================================
  // PAGINATION STATE
  // ========================================

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(initialPageSize);

  const handlePageChange = useCallback((newPage) => {
    setPage(newPage);
  }, []);

  const handlePageSizeChange = useCallback((newPageSize) => {
    setPageSize(newPageSize);
    setPage(0); // Reset to first page when changing page size
  }, []);

  // ========================================
  // SORTING STATE
  // ========================================

  const [sorting, setSorting] = useState(() => {
    if (defaultSort) {
      return [{ id: defaultSort.field, desc: defaultSort.direction === 'desc' }];
    }
    return [];
  });

  const handleSortingChange = useCallback((updater) => {
    setSorting(updater);
  }, []);

  // ========================================
  // FILTERING STATE
  // ========================================

  const [columnFilters, setColumnFilters] = useState(initialFilters);

  const handleFilterChange = useCallback((columnId, value) => {
    setColumnFilters((prev) => {
      if (value === '' || value === null || value === undefined) {
        // Remove filter if value is empty
        const newFilters = { ...prev };
        delete newFilters[columnId];
        return newFilters;
      }
      return {
        ...prev,
        [columnId]: value
      };
    });
    setPage(0); // Reset to first page when filtering
  }, []);

  const handleClearFilters = useCallback(() => {
    setColumnFilters({});
    setPage(0);
  }, []);

  // ========================================
  // ROW SELECTION STATE (Optional)
  // ========================================

  const [rowSelection, setRowSelection] = useState({});

  const handleRowSelectionChange = useCallback((updater) => {
    setRowSelection(updater);
  }, []);

  const handleClearSelection = useCallback(() => {
    setRowSelection({});
  }, []);

  // ========================================
  // RESET FUNCTION
  // ========================================

  const resetTableState = useCallback(() => {
    setPage(0);
    setPageSize(initialPageSize);
    setSorting(defaultSort ? [{ id: defaultSort.field, desc: defaultSort.direction === 'desc' }] : []);
    setColumnFilters(initialFilters);
    setRowSelection({});
  }, [initialPageSize, defaultSort, initialFilters]);

  // ========================================
  // COMPUTED VALUES
  // ========================================

  const hasActiveFilters = useMemo(() => {
    return Object.keys(columnFilters).length > 0;
  }, [columnFilters]);

  const selectedRowCount = useMemo(() => {
    return Object.keys(rowSelection).filter((key) => rowSelection[key]).length;
  }, [rowSelection]);

  // ========================================
  // RETURN STATE AND CONTROLS
  // ========================================

  return {
    // Pagination
    page,
    pageSize,
    setPage: handlePageChange,
    setPageSize: handlePageSizeChange,

    // Sorting
    sorting,
    setSorting: handleSortingChange,

    // Filtering
    columnFilters,
    setColumnFilters,
    setFilter: handleFilterChange,
    clearFilters: handleClearFilters,
    hasActiveFilters,

    // Row Selection
    rowSelection,
    setRowSelection: handleRowSelectionChange,
    clearSelection: handleClearSelection,
    selectedRowCount,

    // Reset
    resetState: resetTableState
  };
};

export default useTableState;
