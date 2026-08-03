import { useState, useMemo, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, Card, CardContent, Chip, Collapse, Grid, IconButton, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material';
import {
  ClearAll as ClearAllIcon,
  Download as DownloadIcon,
  ExpandLess as ExpandLessIcon,
  ExpandMore as ExpandMoreIcon,
  FilterList as FilterListIcon,
  Receipt as ReceiptIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers';
import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import { UnifiedMedicalTable } from 'components/common';
import PermissionGuard from 'components/PermissionGuard';
import axiosClient from 'utils/axios';
import { formatCurrency, formatDate } from 'utils/formatters';
import claimsService from 'services/api/claims.service';

/**
 * تقرير المطالبات - بوابة مقدم الخدمة
 * عرض جميع المطالبات المقدمة من مقدم الخدمة مع إمكانية الفلترة والبحث
 */
const ProviderClaimsReport = () => {
  const normalizeStatusFilter = (value) => {
    const status = typeof value === 'string' ? value.trim().toUpperCase() : value;
    return status === 'PAID' ? 'SETTLED' : status;
  };

  // ========================================
  // STATE
  // ========================================
  const [showFilters, setShowFilters] = useState(true);
  const [filters, setFilters] = useState({
    fromDate: null,
    toDate: null,
    status: '',
    memberBarcode: ''
  });

  const [paginationModel, setPaginationModel] = useState({
    page: 0,
    pageSize: 20
  });
  const [isExporting, setIsExporting] = useState(false);

  // ========================================
  // DATA FETCHING
  // ========================================
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['provider-claims-report', filters, paginationModel],
    queryFn: async () => {
      const fromDate = filters.fromDate?.format ? filters.fromDate.format('YYYY-MM-DD') : filters.fromDate;
      const toDate = filters.toDate?.format ? filters.toDate.format('YYYY-MM-DD') : filters.toDate;
      const status = typeof filters.status === 'string' ? filters.status.trim().toUpperCase() : filters.status;

      const params = {
        page: paginationModel.page,
        size: paginationModel.pageSize,
        sortBy: 'serviceDate',
        sortDir: 'DESC',
        ...(fromDate && { fromDate }),
        ...(toDate && { toDate }),
        ...(status && { status }),
        ...(filters.memberBarcode && { memberBarcode: filters.memberBarcode })
      };
      const response = await axiosClient.get('/api/v1/provider/reports/claims', { params });
      return response?.data?.data ?? response?.data ?? { content: [], totalElements: 0 };
    }
  });

  const claimsData = useMemo(() => data?.content || [], [data]);

  // Financial cards intentionally use the same server-side source as the
  // claims review inbox. Never sum the current table page in the browser.
  const { data: financialSummary } = useQuery({
    queryKey: ['provider-claims-financial-summary', filters],
    queryFn: async () => {
      const fromDate = filters.fromDate?.format ? filters.fromDate.format('YYYY-MM-DD') : filters.fromDate;
      const toDate = filters.toDate?.format ? filters.toDate.format('YYYY-MM-DD') : filters.toDate;
      const status = typeof filters.status === 'string' ? filters.status.trim().toUpperCase() : filters.status;
      return claimsService.getFinancialSummary({ fromDate, toDate, status });
    }
  });
  const totalElements = financialSummary?.claimsCount ?? data?.totalElements ?? 0;
  const summary = {
    claimed: financialSummary?.totalClaimsAmount ?? 0,
    approved: financialSummary?.totalApprovedAmount ?? 0,
    net: (financialSummary?.totalApprovedAmount ?? 0) - (financialSummary?.totalPaidAmount ?? 0)
  };

  // ========================================
  // HANDLERS
  // ========================================
  const handleFilterChange = (field, value) => {
    setFilters((prev) => ({ ...prev, [field]: field === 'status' ? normalizeStatusFilter(value) : value }));
    setPaginationModel((prev) => ({ ...prev, page: 0 }));
  };

  const handleClearFilters = () => {
    setFilters({
      fromDate: null,
      toDate: null,
      status: '',
      memberBarcode: ''
    });
    setPaginationModel((prev) => ({ ...prev, page: 0 }));
  };

  const hasActiveFilters = useMemo(() => {
    return filters.fromDate || filters.toDate || filters.status || filters.memberBarcode;
  }, [filters]);

  const handleExportExcel = async () => {
    try {
      setIsExporting(true);
      const fromDate = filters.fromDate?.format ? filters.fromDate.format('YYYY-MM-DD') : filters.fromDate;
      const toDate = filters.toDate?.format ? filters.toDate.format('YYYY-MM-DD') : filters.toDate;
      const status = typeof filters.status === 'string' ? filters.status.trim().toUpperCase() : filters.status;

      const params = {
        ...(fromDate && { fromDate }),
        ...(toDate && { toDate }),
        ...(status && { status }),
        ...(filters.memberBarcode && { memberBarcode: filters.memberBarcode })
      };

      const response = await axiosClient.get('/api/v1/provider/reports/claims/export', {
        params,
        responseType: 'blob'
      });

      const blobUrl = window.URL.createObjectURL(new Blob([response.data]));
      const link = window.document.createElement('a');
      link.href = blobUrl;
      link.setAttribute('download', `claims_report_${new Date().toISOString().slice(0, 10)}.xlsx`);
      window.document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(blobUrl);
    } catch (exportError) {
      console.error('Export claims report failed:', exportError);
      alert('فشل تصدير تقرير المطالبات');
    } finally {
      setIsExporting(false);
    }
  };

  // ========================================
  // TABLE COLUMNS
  // ========================================
  const columns = useMemo(
    () => [
      {
        id: 'claimNumber',
        label: 'رقم المطالبة',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'serviceDate',
        label: 'تاريخ الخدمة',
        minWidth: '8.125rem',
        sortable: false
      },
      {
        id: 'serviceTime',
        label: 'وقت الخدمة',
        minWidth: '7.5rem',
        sortable: false
      },
      {
        id: 'memberName',
        label: 'اسم المنتفع',
        minWidth: '11.25rem',
        sortable: false
      },
      {
        id: 'memberBarcode',
        label: 'الباركود',
        minWidth: '8.125rem',
        sortable: false
      },
      {
        id: 'employerName',
        label: 'الشركة',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'claimedAmount',
        label: 'المبلغ المطلوب',
        minWidth: '8.125rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'approvedAmount',
        label: 'المعتمد النهائي',
        minWidth: '8.125rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'netAmount',
        label: 'الصافي',
        minWidth: '8.125rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'status',
        label: 'الحالة',
        minWidth: '8.75rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'servicesCount',
        label: 'عدد الخدمات',
        minWidth: '6.875rem',
        align: 'center',
        sortable: false
      }
    ],
    []
  );

  // Keep labels in source-safe Unicode even when older mojibake labels remain
  // in imported legacy code.
  const displayColumns = useMemo(
    () => columns.map((column) => column.id === 'serviceTime'
      ? { ...column, label: '\u0648\u0642\u062a \u0627\u0644\u062e\u062f\u0645\u0629' }
      : column),
    [columns]
  );

  // ========================================
  // CELL RENDERER
  // ========================================
  const getStatusChip = (status, label) => {
    const colors = {
      DRAFT: 'default',
      SUBMITTED: 'info',
      UNDER_REVIEW: 'warning',
      APPROVAL_IN_PROGRESS: 'warning',
      APPROVED: 'success',
      BATCHED: 'secondary',
      NEEDS_CORRECTION: 'warning',
      REJECTED: 'error',
      PAID: 'success',
      SETTLED: 'success'
    };

    return <Chip label={label} color={colors[status] || 'default'} size="small" sx={{ fontWeight: 600 }} />;
  };

  const renderCell = useCallback((claim, column) => {
    if (!claim) return null;

    switch (column.id) {
      case 'claimNumber':
        return (
          <Typography variant="body2" fontWeight={600}>
            {claim.claimNumber || '-'}
          </Typography>
        );

      case 'serviceDate':
        return formatDate(claim.serviceDate || claim.claimDate);

      case 'serviceTime':
        return claim.serviceTime || '-';

      case 'memberName':
        return claim.memberName || '-';

      case 'memberBarcode':
        return claim.memberBarcode || '-';

      case 'employerName':
        return claim.employerName || '-';

      case 'claimedAmount':
        return formatCurrency(claim.claimedAmount);

      case 'approvedAmount':
        return (
          <Typography variant="body2" color="success.main" fontWeight={600}>
            {formatCurrency(claim.approvedAmount)}
          </Typography>
        );

      case 'netAmount':
        return formatCurrency(claim.netAmount);

      case 'status':
        return getStatusChip(claim.status, claim.statusLabel);

      case 'servicesCount':
        return claim.servicesCount || 0;

      default:
        return '-';
    }
  }, []);

  // ========================================
  // BREADCRUMBS
  // ========================================
  const breadcrumbs = [
    { label: 'بوابة مقدم الخدمة', path: '/provider' },
    { label: 'التقارير', path: '/provider/reports' },
    { label: 'المطالبات' }
  ];

  // ========================================
  // PAGE ACTIONS
  // ========================================
  const pageActions = (
    <Stack direction="row" spacing={1}>
      <Button variant="outlined" startIcon={<DownloadIcon />} onClick={handleExportExcel} disabled={isExporting || isLoading}>
        {isExporting ? 'جاري التصدير...' : 'تصدير Excel'}
      </Button>
      <Tooltip title="تحديث">
        <IconButton onClick={() => refetch()} color="primary" disabled={isLoading}>
          <RefreshIcon />
        </IconButton>
      </Tooltip>
    </Stack>
  );

  // ========================================
  // RENDER
  // ========================================
  return (
    <PermissionGuard resource="provider_portal" action="view" fallback={<Alert severity="error">ليس لديك صلاحية لعرض هذه الصفحة</Alert>}>
      <Box>
        {/* Page Header */}
        <UnifiedPageHeader
          title="تقرير المطالبات"
          subtitle="جميع المطالبات المقدمة من مقدم الخدمة"
          breadcrumbs={breadcrumbs}
          icon={ReceiptIcon}
          actions={pageActions}
        />

        {/* Error Alert */}
        {isError && (
          <Alert severity="error" sx={{ mb: '1.0rem' }}>
            {error?.message || 'حدث خطأ أثناء تحميل البيانات'}
          </Alert>
        )}

        <Grid container spacing={2} sx={{ mb: '1.5rem', display: 'none' }}>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card variant="outlined"><CardContent sx={{ textAlign: 'center', py: '1rem' }}><Typography variant="h4" color="primary">{totalElements}</Typography><Typography variant="body2" color="text.secondary">إجمالي المطالبات</Typography></CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card variant="outlined"><CardContent sx={{ textAlign: 'center', py: '1rem' }}><Typography variant="h5" color="warning.main">{formatCurrency(summary.claimed)}</Typography><Typography variant="body2" color="text.secondary">إجمالي المبالغ المطلوبة (الصفحة)</Typography></CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card variant="outlined"><CardContent sx={{ textAlign: 'center', py: '1rem' }}><Typography variant="h5" color="success.main">{formatCurrency(summary.approved)}</Typography><Typography variant="body2" color="text.secondary">إجمالي المبالغ المعتمدة (الصفحة)</Typography></CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card variant="outlined"><CardContent sx={{ textAlign: 'center', py: '1rem' }}><Typography variant="h5" color="info.main">{formatCurrency(summary.net)}</Typography><Typography variant="body2" color="text.secondary">إجمالي الصافي (الصفحة)</Typography></CardContent></Card>
          </Grid>
        </Grid>

        <Grid container spacing={2} sx={{ mb: '1.5rem' }}>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card variant="outlined"><CardContent sx={{ textAlign: 'center', py: '1rem' }}><Typography variant="h4" color="primary">{totalElements}</Typography><Typography variant="body2" color="text.secondary">{'\u0625\u062c\u0645\u0627\u0644\u064a \u0627\u0644\u0645\u0637\u0627\u0644\u0628\u0627\u062a'}</Typography></CardContent></Card></Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card variant="outlined"><CardContent sx={{ textAlign: 'center', py: '1rem' }}><Typography variant="h5" color="warning.main">{formatCurrency(financialSummary?.totalClaimsAmount)}</Typography><Typography variant="body2" color="text.secondary">{'\u0625\u062c\u0645\u0627\u0644\u064a \u0627\u0644\u0645\u0628\u0627\u0644\u063a \u0627\u0644\u0645\u0637\u0644\u0648\u0628\u0629'}</Typography></CardContent></Card></Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card variant="outlined"><CardContent sx={{ textAlign: 'center', py: '1rem' }}><Typography variant="h5" color="success.main">{formatCurrency(financialSummary?.totalApprovedAmount)}</Typography><Typography variant="body2" color="text.secondary">{'\u0625\u062c\u0645\u0627\u0644\u064a \u0627\u0644\u0645\u0628\u0627\u0644\u063a \u0627\u0644\u0645\u0639\u062a\u0645\u062f\u0629'}</Typography></CardContent></Card></Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card variant="outlined"><CardContent sx={{ textAlign: 'center', py: '1rem' }}><Typography variant="h5" color="info.main">{formatCurrency(summary.net)}</Typography><Typography variant="body2" color="text.secondary">{'\u0625\u062c\u0645\u0627\u0644\u064a \u0627\u0644\u0635\u0627\u0641\u064a'}</Typography></CardContent></Card></Grid>
        </Grid>

        {/* Filters */}
        <MainCard sx={{ mb: '1.5rem' }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: showFilters ? 2 : 0 }}>
            <Stack direction="row" alignItems="center" spacing={1}>
              <FilterListIcon color="action" />
              <Typography variant="h6">البحث والفلترة</Typography>
            </Stack>
            <IconButton onClick={() => setShowFilters(!showFilters)} size="small">
              {showFilters ? <ExpandLessIcon /> : <ExpandMoreIcon />}
            </IconButton>
          </Stack>

          <Collapse in={showFilters}>
            <Grid container spacing={2} alignItems="center">
              <Grid size={{ xs: 12, md: 3 }}>
                <DatePicker
                  label="من تاريخ"
                  value={filters.fromDate}
                  onChange={(value) => handleFilterChange('fromDate', value)}
                  slotProps={{
                    textField: {
                      fullWidth: true,
                      size: 'small'
                    }
                  }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 3 }}>
                <DatePicker
                  label="إلى تاريخ"
                  value={filters.toDate}
                  onChange={(value) => handleFilterChange('toDate', value)}
                  slotProps={{
                    textField: {
                      fullWidth: true,
                      size: 'small'
                    }
                  }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 2 }}>
                <TextField
                  fullWidth
                  select
                  label="الحالة"
                  value={filters.status}
                  onChange={(e) => handleFilterChange('status', e.target.value)}
                  size="small"
                >
                  <MenuItem value="">الكل</MenuItem>
                  <MenuItem value="SUBMITTED">مقدمة</MenuItem>
                  <MenuItem value="UNDER_REVIEW">قيد المراجعة</MenuItem>
                  <MenuItem value="APPROVAL_IN_PROGRESS">جاري معالجة الموافقة</MenuItem>
                  <MenuItem value="APPROVED">موافق عليها</MenuItem>
                  <MenuItem value="BATCHED">ضمن دفعة تسوية</MenuItem>
                  <MenuItem value="NEEDS_CORRECTION">تحتاج تصحيح</MenuItem>
                  <MenuItem value="REJECTED">مرفوضة</MenuItem>
                  <MenuItem value="SETTLED">مدفوعة</MenuItem>
                </TextField>
              </Grid>

              <Grid size={{ xs: 12, md: 2 }}>
                <TextField
                  fullWidth
                  label="الباركود"
                  value={filters.memberBarcode}
                  onChange={(e) => handleFilterChange('memberBarcode', e.target.value)}
                  size="small"
                  placeholder="اكتب باركود المنتفع"
                />
              </Grid>

              <Grid size={{ xs: 12, md: 2 }}>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="contained"
                    color="primary"
                    startIcon={<SearchIcon />}
                    onClick={() => refetch()}
                    disabled={isLoading}
                    fullWidth
                  >
                    بحث
                  </Button>
                  <Button variant="outlined" startIcon={<ClearAllIcon />} onClick={handleClearFilters} disabled={!hasActiveFilters}>
                    مسح
                  </Button>
                </Stack>
              </Grid>
            </Grid>
          </Collapse>
        </MainCard>

        {/* Data Table */}
        <MainCard>
          <UnifiedMedicalTable
            persistKey="provider-claims-report"
            columns={displayColumns}
            rows={claimsData}
            loading={isLoading}
            renderCell={renderCell}
            totalCount={totalElements}
            page={paginationModel.page}
            rowsPerPage={paginationModel.pageSize}
            onPageChange={(newPage) => setPaginationModel((prev) => ({ ...prev, page: newPage }))}
            onRowsPerPageChange={(newPageSize) => setPaginationModel({ page: 0, pageSize: newPageSize })}
            emptyIcon={ReceiptIcon}
            emptyMessage="لا توجد مطالبات مسجلة حالياً"
          />
        </MainCard>
      </Box>
    </PermissionGuard>
  );
};

export default ProviderClaimsReport;
