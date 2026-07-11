import PropTypes from 'prop-types';
import { useState, useMemo } from 'react';
import { Typography, Box, Stack, Chip, Table, TableHead, TableBody, TableRow, TableCell, Paper } from '@mui/material';
import DescriptionIcon from '@mui/icons-material/Description';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import PersonIcon from '@mui/icons-material/Person';
import BusinessIcon from '@mui/icons-material/Business';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import EventIcon from '@mui/icons-material/Event';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import UpdateIcon from '@mui/icons-material/Update';

import ClaimStatusChip from './ClaimStatusChip';
import { UnifiedMedicalTable } from 'components/common';
// W1.2 (#6): single canonical LYD formatter for the whole page.
import { formatCurrency } from 'utils/formatters';

/** Grouped 2-decimal number without the currency symbol — for dense financial cells. */
const money = (amount) => (amount == null ? '0.00' : formatCurrency(amount, false));

/**
 * Format date for display
 */
const formatDate = (dateString) => {
  if (!dateString) return '—';
  try {
    const d = new Date(dateString);
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  } catch {
    return dateString;
  }
};

/**
 * Safe string renderer with fallback
 */
const safeString = (value) => {
  if (value == null || value === '') return '—';
  return String(value);
};

/**
 * Table columns configuration
 * All columns are null-safe with fallback rendering
 */
const COLUMNS = [
  {
    id: 'id',
    label: 'رقم المطالبة',
    minWidth: '7.5rem',
    align: 'center',
    format: safeString,
    sortable: true,
    icon: <ReceiptLongIcon fontSize="small" />
  },
  {
    id: 'memberName',
    label: 'اسم المؤمن عليه',
    minWidth: '11.25rem',
    format: safeString,
    sortable: true,
    icon: <PersonIcon fontSize="small" />
  },
  {
    id: 'employerName',
    label: 'الشريك',
    minWidth: '10.625rem',
    format: safeString,
    sortable: true,
    icon: <BusinessIcon fontSize="small" />
  },
  {
    id: 'providerName',
    label: 'مقدم الخدمة',
    minWidth: '10.625rem',
    format: safeString,
    sortable: true,
    icon: <LocalHospitalIcon fontSize="small" />
  },
  { id: 'status', label: 'الحالة', minWidth: '8.125rem', align: 'center', sortable: true },
  {
    id: 'requestedAmount',
    label: 'المبلغ المطلوب',
    minWidth: '8.75rem',
    align: 'right',
    format: formatCurrency,
    sortable: true,
    icon: <AttachMoneyIcon fontSize="small" />
  },
  {
    id: 'approvedAmount',
    label: 'المبلغ المعتمد',
    minWidth: '8.75rem',
    align: 'right',
    format: formatCurrency,
    sortable: true,
    icon: <AttachMoneyIcon fontSize="small" />
  },
  {
    id: 'visitDate',
    label: 'تاريخ الزيارة',
    minWidth: '8.125rem',
    align: 'center',
    format: formatDate,
    sortable: true,
    icon: <EventIcon fontSize="small" />
  },
  {
    id: 'updatedAt',
    label: 'آخر تحديث',
    minWidth: '8.125rem',
    align: 'center',
    format: formatDate,
    sortable: true,
    icon: <UpdateIcon fontSize="small" />
  }
];

/**
 * ClaimsTable Component
 *
 * MUI Table for displaying claims with sticky header
 *
 * @param {Array} claims - Claims data
 * @param {boolean} loading - Loading state
 * @param {number} totalCount - Total filtered count
 * @param {number} page - Current page
 * @param {number} rowsPerPage - Rows per page
 * @param {Function} onPageChange - Page change handler
 * @param {Function} onRowsPerPageChange - Rows per page change handler
 */
