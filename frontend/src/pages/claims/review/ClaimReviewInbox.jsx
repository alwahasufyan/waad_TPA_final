import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Alert, Chip, MenuItem, TextField, Button, Stack } from '@mui/material';
import { Assignment as InboxIcon, Refresh as RefreshIcon, OpenInNew as OpenReviewIcon } from '@mui/icons-material';
import { DataGrid } from '@mui/x-data-grid';
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import { claimsService, medicalReviewersService } from 'services/api';

/**
 * Reviewer claim inbox (CLAIM-REVIEW-SPLIT-2B) — lists claims scoped to the
 * current reviewer's assigned providers (enforced server-side by
 * ClaimService.listClaims via ReviewerProviderIsolationService), filterable
 * by status/provider/keyword (claim number, member name), navigating into
 * the existing ClaimReviewWorkspace on row click.
 */

const REVIEW_STATUSES = ['SUBMITTED', 'UNDER_REVIEW', 'NEEDS_CORRECTION', 'APPROVED', 'REJECTED', 'SETTLED'];

const STATUS_CONFIG = {
  DRAFT: { label: 'مسودة', color: 'default' },
  SUBMITTED: { label: 'مقدمة', color: 'info' },
  UNDER_REVIEW: { label: 'تحت المراجعة', color: 'info' },
  NEEDS_CORRECTION: { label: 'معلقة للمراجعة', color: 'warning' },
  APPROVED: { label: 'معتمدة', color: 'success' },
  REJECTED: { label: 'مرفوضة', color: 'error' },
  SETTLED: { label: 'تمت التسوية', color: 'success' }
};

const getStatusChip = (status) => {
  const config = STATUS_CONFIG[status] || { label: status || '-', color: 'default' };
  return <Chip size="small" color={config.color} label={config.label} />;
};

