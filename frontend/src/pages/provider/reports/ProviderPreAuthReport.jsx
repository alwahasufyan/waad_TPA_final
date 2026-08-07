import { useState, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import {
  ClearAll as ClearAllIcon,
  Download as DownloadIcon,
  ExpandLess as ExpandLessIcon,
  ExpandMore as ExpandMoreIcon,
  FilterList as FilterListIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
  VerifiedUser as PreAuthIcon,
  Visibility as VisibilityIcon,
  ReceiptLong as ReceiptLongIcon
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers';
import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import { UnifiedMedicalTable } from 'components/common';
import PermissionGuard from 'components/PermissionGuard';
import axiosClient from 'utils/axios';
import { formatCurrency, formatDate } from 'utils/formatters';

/**
 * تقرير الموافقات المسبقة - بوابة مقدم الخدمة
 */
const ProviderPreAuthReport = () => {
  const navigate = useNavigate();
  const claimState = (preAuth) => ({
    visitId: preAuth.visitId,
    preAuthorizationId: preAuth.preAuthId,
    memberId: preAuth.memberId,
    memberName: preAuth.memberName,
    memberCivilId: preAuth.civilId,
    memberCardNumber: preAuth.memberCardNumber || preAuth.memberBarcode,
    employerName: preAuth.employerName,
    providerId: preAuth.providerId,
    providerName: preAuth.providerName
  });
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
  const [selectedPreAuth, setSelectedPreAuth] = useState(null);

  // ========================================
  // DATA FETCHING
  // ========================================
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['provider-preauth-report', filters, paginationModel],
    queryFn: async () => {
      const fromDate = filters.fromDate?.format ? filters.fromDate.format('YYYY-MM-DD') : filters.fromDate;
      const toDate = filters.toDate?.format ? filters.toDate.format('YYYY-MM-DD') : filters.toDate;
      const status = typeof filters.status === 'string' ? filters.status.trim().toUpperCase() : filters.status;

      const params = {
        page: paginationModel.page,
        size: paginationModel.pageSize,
        sortBy: 'requestDate',
        sortDir: 'DESC',
        ...(fromDate && { fromDate }),
        ...(toDate && { toDate }),
        ...(status && { status }),
        ...(filters.memberBarcode && { memberBarcode: filters.memberBarcode })
      };
      const response = await axiosClient.get('/api/v1/provider/reports/pre-auth', { params });
      return response?.data?.data ?? response?.data ?? { content: [], totalElements: 0 };
    }
  });

  const preAuthData = useMemo(() => data?.content || [], [data]);
  const totalElements = data?.totalElements || 0;

  // ========================================
  // HANDLERS
  // ========================================
  const handleFilterChange = (field, value) => {
    setFilters((prev) => ({ ...prev, [field]: value }));
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

      const response = await axiosClient.get('/api/v1/provider/reports/pre-auth/export', {
        params,
        responseType: 'blob'
      });

      const blobUrl = window.URL.createObjectURL(new Blob([response.data]));
      const link = window.document.createElement('a');
      link.href = blobUrl;
      link.setAttribute('download', `preauth_report_${new Date().toISOString().slice(0, 10)}.xlsx`);
      window.document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(blobUrl);
    } catch (exportError) {
      console.error('Export pre-auth report failed:', exportError);
      alert('فشل تصدير تقرير الموافقات المسبقة');
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
        id: 'preAuthNumber',
        label: 'رقم الموافقة',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'requestDate',
        label: 'تاريخ الطلب',
        minWidth: '8.125rem',
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
        id: 'serviceName',
        label: 'الخدمة',
        minWidth: '12.5rem',
        sortable: false
      },
      {
        id: 'sessionsRequested',
        label: 'الجلسات المطلوبة',
        minWidth: '8.125rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'sessionsApproved',
        label: 'الجلسات الموافقة',
        minWidth: '8.125rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'sessionsUsed',
        label: 'المستخدم',
        minWidth: '6.25rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'requestedAmount',
        label: 'المبلغ المطلوب',
        minWidth: '8.125rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'approvedAmount',
        label: 'المبلغ الموافق',
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
        id: 'actions',
        label: 'الإجراءات',
        minWidth: '12rem',
        align: 'center',
        sortable: false
      }
    ],
    []
  );

  // ========================================
  // CELL RENDERER
  // ========================================
  const getStatusChip = (status, label) => {
    const colors = {
      PENDING: 'warning',
      UNDER_REVIEW: 'info',
      NEEDS_CORRECTION: 'warning',
      APPROVAL_IN_PROGRESS: 'info',
      APPROVED: 'success',
      PARTIALLY_APPROVED: 'warning',
      ACKNOWLEDGED: 'info',
      REJECTED: 'error',
      EXPIRED: 'default',
      CANCELLED: 'default'
    };

    return <Chip label={label} color={colors[status] || 'default'} size="small" sx={{ fontWeight: 600 }} />;
  };

  const renderCell = useCallback((preAuth, column) => {
    if (!preAuth) return null;

    switch (column.id) {
      case 'preAuthNumber':
        return (
          <Typography variant="body2" fontWeight={600}>
            {preAuth.preAuthNumber || '-'}
          </Typography>
        );

      case 'requestDate':
        return formatDate(preAuth.requestDate);

      case 'memberName':
        return preAuth.memberName || '-';

      case 'memberBarcode':
        return preAuth.memberBarcode || '-';

      case 'serviceName':
        if (Array.isArray(preAuth.lines) && preAuth.lines.length > 1) {
          return `${preAuth.serviceName || ''} (+${preAuth.lines.length - 1} أخرى)`.trim();
        }
        return preAuth.serviceName || '-';

      case 'sessionsRequested':
        return preAuth.sessionsRequested || 0;

      case 'sessionsApproved':
        return (
          <Typography variant="body2" color="success.main" fontWeight={600}>
            {preAuth.sessionsApproved || 0}
          </Typography>
        );

      case 'sessionsUsed':
        const used = preAuth.sessionsUsed || 0;
        const approved = preAuth.sessionsApproved || 0;
        const percentage = approved > 0 ? (used / approved) * 100 : 0;

        return (
          <Box sx={{ width: '100%' }}>
            <Typography variant="caption" display="block" align="center">
              {used} / {approved}
            </Typography>
            <LinearProgress variant="determinate" value={percentage} sx={{ height: '0.375rem', borderRadius: '0.25rem' }} />
          </Box>
        );

      case 'requestedAmount':
        return formatCurrency(preAuth.requestedAmount);

      case 'approvedAmount':
        return (
          <Typography variant="body2" color="success.main" fontWeight={600}>
            {formatCurrency(preAuth.approvedAmount)}
          </Typography>
        );

      case 'status':
        return getStatusChip(preAuth.status, preAuth.statusLabel);

      case 'actions': {
        const status = String(preAuth.status || '').toUpperCase();
        const fullyApproved = ['APPROVED', 'ACKNOWLEDGED'].includes(status);
        const hasClaim = Boolean(preAuth.claimId);
        return (
          <Stack direction="row" spacing={0.5} justifyContent="center">
            <Tooltip title="عرض تفاصيل الموافقة">
              <IconButton size="small" color="primary" onClick={() => setSelectedPreAuth(preAuth)}>
                <VisibilityIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            {hasClaim ? (
              <Button
                size="small"
                variant="outlined"
                startIcon={<ReceiptLongIcon />}
                onClick={() => navigate('/provider/claims/submit', { state: { claimId: preAuth.claimId, visitId: preAuth.visitId } })}
              >
                المطالبة
              </Button>
            ) : fullyApproved && preAuth.visitId ? (
              <Button
                size="small"
                variant="outlined"
                startIcon={<ReceiptLongIcon />}
                onClick={() => navigate('/provider/claims/submit', { state: claimState(preAuth) })}
              >
                إنشاء مطالبة
              </Button>
            ) : null}
          </Stack>
        );
      }

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
    { label: 'الموافقات المسبقة' }
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
          title="تقرير الموافقات المسبقة"
          subtitle="جميع طلبات الموافقات المسبقة"
          breadcrumbs={breadcrumbs}
          icon={PreAuthIcon}
          actions={pageActions}
        />

        {/* Error Alert */}
        {isError && (
          <Alert severity="error" sx={{ mb: '1.0rem' }}>
            {error?.message || 'حدث خطأ أثناء تحميل البيانات'}
          </Alert>
        )}

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
                  <MenuItem value="PENDING">قيد الانتظار</MenuItem>
                  <MenuItem value="UNDER_REVIEW">قيد المراجعة</MenuItem>
                  <MenuItem value="NEEDS_CORRECTION">بحاجة لاستكمال بيانات</MenuItem>
                  <MenuItem value="APPROVAL_IN_PROGRESS">جارٍ الاعتماد</MenuItem>
                  <MenuItem value="APPROVED">موافق عليها</MenuItem>
                  <MenuItem value="PARTIALLY_APPROVED">موافقة جزئية</MenuItem>
                  <MenuItem value="ACKNOWLEDGED">تم الاطلاع</MenuItem>
                  <MenuItem value="REJECTED">مرفوضة</MenuItem>
                  <MenuItem value="EXPIRED">منتهية الصلاحية</MenuItem>
                  <MenuItem value="CANCELLED">ملغاة</MenuItem>
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
            persistKey="provider-preauth-report"
            columns={columns}
            rows={preAuthData}
            loading={isLoading}
            renderCell={renderCell}
            totalCount={totalElements}
            page={paginationModel.page}
            rowsPerPage={paginationModel.pageSize}
            onPageChange={(newPage) => setPaginationModel((prev) => ({ ...prev, page: newPage }))}
            onRowsPerPageChange={(newPageSize) => setPaginationModel({ page: 0, pageSize: newPageSize })}
            emptyIcon={PreAuthIcon}
            emptyMessage="لا توجد طلبات موافقات مسبقة مسجلة حالياً"
          />
        </MainCard>

        <Dialog open={Boolean(selectedPreAuth)} onClose={() => setSelectedPreAuth(null)} fullWidth maxWidth="md">
          <DialogTitle>تفاصيل الموافقة المسبقة</DialogTitle>
          <DialogContent dividers>
            {selectedPreAuth && (
              <>
                <Grid container spacing={2} sx={{ pt: 0.5 }}>
                  {[
                    ['رقم الموافقة', selectedPreAuth.preAuthNumber],
                    ['الحالة', selectedPreAuth.statusLabel || selectedPreAuth.status],
                    ['تاريخ الطلب', formatDate(selectedPreAuth.requestDate)],
                    ['المستفيد', selectedPreAuth.memberName],
                    ['الباركود', selectedPreAuth.memberBarcode],
                    ['المبلغ المطلوب', formatCurrency(selectedPreAuth.requestedAmount)],
                    ['المبلغ الموافق عليه', formatCurrency(selectedPreAuth.approvedAmount)],
                    ['ملاحظات المراجع', selectedPreAuth.reviewerNotes || '-']
                  ].map(([label, value]) => (
                    <Grid key={label} size={{ xs: 12, sm: 6 }}>
                      <Typography variant="caption" color="text.secondary" display="block">
                        {label}
                      </Typography>
                      <Typography variant="body1" fontWeight={600}>
                        {value || '-'}
                      </Typography>
                    </Grid>
                  ))}
                </Grid>

                {/* WAAD-PREAUTH-LINE-QUANTITY-FIX-1: show EVERY requested
                    service with its own quantity/price/decision — this
                    modal previously only ever showed the header's single
                    "line 0" snapshot (serviceName/requestedAmount), so a
                    2-service pre-authorization looked like it had only one
                    service, and quantity was invisible entirely. */}
                <Typography variant="subtitle2" fontWeight={700} sx={{ mt: 2.5, mb: 1 }}>
                  الخدمات المطلوبة
                </Typography>
                <TableContainer component={Paper} variant="outlined">
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>الخدمة</TableCell>
                        <TableCell align="center">الكمية</TableCell>
                        <TableCell align="right">سعر الوحدة</TableCell>
                        <TableCell align="right">الإجمالي المطلوب</TableCell>
                        <TableCell align="right">المعتمد</TableCell>
                        <TableCell align="center">القرار</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(selectedPreAuth.lines && selectedPreAuth.lines.length > 0
                        ? selectedPreAuth.lines
                        : [
                            {
                              serviceName: selectedPreAuth.serviceName,
                              quantity: selectedPreAuth.sessionsRequested || 1,
                              unitPrice: selectedPreAuth.requestedAmount,
                              contractPrice: selectedPreAuth.requestedAmount,
                              approvedAmount: selectedPreAuth.approvedAmount,
                              reviewerDecision: null
                            }
                          ]
                      ).map((line, index) => (
                        <TableRow key={index}>
                          <TableCell>{line.serviceName || '-'}</TableCell>
                          <TableCell align="center">{line.quantity ?? 1}</TableCell>
                          <TableCell align="right">{formatCurrency(line.unitPrice)}</TableCell>
                          <TableCell align="right">{formatCurrency(line.contractPrice)}</TableCell>
                          <TableCell align="right">{formatCurrency(line.approvedAmount)}</TableCell>
                          <TableCell align="center">
                            {line.reviewerDecision === 'APPROVED' && <Chip label="موافق" color="success" size="small" />}
                            {line.reviewerDecision === 'REJECTED' && <Chip label="مرفوض" color="error" size="small" />}
                            {line.reviewerDecision === 'CLARIFICATION_REQUIRED' && (
                              <Chip label="بحاجة إيضاح" color="warning" size="small" />
                            )}
                            {!line.reviewerDecision && <Chip label="قيد المراجعة" color="default" size="small" />}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setSelectedPreAuth(null)}>إغلاق</Button>
            {selectedPreAuth && ['APPROVED', 'ACKNOWLEDGED'].includes(String(selectedPreAuth.status || '').toUpperCase()) && selectedPreAuth.visitId && !selectedPreAuth.claimId && (
              <Button
                variant="contained"
                startIcon={<ReceiptLongIcon />}
                onClick={() => {
                  const current = selectedPreAuth;
                  setSelectedPreAuth(null);
                  navigate('/provider/claims/submit', { state: claimState(current) });
                }}
              >
                إنشاء مطالبة
              </Button>
            )}
          </DialogActions>
        </Dialog>
      </Box>
    </PermissionGuard>
  );
};

export default ProviderPreAuthReport;
