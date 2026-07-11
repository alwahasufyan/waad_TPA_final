/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * UNIFIED MEDICAL TABLE - TBA WAAD MEDICAL TPA SYSTEM
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * THE SINGLE SOURCE OF TRUTH FOR ALL TABLES IN THE SYSTEM
 * Based on Provider Visits Log design pattern
 *
 * ARCHITECTURE PRINCIPLES:
 * ✅ ONE table design for the ENTIRE system
 * ✅ Full-width (100% viewport width)
 * ✅ Soft medical green header (#E8F5F1)
 * ✅ Fixed/sticky header
 * ✅ Filters ABOVE table (never inside)
 * ✅ Desktop-first professional medical UI
 * ✅ RTL support for Arabic
 *
 * FORBIDDEN:
 * ❌ MUI DataGrid
 * ❌ Filters in table headers
 * ❌ Different table styles per module
 * ❌ Cards wrapping tables
 * ❌ Nested scrollbars
 *
 * @author TBA WAAD Development Team
 * @version 2.0.0 - Unified Medical Standard
 * @since 2026-02-08
 * ═══════════════════════════════════════════════════════════════════════════════
 */

import PropTypes from 'prop-types';
import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Typography,
  CircularProgress,
  Stack,
  TableSortLabel,
  alpha,
  useTheme,
  Collapse,
  IconButton,
  Tooltip,
  Menu,
  MenuItem,
  Checkbox,
  ListItemText,
  Button
} from '@mui/material';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import ViewColumnIcon from '@mui/icons-material/ViewColumn';
import DensitySmallIcon from '@mui/icons-material/DensitySmall';
import DensityMediumIcon from '@mui/icons-material/DensityMedium';
import RefreshIcon from '@mui/icons-material/Refresh';
import { useState, useMemo, Fragment } from 'react';

// ═══════════════════════════════════════════════════════════════════════════════
// MEDICAL COLOR THEME (SYSTEM-WIDE STANDARD)
// ═══════════════════════════════════════════════════════════════════════════════

const MEDICAL_TABLE_THEME = {
  // Header - Soft Medical Green (Light Mode)
  header: {
    light: {
      background: '#E8F5F1', // Soft mint green
      text: '#0D4731', // Dark green
      border: '#C8E6C9'
    },
    dark: {
      background: '#1E3A5F', // Professional dark blue
      text: '#FFFFFF'
    }
  },
  // Row hover - Very light medical tint
  row: {
    light: {
      odd: 'rgba(13, 71, 161, 0.04)',
      hover: 'rgba(13, 71, 161, 0.08)'
    },
    dark: {
      odd: 'rgba(13, 71, 161, 0.08)',
      hover: 'rgba(13, 71, 161, 0.15)'
    }
  }
};

// ═══════════════════════════════════════════════════════════════════════════════
// UNIFIED MEDICAL TABLE COMPONENT
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * UnifiedMedicalTable - The ONLY table component used in TBA WAAD system
 *
 * @example
 * <UnifiedMedicalTable
 *   columns={[
 *     { id: 'id', label: 'الرقم', minWidth: '5.0rem', icon: <BadgeIcon /> },
 *     { id: 'name', label: 'الاسم', minWidth: '10.0rem', icon: <PersonIcon /> }
 *   ]}
 *   rows={data}
 *   loading={false}
 *   totalCount={100}
 *   page={0}
 *   rowsPerPage={10}
 *   onPageChange={(newPage) => setPage(newPage)}
 *   onRowsPerPageChange={(newSize) => setPageSize(newSize)}
 *   renderCell={(row, column) => row[column.id]}
 * />
 */