const ClaimsTable = ({ claims, loading, totalCount, page, rowsPerPage, onPageChange, onRowsPerPageChange }) => {
  // Sorting state
  const [orderBy, setOrderBy] = useState('id');
  const [order, setOrder] = useState('desc');

  const handleSort = (property) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

  const descendingComparator = (a, b, orderKey) => {
    const aVal = a[orderKey];
    const bVal = b[orderKey];

    if (aVal == null && bVal == null) return 0;
    if (aVal == null) return 1;
    if (bVal == null) return -1;

    if (typeof aVal === 'number' && typeof bVal === 'number') {
      return bVal - aVal;
    }

    return String(bVal).localeCompare(String(aVal), 'ar');
  };

  const getComparator = (orderKey, orderDirection) => {
    return orderDirection === 'desc' ? (a, b) => descendingComparator(a, b, orderKey) : (a, b) => -descendingComparator(a, b, orderKey);
  };

  const sortedClaims = useMemo(() => {
    return [...claims].sort(getComparator(orderBy, order));
  }, [claims, orderBy, order]);

  const renderCellValue = (claim, column) => {
    const value = claim[column.id];
    if (column.id === 'status') {
      return <ClaimStatusChip status={value} />;
    }
    // W1.2 (#2): show the official claim reference (e.g. CLM-251), not the internal id.
    if (column.id === 'id') {
      return safeString(claim._raw?.claimNumber || value);
    }
    if (column.format) {
      return column.format(value);
    }
    return value ?? '—';
  };

  // Service-items table. Columns are ordered for the operational review flow.
  // HOTFIX: field names corrected to the actual API line DTO (medicalServiceName),
  // and the per-line company/member split is derived HONESTLY from the line's own
  // coverage percentages (coveragePercent / patientSharePercent) applied to the
  // accepted amount — the line DTO carries no direct companyShare/patientShare.
  const rejectedOf = (line) => (line.rejected ? line.totalPrice : line.refusedAmount) || 0;
  const acceptedOf = (line) => Math.max(0, (line.totalPrice || 0) - (line.refusedAmount || 0));
  const coverageOf = (line) => (line.coveragePercent != null ? line.coveragePercent : 100);
  const patientPctOf = (line) => (line.patientSharePercent != null ? line.patientSharePercent : 100 - coverageOf(line));
  const companyShareOf = (line) => (line.rejected ? 0 : acceptedOf(line) * coverageOf(line) / 100);
  const memberShareOf = (line) => (line.rejected ? 0 : acceptedOf(line) * patientPctOf(line) / 100);

  const renderExpandedRow = (row) => {
    const lines = row._raw?.lines || [];
    if (!lines.length) {
      return <Typography variant="caption" color="text.secondary">لا توجد خدمات مسجلة</Typography>;
    }

    const headCell = { fontWeight: 800, py: 1, bgcolor: '#fcfcfc', fontSize: '0.78rem', whiteSpace: 'nowrap' };
    const muted = { fontSize: '0.82rem', fontWeight: 700, color: 'text.secondary' };

    return (
      <Paper variant="outlined" sx={{ width: '100%', mb: 1, borderRadius: 1.5, overflow: 'hidden' }}>
        {/* Header */}
        <Box sx={{ px: '1.25rem', py: 0.75, bgcolor: '#E8F5F1', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle2" fontWeight={900} color="#0D4731" sx={{ fontSize: '0.85rem' }}>
            بنود الخدمة
          </Typography>
          <Chip size="small" variant="outlined" label={`${lines.length} بند`} sx={{ fontWeight: 700, fontSize: '0.75rem' }} />
        </Box>

        {/* Line items — columns ordered for operational review (#4) */}
        <Table size="small" sx={{ minWidth: '46rem' }}>
          <TableHead>
            <TableRow>
              <TableCell align="right" width={260} sx={headCell}>الخدمة الطبية</TableCell>
              <TableCell align="center" width={45} sx={headCell}>الكمية</TableCell>
              <TableCell align="center" width={80} sx={headCell}>سعر الوحدة</TableCell>
              <TableCell align="center" width={90} sx={headCell}>الإجمالي</TableCell>
              <TableCell align="center" width={55} sx={headCell}>التحمل %</TableCell>
              <TableCell align="center" width={95} sx={headCell}>حصة الشركة</TableCell>
              <TableCell align="center" width={95} sx={headCell}>تحمّل المريض</TableCell>
              <TableCell align="center" width={90} sx={headCell}>المرفوض</TableCell>
              <TableCell align="center" width={85} sx={{ ...headCell, color: 'text.secondary' }}>سقف المنفعة</TableCell>
              <TableCell align="center" width={90} sx={{ ...headCell, color: 'text.secondary' }}>الرصيد المتبقي</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {lines.map((line) => (
              <TableRow key={line.id} hover sx={{ '& td': { py: 0.6, px: '0.75rem', fontSize: '0.82rem' }, ...(line.rejected && { bgcolor: 'rgba(211, 47, 47, 0.05)' }) }}>
                <TableCell align="right">
                  <Stack spacing={0.25} alignItems="flex-start">
                    <Typography variant="body2" sx={{ fontWeight: 800, fontSize: '0.82rem', color: line.rejected ? 'error.main' : 'text.primary' }}>
                      {line.medicalServiceName || safeString(line.medicalServiceCode)}
                    </Typography>
                    {line.serviceCategoryName && (
                      <Typography variant="caption" sx={{ fontSize: '0.72rem', color: 'text.secondary', fontWeight: 700 }}>
                        {line.serviceCategoryName}
                      </Typography>
                    )}
                    {line.rejectionReason && (
                      <Typography variant="caption" sx={{ fontSize: '0.72rem', color: 'error.main', fontWeight: 800 }}>
                        السبب: {line.rejectionReason}
                      </Typography>
                    )}
                  </Stack>
                </TableCell>
                <TableCell align="center"><Typography sx={{ fontWeight: 800, fontSize: '0.85rem' }}>{line.quantity}</Typography></TableCell>
                <TableCell align="center">{money(line.unitPrice)}</TableCell>
                <TableCell align="center"><Typography sx={{ fontWeight: 800, fontSize: '0.85rem' }}>{money(line.totalPrice)}</Typography></TableCell>
                <TableCell align="center"><Typography sx={muted}>{`${coverageOf(line)}%`}</Typography></TableCell>
                <TableCell align="center"><Typography sx={{ fontWeight: 900, fontSize: '0.85rem', color: 'success.main' }}>{money(companyShareOf(line))}</Typography></TableCell>
                <TableCell align="center"><Typography sx={{ fontWeight: 900, fontSize: '0.85rem', color: 'warning.dark' }}>{money(memberShareOf(line))}</Typography></TableCell>
                <TableCell align="center"><Typography sx={{ fontWeight: 800, fontSize: '0.85rem', color: 'error.main' }}>{money(rejectedOf(line))}</Typography></TableCell>
                <TableCell align="center"><Typography sx={muted}>{line.benefitLimit > 0 ? money(line.benefitLimit) : '—'}</Typography></TableCell>
                <TableCell align="center"><Typography sx={muted}>{line.remainingAmount != null ? money(line.remainingAmount) : '—'}</Typography></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    );
  };

  const paginatedClaims = sortedClaims.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  return (
    <UnifiedMedicalTable
      columns={COLUMNS}
      rows={paginatedClaims}
      loading={loading}
      totalCount={totalCount}
      page={page}
      rowsPerPage={rowsPerPage}
      onPageChange={onPageChange}
      onRowsPerPageChange={onRowsPerPageChange}
      rowsPerPageOptions={[10, 25, 50, 100]}
      sortBy={orderBy}
      sortDirection={order}
      onSort={handleSort}
      renderCell={renderCellValue}
      renderExpandedRow={renderExpandedRow}
      isRowExpandable={(row) => row._raw?.lines?.length > 0}
      getRowKey={(claim) => claim.id}
      emptyMessage="لا توجد مطالبات مطابقة للفلاتر المحددة"
      emptyIcon={DescriptionIcon}
      loadingMessage="جارِ تحميل المطالبات..."
      size="small"
    />
  );
};

ClaimsTable.propTypes = {
  claims: PropTypes.array.isRequired,
  loading: PropTypes.bool,
  totalCount: PropTypes.number.isRequired,
  page: PropTypes.number.isRequired,
  rowsPerPage: PropTypes.number.isRequired,
  onPageChange: PropTypes.func.isRequired,
  onRowsPerPageChange: PropTypes.func.isRequired
};

export default ClaimsTable;



