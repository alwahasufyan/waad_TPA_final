import { useState, useEffect, useMemo, useRef } from 'react';
import { useReactToPrint } from 'react-to-print';
import useEmployerScope from 'hooks/useEmployerScope';
import useClaimsReport, { DEFAULT_FILTERS, CLAIM_STATUS_LABELS, ALL_CLAIM_STATUSES } from 'hooks/useClaimsReport';
import { formatNumber } from 'utils/formatters';
import { providersService } from 'services/api/providers.service';
import { exportToExcel } from 'utils/exportUtils';
import { useCompanySettings } from 'contexts/CompanySettingsContext';

// MUI Components
import { Box, Stack, Typography, Alert, Chip, Button } from '@mui/material';

// MUI Icons
import RefreshIcon from '@mui/icons-material/Refresh';
import WarningIcon from '@mui/icons-material/Warning';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import AssignmentIcon from '@mui/icons-material/Assignment';
import BusinessIcon from '@mui/icons-material/Business';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import SearchIcon from '@mui/icons-material/Search';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import PrintIcon from '@mui/icons-material/Print';

// Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { ClaimsTable } from 'components/reports/claims';
import EnterpriseFilters, { useFilterState } from 'components/common/EnterpriseFilters';
import WorkspaceSidebar, { WorkspaceSection } from 'components/common/WorkspaceSidebar';

/**
 * Claims Operational Report
 *
 * READ-ONLY operational view of all finalized claims.
 */