const UnifiedMedicalTable = ({
  // Column Definition
  columns = [],

  // Data - accept both 'rows' and 'data' as aliases
  rows: rowsProp = [],
  data: dataProp,           // alias for rows
  loading = false,
  isLoading,                // alias for loading
  totalCount: totalCountProp = 0,
  totalItems: totalItemsProp, // alias for totalCount

  // Pagination
  page = 0,
  rowsPerPage = 10,
  onPageChange,
  onRowsPerPageChange,
  rowsPerPageOptions = [5, 10, 25, 50],

  // Sorting (Optional)
  sortBy,
  sortDirection,
  onSort,

  // Row Rendering
  renderCell,
  getRowKey = (row, index) => row.id || index,
  getRowSx,

  // Expandable Row
  renderExpandedRow,
  isRowExpandable,

  // Empty State - accept both individual props and emptyStateConfig object
  emptyMessage = 'لا توجد بيانات',
  emptyIcon: EmptyIcon = LocalHospitalIcon,
  emptyStateConfig,  // { icon, title, description } - alias for emptyMessage/emptyIcon

  // Loading State
  loadingMessage = 'جارِر التحميل...',

  // Error State (consumed here, not passed to DOM)
  error,
  onErrorClose,

  // Table Props
  stickyHeader = true,
  size = 'medium',
  hover = true,

  // Enterprise table preferences (Stage 2.1) — OPT-IN via persistKey.
  // When provided, a small toolbar exposes a density toggle and a column
  // show/hide menu, both remembered in localStorage per key. When absent, the
  // component behaves exactly as before (no toolbar, no persistence).
  persistKey,

  // Styling
  sx = {},
  tableContainerSx = {},

  // Extended props accepted but handled internally (not forwarded to DOM)
  // eslint-disable-next-line no-unused-vars
  dataFetchFn,
  // eslint-disable-next-line no-unused-vars
  queryKey,
  // eslint-disable-next-line no-unused-vars
  enableExport,
  // eslint-disable-next-line no-unused-vars
  onExportExcel,
  // eslint-disable-next-line no-unused-vars
  defaultSort,
  // eslint-disable-next-line no-unused-vars
  enableAdvancedFilters,
  // eslint-disable-next-line no-unused-vars
  filtersConfig,
  // eslint-disable-next-line no-unused-vars
  onFilterChange,
  // eslint-disable-next-line no-unused-vars
  enableSearch,
  // eslint-disable-next-line no-unused-vars
  searchPlaceholder,
  // eslint-disable-next-line no-unused-vars
  onSearchChange,
  // eslint-disable-next-line no-unused-vars
  onRefresh,
  // eslint-disable-next-line no-unused-vars
  enablePagination,

  ...otherProps
}) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  // Resolve aliased props
  const rows = dataProp !== undefined ? dataProp : rowsProp;
  const totalCount = totalItemsProp !== undefined ? totalItemsProp : totalCountProp;
  const resolvedLoading = isLoading !== undefined ? isLoading : loading;
  const resolvedEmptyMessage = emptyStateConfig?.title || emptyStateConfig?.description || emptyMessage;
  const ResolvedEmptyIcon = emptyStateConfig?.icon || EmptyIcon;

  // Normalize columns: support DataGrid-style 'field'/'headerName' as aliases for 'id'/'label'
  const normalizedColumns = columns.map((col) => ({
    ...col,
    id: col.id || col.field,
    label: col.label || col.headerName
  }));

  // ── Enterprise table preferences (Stage 2.1): density + column visibility ──
  // Fully backward compatible: without `persistKey` there is no toolbar and
  // behavior is identical (density = size, all columns visible).
  const prefsEnabled = !!persistKey;
  const densityStorageKey = persistKey ? `waad:tbl:${persistKey}:density` : null;
  const colsStorageKey = persistKey ? `waad:tbl:${persistKey}:hiddenCols` : null;
  const orderStorageKey = persistKey ? `waad:tbl:${persistKey}:colOrder` : null;

  const [density, setDensity] = useState(() => {
    if (!densityStorageKey) return size;
    try { return localStorage.getItem(densityStorageKey) || size; } catch { return size; }
  });
  const [hiddenColumnIds, setHiddenColumnIds] = useState(() => {
    if (!colsStorageKey) return [];
    try { return JSON.parse(localStorage.getItem(colsStorageKey) || '[]'); } catch { return []; }
  });
  const [columnOrder, setColumnOrder] = useState(() => {
    if (!orderStorageKey) return [];
    try { return JSON.parse(localStorage.getItem(orderStorageKey) || '[]'); } catch { return []; }
  });
  const [colMenuAnchor, setColMenuAnchor] = useState(null);

  const resolvedDensity = prefsEnabled ? density : size;

  // Apply a saved column order (Stage 2.1-B): reorder normalizedColumns by the
  // persisted id list, appending any new/unknown columns at the end so the table
  // never drops a column that was added after the order was saved.
  const orderedColumns = useMemo(() => {
    if (!columnOrder.length) return normalizedColumns;
    const byId = new Map(normalizedColumns.map((c) => [c.id, c]));
    const ordered = columnOrder.map((id) => byId.get(id)).filter(Boolean);
    normalizedColumns.forEach((c) => { if (!columnOrder.includes(c.id)) ordered.push(c); });
    return ordered;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [columns, columnOrder]);

  const visibleColumns = useMemo(
    () => (hiddenColumnIds.length
      ? orderedColumns.filter((c) => !hiddenColumnIds.includes(c.id))
      : orderedColumns),
    [orderedColumns, hiddenColumnIds]
  );

  const persistPref = (key, value) => {
    try { if (key) localStorage.setItem(key, value); } catch { /* storage unavailable — ignore */ }
  };
  const toggleDensity = () => {
    const next = density === 'small' ? 'medium' : 'small';
    setDensity(next);
    persistPref(densityStorageKey, next);
  };
  const toggleColumnVisibility = (id) => {
    setHiddenColumnIds((prev) => {
      // Never allow hiding the last remaining visible column.
      const next = prev.includes(id)
        ? prev.filter((x) => x !== id)
        : (prev.length + 1 < normalizedColumns.length ? [...prev, id] : prev);
      persistPref(colsStorageKey, JSON.stringify(next));
      return next;
    });
  };
  // Move a column up/down in the display order (Stage 2.1-B), persisted.
  const moveColumn = (id, direction) => {
    const base = orderedColumns.map((c) => c.id);
    const from = base.indexOf(id);
    const to = from + direction;
    if (from < 0 || to < 0 || to >= base.length) return;
    const next = [...base];
    [next[from], next[to]] = [next[to], next[from]];
    setColumnOrder(next);
    persistPref(orderStorageKey, JSON.stringify(next));
  };

  // Theme colors — use CSS variables injected by AppearanceInjector for light mode
  const headerBg   = isDark ? MEDICAL_TABLE_THEME.header.dark.background : 'var(--tba-th-bg, #E0F2F1)';
  const headerText = isDark ? MEDICAL_TABLE_THEME.header.dark.text        : 'var(--tba-th-text, #004D50)';
  const headerBorder = isDark ? theme.palette.divider                     : 'var(--tba-th-text, #00838F)';
  const rowEven = isDark ? MEDICAL_TABLE_THEME.row.dark.odd  : 'var(--tba-row-even, rgba(224,242,241,0.45))';
  const rowHover = isDark ? MEDICAL_TABLE_THEME.row.dark.hover : 'var(--tba-selection, rgba(0,131,143,0.08))';

  // Pagination handlers
  const handlePageChange = (_, newPage) => {
    onPageChange?.(newPage);
  };

  const handleRowsPerPageChange = (event) => {
    const newSize = parseInt(event.target.value, 10);
    onRowsPerPageChange?.(newSize);
  };

  // Calculate columns span for loading/empty states
  const colSpan = visibleColumns.length + (renderExpandedRow ? 1 : 0);

  // Expansion state
  const [expandedRows, setExpandedRows] = useState({});

  const toggleRowExpansion = (rowId) => {
    setExpandedRows((prev) => ({
      ...prev,
      [rowId]: !prev[rowId]
    }));
  };

  // Handle sort
  const handleSortRequest = (columnId) => {
    if (onSort && visibleColumns.find((col) => col.id === columnId)?.sortable !== false) {
      const isAsc = sortBy === columnId && sortDirection === 'asc';
      onSort(columnId, isAsc ? 'desc' : 'asc');
    }
  };

  return (
    <Box
      sx={{
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        ...sx
      }}
      {...otherProps}
    >
      {/* Enterprise preferences toolbar (opt-in via persistKey) — refresh + density + columns/reorder */}
      {prefsEnabled && (
        <Stack direction="row" justifyContent="flex-end" alignItems="center" spacing={0.25} sx={{ mb: 0.5 }}>
          {onRefresh && (
            <Tooltip title="تحديث">
              <IconButton size="small" onClick={onRefresh} aria-label="تحديث الجدول">
                <RefreshIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
          <Tooltip title={density === 'small' ? 'عرض مريح' : 'عرض مضغوط'}>
            <IconButton size="small" onClick={toggleDensity} aria-label="كثافة الصفوف">
              {density === 'small' ? <DensityMediumIcon fontSize="small" /> : <DensitySmallIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
          <Tooltip title="إظهار/إخفاء وترتيب الأعمدة">
            <IconButton size="small" onClick={(e) => setColMenuAnchor(e.currentTarget)} aria-label="أعمدة الجدول">
              <ViewColumnIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Menu anchorEl={colMenuAnchor} open={!!colMenuAnchor} onClose={() => setColMenuAnchor(null)}>
            {orderedColumns.map((col, idx) => (
              <MenuItem key={col.id} dense disableRipple sx={{ pr: 0.5 }}>
                <Checkbox
                  size="small"
                  checked={!hiddenColumnIds.includes(col.id)}
                  onChange={() => toggleColumnVisibility(col.id)}
                />
                <ListItemText primary={col.label || col.id} sx={{ mr: 2 }} />
                <Tooltip title="تحريك لأعلى">
                  <span>
                    <IconButton size="small" disabled={idx === 0} onClick={() => moveColumn(col.id, -1)} aria-label="تحريك العمود لأعلى">
                      <KeyboardArrowUpIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
                <Tooltip title="تحريك لأسفل">
                  <span>
                    <IconButton size="small" disabled={idx === orderedColumns.length - 1} onClick={() => moveColumn(col.id, 1)} aria-label="تحريك العمود لأسفل">
                      <KeyboardArrowDownIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
              </MenuItem>
            ))}
          </Menu>
        </Stack>
      )}

      {/* Table Container - NO card wrapper, NO shadows */}
      <TableContainer
        component={Paper}
        variant="outlined"
        sx={{
          borderRadius: '0.25rem',
          overflow: 'auto',
          border: `1px solid ${theme.palette.divider}`,
          boxShadow: 'none', // NO shadows
          width: '100%',
          flexGrow: 1,
          ...tableContainerSx
        }}
      >
        <Table size={resolvedDensity} stickyHeader={stickyHeader}>
          {/* Header - Soft Medical Green */}
          <TableHead>
            <TableRow>
              {renderExpandedRow && (
                <TableCell
                  padding="checkbox"
                  sx={{
                    bgcolor: headerBg,
                    borderBottom: `2px solid ${headerBorder}`
                  }}
                />
              )}
              {visibleColumns.map((column) => {
                const isSortable = column.sortable !== false && onSort;
                const isActive = sortBy === column.id;

                return (
                  <TableCell
                    key={column.id}
                    align={column.align || 'left'}
                    sx={{
                      bgcolor: headerBg,
                      color: headerText,
                      fontWeight: 600,
                      py: '0.75rem',
                      minWidth: column.minWidth || 80,
                      width: column.width,
                      whiteSpace: 'nowrap',
                      borderBottom: `2px solid ${headerBorder}`
                    }}
                  >
                    {isSortable ? (
                      <TableSortLabel
                        active={isActive}
                        direction={isActive ? sortDirection : 'asc'}
                        onClick={() => handleSortRequest(column.id)}
                        IconComponent={() => null} // Hides the default sort arrow entirely per user request
                        sx={{
                          color: headerText,
                          '&:hover': {
                            color: headerText,
                            opacity: 0.8
                          },
                          '&.Mui-active': {
                            color: headerText
                          }
                        }}
                      >
                        {column.icon ? (
                          <Stack direction="row" spacing={0.5} alignItems="center">
                            {column.icon}
                            <span>{column.label}</span>
                          </Stack>
                        ) : (
                          column.label
                        )}
                      </TableSortLabel>
                    ) : column.icon ? (
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        {column.icon}
                        <span>{column.label}</span>
                      </Stack>
                    ) : (
                      column.label
                    )}
                  </TableCell>
                );
              })}
            </TableRow>
          </TableHead>

          {/* Body */}
          <TableBody>
            {/* Loading State */}
            {resolvedLoading ? (
              <TableRow>
                <TableCell colSpan={colSpan} align="center" sx={{ py: '2.0rem' }}>
                  <CircularProgress size={40} />
                  <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
                    {loadingMessage}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : !rows || rows.length === 0 ? (
              /* Empty State */
              <TableRow>
                <TableCell colSpan={colSpan} align="center" sx={{ py: '2.0rem' }}>
                  <ResolvedEmptyIcon sx={{ fontSize: '3.0rem', color: 'action.disabled', mb: 1 }} />
                  <Typography variant="body1" color="textSecondary">
                    {resolvedEmptyMessage}
                  </Typography>
                  {emptyStateConfig?.description && emptyStateConfig?.title && (
                    <Typography variant="body2" color="textSecondary" sx={{ mt: 0.5 }}>
                      {emptyStateConfig.description}
                    </Typography>
                  )}
                  {/* Optional suggested action (Stage 2.1-B) — { label, onClick } */}
                  {emptyStateConfig?.action?.label && (
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={emptyStateConfig.action.onClick}
                      sx={{ mt: 1.5 }}
                    >
                      {emptyStateConfig.action.label}
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ) : (
              /* Data Rows */
              rows.map((row, rowIndex) => {
                const rowKey = getRowKey(row, rowIndex);
                const isExpanded = !!expandedRows[rowKey];
                const expandable = isRowExpandable ? isRowExpandable(row) : !!renderExpandedRow;

                return (
                  <Fragment key={rowKey}>
                    <TableRow
                      hover={hover}
                      sx={{
                        '&:nth-of-type(even)': { bgcolor: rowEven },
                        '&:hover': {
                          bgcolor: `${rowHover} !important`
                        },
                        transition: 'background-color 0.2s',
                        borderBottom: renderExpandedRow ? 'none' : `1px solid ${alpha(theme.palette.divider, 0.8)}`,
                        ...(getRowSx ? getRowSx(row, rowIndex) : {})
                      }}
                    >
                      {renderExpandedRow && (
                        <TableCell padding="checkbox" sx={{ py: '0.75rem', borderBottom: `1px solid ${alpha(theme.palette.divider, 0.8)}` }}>
                          {expandable && (
                            <IconButton size="small" onClick={() => toggleRowExpansion(rowKey)}>
                              {isExpanded ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
                            </IconButton>
                          )}
                        </TableCell>
                      )}
                      {visibleColumns.map((column) => (
                        <TableCell
                          key={column.id}
                          align={column.align || 'left'}
                          sx={{
                            py: '0.75rem',
                            borderBottom: `1px solid ${alpha(theme.palette.divider, 0.8)}`
                          }}
                        >
                          {column.renderCell
                            ? column.renderCell({ row, value: row[column.id], id: column.id })
                            : renderCell
                              ? renderCell(row, column, rowIndex)
                              : row[column.id]}
                        </TableCell>
                      ))}
                    </TableRow>
                    {renderExpandedRow && expandable && (
                      <TableRow sx={{ '& td': { padding: 0, borderBottom: isExpanded ? `1px solid ${alpha(theme.palette.divider, 0.8)}` : 'none' } }}>
                        <TableCell colSpan={colSpan}>
                          <Collapse in={isExpanded} timeout="auto" unmountOnExit>
                            <Box sx={{ p: '1.0rem', bgcolor: alpha(theme.palette.primary.main, 0.02) }}>
                              {renderExpandedRow(row, rowIndex)}
                            </Box>
                          </Collapse>
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                );
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Pagination - Always visible */}
      {onPageChange && onRowsPerPageChange && (
        <TablePagination
          component="div"
          count={totalCount}
          page={page}
          onPageChange={handlePageChange}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={handleRowsPerPageChange}
          rowsPerPageOptions={rowsPerPageOptions}
          labelRowsPerPage="صفوف لكل صفحة"
          labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
          sx={{
            borderTop: `1px solid ${theme.palette.divider}`,
            bgcolor: theme.palette.background.paper,
            overflow: 'hidden'
          }}
        />
      )}
    </Box>
  );
};

UnifiedMedicalTable.propTypes = {
  // Columns
  columns: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string.isRequired,
      label: PropTypes.string.isRequired,
      minWidth: PropTypes.number,
      width: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      align: PropTypes.oneOf(['left', 'center', 'right']),
      icon: PropTypes.node,
      sortable: PropTypes.bool
    })
  ).isRequired,

  // Data
  rows: PropTypes.array.isRequired,
  loading: PropTypes.bool,
  totalCount: PropTypes.number,

  // Pagination
  page: PropTypes.number,
  rowsPerPage: PropTypes.number,
  onPageChange: PropTypes.func,
  onRowsPerPageChange: PropTypes.func,
  rowsPerPageOptions: PropTypes.arrayOf(PropTypes.number),

  // Sorting
  sortBy: PropTypes.string,
  sortDirection: PropTypes.oneOf(['asc', 'desc']),
  onSort: PropTypes.func,

  // Enterprise preferences (opt-in): localStorage key enabling density + column controls
  persistKey: PropTypes.string,

  // Row Rendering
  renderCell: PropTypes.func,
  getRowKey: PropTypes.func,
  getRowSx: PropTypes.func,

  // Expandable Row
  renderExpandedRow: PropTypes.func,
  isRowExpandable: PropTypes.func,

  // Empty State
  emptyMessage: PropTypes.string,
  emptyIcon: PropTypes.elementType,

  // Loading State
  loadingMessage: PropTypes.string,

  // Table Props
  stickyHeader: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium']),
  hover: PropTypes.bool,

  // Styling
  sx: PropTypes.object
};

export default UnifiedMedicalTable;