const ClaimReviewInbox = () => {
  const navigate = useNavigate();

  const [claims, setClaims] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalRows, setTotalRows] = useState(0);

  const [statusFilter, setStatusFilter] = useState('');
  const [providerFilter, setProviderFilter] = useState('');
  const [search, setSearch] = useState('');
  const [providers, setProviders] = useState([]);

  useEffect(() => {
    medicalReviewersService
      .getMyProviders()
      .then((list) => setProviders(Array.isArray(list) ? list : []))
      .catch(() => setProviders([]));
  }, []);

  const fetchClaims = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await claimsService.list({
        page: page + 1,
        size: pageSize,
        sortBy: 'createdAt',
        sortDir: 'desc',
        status: statusFilter || undefined,
        providerId: providerFilter || undefined,
        search: search || undefined
      });
      setClaims(response.items || []);
      setTotalRows(response.total || 0);
    } catch (err) {
      setError(err.userMessage || err.response?.data?.messageAr || err.response?.data?.message || 'فشل في تحميل قائمة المطالبات');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter, providerFilter, search]);

  useEffect(() => {
    fetchClaims();
  }, [fetchClaims]);

  // NOTE ON DTO GAPS (documented in the phase report): ClaimResponse has no
  // dedicated "card number" field for the member — memberNationalNumber is
  // the closest available identifier and is shown alongside the member's
  // name. If neither is present, the cell shows "—", never a fabricated value.
  const columns = [
    {
      field: 'claimNumber',
      headerName: 'رقم المطالبة الرسمي',
      width: '11.25rem',
      // CLAIM-NUMBERING-1's official reference only — never falls back to
      // "CLM-" + id, a TAG number, or the row index.
      valueGetter: (value, row) => row.claimNumber || '—'
    },
    {
      field: 'providerName',
      headerName: 'مقدم الخدمة',
      flex: 1,
      minWidth: '9.375rem',
      valueGetter: (value, row) => row.providerName || '—'
    },
    {
      field: 'member',
      headerName: 'العضو / رقم البطاقة',
      flex: 1,
      minWidth: '11.25rem',
      valueGetter: (value, row) => {
        const name = row.memberFullName || row.memberName || null;
        const idNumber = row.memberNationalNumber || null;
        if (name && idNumber) return `${name} — ${idNumber}`;
        return name || idNumber || '—';
      }
    },
    {
      field: 'status',
      headerName: 'الحالة',
      width: '8.75rem',
      renderCell: (params) => getStatusChip(params.value)
    },
    {
      field: 'requestedAmount',
      headerName: 'إجمالي المطالبة',
      width: '8.75rem',
      valueGetter: (value, row) => (row.requestedAmount != null ? Number(row.requestedAmount).toFixed(2) : '—')
    },
    {
      field: 'approvedAmount',
      headerName: 'المعتمد النهائي إن وجد',
      width: '9.375rem',
      valueGetter: (value, row) => (row.approvedAmount != null ? Number(row.approvedAmount).toFixed(2) : '—')
    },
    {
      field: 'visitType',
      headerName: 'نوع الزيارة',
      width: '8.125rem',
      valueGetter: (value, row) => row.visitType || '—'
    },
    {
      field: 'serviceDate',
      headerName: 'تاريخ الزيارة',
      width: '8.125rem',
      valueGetter: (value, row) => (row.serviceDate ? new Date(row.serviceDate).toLocaleDateString('en-US') : '—')
    },
    {
      field: 'updatedAt',
      headerName: 'آخر تحديث',
      width: '8.75rem',
      valueGetter: (value, row) => {
        const ts = row.updatedAt || row.createdAt;
        return ts ? new Date(ts).toLocaleDateString('en-US') : '—';
      }
    },
    {
      field: 'actions',
      headerName: 'إجراء',
      width: '10.0rem',
      sortable: false,
      renderCell: (params) => (
        <Button
          size="small"
          variant="outlined"
          startIcon={<OpenReviewIcon fontSize="small" />}
          onClick={(e) => {
            e.stopPropagation();
            navigate(`/claims/${params.row.id}/medical-review`);
          }}
        >
          فتح المراجعة
        </Button>
      )
    }
  ];

  return (
    <>
      <ModernPageHeader
        title="صندوق مراجعة المطالبات"
        subtitle="المطالبات المقدمة من مقدمي الخدمة المعتمدين لك"
        icon={InboxIcon}
        actions={
          <Button startIcon={<RefreshIcon />} onClick={fetchClaims} disabled={loading}>
            تحديث
          </Button>
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: '1.0rem' }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <MainCard>
        <Stack direction="row" spacing={2} sx={{ mb: '1.0rem', flexWrap: 'wrap' }}>
          <TextField
            select
            label="الحالة"
            size="small"
            sx={{ minWidth: '10.0rem' }}
            value={statusFilter}
            onChange={(e) => {
              setPage(0);
              setStatusFilter(e.target.value);
            }}
          >
            <MenuItem value="">الكل</MenuItem>
            {REVIEW_STATUSES.map((s) => (
              <MenuItem key={s} value={s}>
                {STATUS_CONFIG[s]?.label || s}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            select
            label="مقدم الخدمة"
            size="small"
            sx={{ minWidth: '12.5rem' }}
            value={providerFilter}
            onChange={(e) => {
              setPage(0);
              setProviderFilter(e.target.value);
            }}
          >
            <MenuItem value="">كل مقدمي الخدمة المعتمدين لي</MenuItem>
            {providers.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.name}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            label="بحث (رقم المطالبة الرسمي / اسم المؤمن عليه)"
            size="small"
            sx={{ minWidth: '15.625rem', flexGrow: 1 }}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                setPage(0);
                fetchClaims();
              }
            }}
          />
        </Stack>

        <Box sx={{ minHeight: '25.0rem', width: '100%' }}>
          <DataGrid
            autoHeight
            rows={claims}
            getRowId={(row) => row.id}
            columns={columns}
            loading={loading}
            paginationMode="server"
            rowCount={totalRows}
            paginationModel={{ page, pageSize }}
            onPaginationModelChange={(model) => {
              setPage(model.page);
              setPageSize(model.pageSize);
            }}
            pageSizeOptions={[10, 20, 50]}
            disableSelectionOnClick
            onRowClick={(params) => navigate(`/claims/${params.row.id}/medical-review`)}
            localeText={{
              noRowsLabel: 'لا توجد مطالبات مطابقة',
              MuiTablePagination: {
                labelRowsPerPage: 'عدد الصفوف:'
              }
            }}
            sx={{
              '& .MuiDataGrid-row': {
                cursor: 'pointer',
                '&:hover': {
                  backgroundColor: 'action.hover'
                }
              }
            }}
          />
        </Box>
      </MainCard>
    </>
  );
};

export default ClaimReviewInbox;