const ClaimsReport = () => {
  const { companyName } = useCompanySettings();

  const [selectedEmployerId, setSelectedEmployerId] = useState(null);
  const { canSelectEmployer, effectiveEmployerId, employers, isEmployerLocked, userEmployerId } = useEmployerScope(selectedEmployerId);

  useEffect(() => {
    if (isEmployerLocked && userEmployerId && !selectedEmployerId) {
      setSelectedEmployerId(userEmployerId);
    }
  }, [isEmployerLocked, userEmployerId, selectedEmployerId]);

  const [selectedProviderId, setSelectedProviderId] = useState(null);
  const [providers, setProviders] = useState([]);

  useEffect(() => {
    const fetchProviders = async () => {
      try {
        const data = await providersService.getSelector();
        const providersList = data ?? [];
        setProviders(Array.isArray(providersList) ? providersList : []);
      } catch (err) {
        console.error('Failed to fetch providers:', err);
        setProviders([]);
      }
    };
    fetchProviders();
  }, []);

  // W1.1: remember filter values across sessions (employer/provider stay page-scoped)
  const [filters, setFilters] = useFilterState('claims-report-filters', DEFAULT_FILTERS);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);

  const { claims, totalFetched, loading, error, pagination, refetch } = useClaimsReport({
    employerId: effectiveEmployerId,
    providerId: selectedProviderId,
    filters
  });

  const totalCount = claims.length;
  const hasPartialData = pagination.totalElements > totalFetched;

  const handleEmployerChange = (employerId) => {
    if (canSelectEmployer) {
      setSelectedEmployerId(employerId);
      setPage(0);
    }
  };

  const handleProviderChange = (providerId) => {
    setSelectedProviderId(providerId);
    setPage(0);
  };

  const handleFilterChange = (newFilters) => {
    setFilters(newFilters);
    setPage(0);
  };

  const handlePageChange = (newPage) => setPage(newPage);
  const handleRowsPerPageChange = (newSize) => { setRowsPerPage(newSize); setPage(0); };

  // ── Enterprise Filter Framework (W1.1) config ──
  const filterFields = useMemo(() => [
    { key: 'employerId', label: 'الشريك', type: 'select', hidden: !canSelectEmployer,
      placeholder: 'جميع الشركاء', icon: <BusinessIcon fontSize="small" />,
      options: (employers || []).map((e) => ({ value: e.id, label: e.name })) },
    { key: 'providerId', label: 'مقدم الخدمة', type: 'select',
      placeholder: 'جميع مقدمي الخدمة', icon: <LocalHospitalIcon fontSize="small" />,
      options: (providers || []).map((p) => ({ value: p.id, label: p.name })) },
    { key: 'statuses', label: 'الحالة', type: 'multiselect',
      options: ALL_CLAIM_STATUSES.map((s) => ({ value: s, label: CLAIM_STATUS_LABELS[s] || s })) },
    { key: 'memberSearch', label: 'بحث بالعضو', type: 'text',
      placeholder: 'اسم العضو...', icon: <SearchIcon fontSize="small" /> },
    { key: 'dateFrom', label: 'من تاريخ', type: 'date', gridSize: 2, icon: <CalendarTodayIcon fontSize="small" /> },
    { key: 'dateTo', label: 'إلى تاريخ', type: 'date', gridSize: 2, icon: <CalendarTodayIcon fontSize="small" /> }
  ], [canSelectEmployer, employers, providers]);

  const filterValues = useMemo(
    () => ({ employerId: selectedEmployerId, providerId: selectedProviderId, ...filters }),
    [selectedEmployerId, selectedProviderId, filters]
  );

  // Route each filter change to the page's existing setters (no data-layer change).
  const handleFiltersChange = (next) => {
    const { employerId, providerId, ...rest } = next;
    if (employerId !== selectedEmployerId) handleEmployerChange(employerId ?? null);
    if (providerId !== selectedProviderId) handleProviderChange(providerId ?? null);
    const current = { statuses: filters.statuses, memberSearch: filters.memberSearch, dateFrom: filters.dateFrom, dateTo: filters.dateTo };
    if (JSON.stringify(rest) !== JSON.stringify(current)) handleFilterChange({ ...filters, ...rest });
  };

  // Active-filter count for the Workspace Sidebar badge (each field counts once).
  const activeFilterCount = useMemo(() => [
    selectedEmployerId,
    selectedProviderId,
    filters.memberSearch?.trim() ? 1 : null,
    filters.dateFrom,
    filters.dateTo,
    filters.statuses?.length ? 1 : null
  ].filter(Boolean).length, [selectedEmployerId, selectedProviderId, filters]);

  // W1.2 fix: print the actual claims report (the table), not a page screenshot.
  const printRef = useRef(null);
  const handlePrint = useReactToPrint({
    contentRef: printRef,
    documentTitle: `تقرير_المطالبات_${new Date().toISOString().slice(0, 10)}`
  });

  const handleExportExcel = () => {
    try {
      const exportData = claims.map((claim) => ({
        'رقم المطالبة': claim._raw?.claimNumber || claim.id,
        'اسم المؤمن عليه': claim.memberName,
        الشريك: claim.employerName,
        'مقدم الخدمة': claim.providerName,
        الحالة: CLAIM_STATUS_LABELS[claim.status] || claim.status,
        'المبلغ المطلوب': claim.requestedAmount,
        'المعتمد النهائي': claim._raw?.approvedAmount || '-',
        'تاريخ الزيارة': claim.visitDate || '-',
        'آخر تحديث': claim.updatedAt ? new Date(claim.updatedAt).toLocaleDateString('en-GB') : '-'
      }));
      const timestamp = new Date().toISOString().slice(0, 10);
      exportToExcel(exportData, `تقرير_المطالبات_${timestamp}`, { companyName });
    } catch (err) {
      console.error('Failed to export Excel:', err);
    }
  };

  return (
    <MainCard>
      {/* Slim header: title + record count + a single Workspace-Sidebar trigger.
          W1.2 (#1): all tools live in the sidebar so the table starts right here. */}
      <ModernPageHeader
        titleKey="تقرير المطالبات التشغيلي"
        titleIcon={<AssignmentIcon color="primary" />}
        subtitleKey="قائمة شاملة بجميع المطالبات المعالجة"
        actions={
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Chip label={`${totalCount} مطالبة`} size="small" color="primary" variant="outlined" />
            {!loading && totalFetched > 0 && (
              <Typography variant="caption" color="text.secondary">
                إجمالي السجلات: <strong>{totalFetched}</strong>
              </Typography>
            )}
            <WorkspaceSidebar title="أدوات العمل" badgeCount={activeFilterCount}>
              <WorkspaceSection label="إجراءات">
                <Stack spacing={1}>
                  <Button fullWidth variant="outlined" color="primary" startIcon={<RefreshIcon />} onClick={refetch} disabled={loading}>
                    تحديث البيانات
                  </Button>
                  <Button fullWidth variant="outlined" color="success" startIcon={<FileDownloadIcon />} onClick={handleExportExcel} disabled={loading || totalCount === 0}>
                    تصدير Excel
                  </Button>
                  <Button fullWidth variant="outlined" color="inherit" startIcon={<PrintIcon />} onClick={handlePrint} disabled={totalCount === 0}>
                    طباعة
                  </Button>
                </Stack>
              </WorkspaceSection>
              <WorkspaceSection label="فلاتر البحث">
                <EnterpriseFilters
                  embedded
                  filterKey="claims-report"
                  fields={filterFields}
                  values={filterValues}
                  onChange={handleFiltersChange}
                />
              </WorkspaceSection>
            </WorkspaceSidebar>
          </Stack>
        }
      />

      {error && (
        <Alert severity="error" icon={<WarningIcon />} sx={{ mb: '1.0rem' }}>
          {error}
        </Alert>
      )}

      {hasPartialData && (
        <Alert severity="warning" sx={{ mb: '1.0rem' }}>
          <Typography variant="body2">
            تم تحميل {formatNumber(totalFetched)} سجل من أصل {formatNumber(pagination.totalElements)} سجل.
          </Typography>
        </Alert>
      )}

      {/* Printable region — react-to-print renders just this node as the report. */}
      <Box ref={printRef}>
        <Box sx={{ display: 'none', '@media print': { display: 'block', mb: 2 } }}>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>تقرير المطالبات التشغيلي</Typography>
          <Typography variant="body2" color="text.secondary">
            {companyName ? `${companyName} — ` : ''}تاريخ الطباعة: {new Date().toLocaleDateString('en-GB')} — عدد المطالبات: {totalCount}
          </Typography>
        </Box>
        <ClaimsTable
          claims={claims}
          loading={loading}
          totalCount={totalCount}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={handlePageChange}
          onRowsPerPageChange={handleRowsPerPageChange}
        />
      </Box>

      <style>
        {`@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }`}
      </style>
    </MainCard>
  );
};

export default ClaimsReport;
