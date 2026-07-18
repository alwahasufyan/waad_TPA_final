import PropTypes from 'prop-types';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TableSortLabel from '@mui/material/TableSortLabel';
import TablePagination from '@mui/material/TablePagination';
import Paper from '@mui/material/Paper';
import Box from '@mui/material/Box';
import Skeleton from '@mui/material/Skeleton';

/**
 * ReportTable — a strictly READ-ONLY, server-paginated report grid.
 * Sticky header, horizontal scroll, optional server-side sorting. It never
 * renders row actions (no edit/delete/approve) — reports are read-only.
 *
 * columns: [{ key, header, align?, width?, minWidth?, sortable?, render?(row) }]
 */
export default function ReportTable({
  columns,
  rows,
  loading = false,
  page = 0,
  pageSize = 25,
  total = 0,
  onPageChange,
  onPageSizeChange,
  sort = null,
  onSortChange,
  getRowKey,
  emptyState = null,
  maxHeight = '60vh',
  pageSizeOptions = [10, 25, 50, 100]
}) {
  const showEmpty = !loading && rows.length === 0;

  return (
    <Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden' }}>
      <TableContainer sx={{ maxHeight }}>
        <Table stickyHeader size="small" sx={{ minWidth: 720 }}>
          <TableHead>
            <TableRow>
              {columns.map((col) => (
                <TableCell
                  key={col.key}
                  align={col.align || 'right'}
                  sx={{
                    fontWeight: 800,
                    whiteSpace: 'nowrap',
                    bgcolor: 'grey.100',
                    width: col.width,
                    minWidth: col.minWidth || 120
                  }}
                  sortDirection={sort?.key === col.key ? sort.dir : false}
                >
                  {col.sortable && onSortChange ? (
                    <TableSortLabel
                      active={sort?.key === col.key}
                      direction={sort?.key === col.key ? sort.dir : 'asc'}
                      onClick={() => onSortChange(col.key)}
                    >
                      {col.header}
                    </TableSortLabel>
                  ) : (
                    col.header
                  )}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {loading &&
              Array.from({ length: 6 }).map((_, r) => (
                <TableRow key={`sk-${r}`}>
                  {columns.map((col) => (
                    <TableCell key={col.key} align={col.align || 'right'}>
                      <Skeleton variant="text" />
                    </TableCell>
                  ))}
                </TableRow>
              ))}

            {!loading &&
              rows.map((row, idx) => (
                <TableRow key={getRowKey ? getRowKey(row, idx) : (row.id ?? idx)} hover>
                  {columns.map((col) => (
                    <TableCell key={col.key} align={col.align || 'right'} sx={{ whiteSpace: col.wrap ? 'normal' : 'nowrap' }}>
                      {col.render ? col.render(row) : (row[col.key] ?? '—')}
                    </TableCell>
                  ))}
                </TableRow>
              ))}

            {showEmpty && (
              <TableRow>
                <TableCell colSpan={columns.length} sx={{ border: 0, py: 0 }}>
                  <Box sx={{ py: 4 }}>{emptyState}</Box>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <TablePagination
        component="div"
        count={total}
        page={page}
        onPageChange={(_, next) => onPageChange?.(next)}
        rowsPerPage={pageSize}
        onRowsPerPageChange={(e) => onPageSizeChange?.(parseInt(e.target.value, 10))}
        rowsPerPageOptions={pageSizeOptions}
        labelRowsPerPage="عدد الصفوف:"
        labelDisplayedRows={({ from, to, count }) => `${from}–${to} من ${count}`}
        sx={{ borderTop: 1, borderColor: 'divider' }}
      />
    </Paper>
  );
}

ReportTable.propTypes = {
  columns: PropTypes.arrayOf(
    PropTypes.shape({
      key: PropTypes.string.isRequired,
      header: PropTypes.node.isRequired,
      align: PropTypes.oneOf(['left', 'right', 'center']),
      width: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
      minWidth: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
      sortable: PropTypes.bool,
      wrap: PropTypes.bool,
      render: PropTypes.func
    })
  ).isRequired,
  rows: PropTypes.array.isRequired,
  loading: PropTypes.bool,
  page: PropTypes.number,
  pageSize: PropTypes.number,
  total: PropTypes.number,
  onPageChange: PropTypes.func,
  onPageSizeChange: PropTypes.func,
  sort: PropTypes.shape({ key: PropTypes.string, dir: PropTypes.oneOf(['asc', 'desc']) }),
  onSortChange: PropTypes.func,
  getRowKey: PropTypes.func,
  emptyState: PropTypes.node,
  maxHeight: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  pageSizeOptions: PropTypes.array
};
