import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueries } from '@tanstack/react-query';
import {
  Box,
  Alert,
  Chip,
  TextField,
  Button,
  IconButton,
  Stack,
  Grid,
  Card,
  CardContent,
  Typography,
  Tooltip,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  TablePagination,
  CircularProgress
} from '@mui/material';
import {
  Assignment as InboxIcon,
  Refresh as RefreshIcon,
  Visibility as ViewIcon,
  Business as BusinessIcon,
  Search as SearchIcon,
  FilterList as FilterIcon,
  PendingActions as PendingIcon,
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  ReceiptLong as ReceiptIcon
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import { claimsService, medicalReviewersService } from 'services/api';

/**
 * Reviewer claim inbox (CLAIM-REVIEW-SPLIT-2B) — lists claims scoped to the
 * current reviewer's assigned providers (enforced server-side by
 * ClaimService.listClaims via ReviewerProviderIsolationService), filterable
 * by status/provider/keyword (claim number, member name), navigating into
 * the existing ClaimReviewWorkspace on row click.
 *
 * PROVIDER-PORTAL-REVIEW-ROUTING-1/2: the default view must show only claims
 * that actually need reviewer attention (SUBMITTED / UNDER_REVIEW /
 * NEEDS_CORRECTION) and must never silently include DRAFT — a provider's
 * unsubmitted draft is not yet visible to the medical review team. "الكل"
 * therefore means "all review-relevant statuses" (the 6 below), not a true
 * unfiltered query to the backend.
 *
 * CLAIM-REVIEW-INBOX-LOVABLE-POLISH-1: visual layout (provider sidebar, KPI
 * strip, status tabs, claims table) rebuilt to match an attached Lovable
 * reference using MUI + real data only — see the phase report for exactly
 * which elements were adopted vs. adapted. All KPI/provider numbers below
 * reuse the same getFinancialSummary endpoint fixed and tested in ROUTING-1/2
 * — no new backend surface area was added for this visual pass.
 */

const PENDING_REVIEW_STATUSES = ['SUBMITTED', 'UNDER_REVIEW', 'NEEDS_CORRECTION'];
const APPROVED_STATUSES = ['APPROVED', 'BATCHED', 'SETTLED'];
const REJECTED_STATUSES = ['REJECTED'];
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

// Status-tab -> backend status list. "الكل" resolves to the 6 review-relevant
// statuses (never DRAFT — a provider's unsubmitted draft must never leak here).
const STATUS_TABS = [
  { key: 'ALL', label: 'الكل', statuses: REVIEW_STATUSES },
  { key: 'PENDING_REVIEW', label: 'قيد المراجعة', statuses: PENDING_REVIEW_STATUSES, tone: 'info' },
  { key: 'APPROVED', label: 'معتمدة', statuses: APPROVED_STATUSES, tone: 'success' },
  { key: 'REJECTED', label: 'مرفوضة', statuses: REJECTED_STATUSES, tone: 'error' }
];

// No new date-formatting dependency — a small local relative-time helper,
// computed from the real createdAt/updatedAt timestamp only.
const formatRelativeTime = (iso) => {
  if (!iso) return '—';
  const diffMs = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(diffMs) || diffMs < 0) return '—';
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return 'الآن';
  if (minutes < 60) return `قبل ${minutes} د`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `قبل ${hours} س`;
  const days = Math.floor(hours / 24);
  return `قبل ${days} يوم`;
};

// A claim-list row's "service" summary — real data only. A claim with no
// lines loaded shows "—"; one line shows its name; more than one shows a
// count instead of fabricating a combined name.
const formatServiceSummary = (row) => {
  const lines = Array.isArray(row.lines) ? row.lines : [];
  if (lines.length === 0) return '—';
  if (lines.length === 1) return lines[0].serviceName || lines[0].medicalServiceName || '—';
  const firstName = lines[0].serviceName || lines[0].medicalServiceName;
  return firstName ? `${firstName} +${lines.length - 1}` : `${lines.length} خدمات`;
};

const emptyBreakdown = { pendingCount: 0, approvedCount: 0, rejectedCount: 0, pendingAmount: 0, approvedAmount: 0, rejectedAmount: 0 };

const MiniStat = ({ label, value, color }) => (
  <Box
    sx={{
      flex: 1,
      borderRadius: '0.375rem',
      py: 0.4,
      textAlign: 'center',
      bgcolor: (theme) => `color-mix(in srgb, ${theme.palette[color]?.main || theme.palette.grey[500]} 12%, transparent)`
    }}
  >
    <Typography variant="caption" fontWeight={700} sx={{ display: 'block', color: `${color}.main` }}>
      {value}
    </Typography>
    <Typography variant="caption" sx={{ fontSize: '0.625rem', color: 'text.secondary' }}>
      {label}
    </Typography>
  </Box>
);

const KpiCard = ({ icon, label, value, sub, color }) => (
  <Card variant="outlined" sx={{ borderRadius: '0.75rem', height: '100%' }}>
    <CardContent sx={{ p: '1rem', '&:last-child': { pb: '1rem' } }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: '0.5rem' }}>
        <Typography variant="caption" fontWeight={600} color="text.secondary">
          {label}
        </Typography>
        <Box
          sx={{
            width: '1.75rem',
            height: '1.75rem',
            borderRadius: '0.5rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: `${color}.lighter`,
            color: `${color}.main`
          }}
        >
          {icon}
        </Box>
      </Stack>
      <Stack direction="row" alignItems="baseline" spacing={0.75}>
        <Typography variant="h5" fontWeight={700}>
          {value}
        </Typography>
        {sub && (
          <Typography variant="caption" color="text.secondary">
            {sub}
          </Typography>
        )}
      </Stack>
    </CardContent>
  </Card>
);

const ProviderSidebarItem = ({ provider, stats, active, onClick }) => (
  <Card
    variant="outlined"
    onClick={onClick}
    sx={{
      cursor: 'pointer',
      borderRadius: '0.75rem',
      borderColor: active ? 'primary.main' : 'divider',
      borderWidth: active ? 2 : 1,
      bgcolor: active ? 'action.selected' : 'background.paper',
      transition: 'all 0.15s',
      '&:hover': { borderColor: 'primary.light' }
    }}
  >
    <CardContent sx={{ p: '0.75rem', '&:last-child': { pb: '0.75rem' } }}>
      <Stack direction="row" alignItems="flex-start" justifyContent="space-between" spacing={1}>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ minWidth: 0 }}>
          <Box
            sx={{
              width: '2rem',
              height: '2rem',
              flexShrink: 0,
              borderRadius: '0.5rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: active ? 'primary.main' : 'action.hover',
              color: active ? 'primary.contrastText' : 'text.secondary'
            }}
          >
            <BusinessIcon fontSize="small" />
          </Box>
          <Typography variant="body2" fontWeight={600} noWrap>
            {provider.name}
          </Typography>
        </Stack>
        <Chip
          size="small"
          label={stats.pendingCount + stats.approvedCount + stats.rejectedCount}
          sx={{ fontWeight: 700, flexShrink: 0 }}
        />
      </Stack>
      <Stack direction="row" spacing={0.75} sx={{ mt: '0.6rem' }}>
        <MiniStat label="مراجعة" value={stats.pendingCount} color="info" />
        <MiniStat label="معتمدة" value={stats.approvedCount} color="success" />
        <MiniStat label="مرفوضة" value={stats.rejectedCount} color="error" />
      </Stack>
    </CardContent>
  </Card>
);

const ClaimReviewInbox = () => {
  const navigate = useNavigate();

  const [claims, setClaims] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalRows, setTotalRows] = useState(0);

  const [statusTab, setStatusTab] = useState('PENDING_REVIEW');
  const [selectedProviderId, setSelectedProviderId] = useState('');
  const [search, setSearch] = useState('');
  const [providers, setProviders] = useState([]);

  const effectiveStatuses = useMemo(
    () => STATUS_TABS.find((t) => t.key === statusTab)?.statuses || PENDING_REVIEW_STATUSES,
    [statusTab]
  );

  useEffect(() => {
    medicalReviewersService
      .getMyProviders()
      .then((list) => setProviders(Array.isArray(list) ? list : []))
      .catch(() => setProviders([]));
  }, []);

  // Default to the reviewer's first assigned provider once loaded — clicking
  // the same provider again clears the selection (back to "all my providers").
  useEffect(() => {
    if (providers.length > 0 && !selectedProviderId) {
      setSelectedProviderId(providers[0].id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [providers]);

  // One set of pending/approved/rejected financial-summary calls per assigned
  // provider — reused for both the sidebar breakdown and the KPI strip totals,
  // so nothing is fetched twice. Same endpoint fixed/tested in ROUTING-1/2.
  const summaryQueries = useQueries({
    queries: providers.flatMap((p) => [
      {
        queryKey: ['reviewer-inbox-summary', p.id, 'pending'],
        queryFn: () => claimsService.getFinancialSummary({ providerId: p.id, status: PENDING_REVIEW_STATUSES })
      },
      {
        queryKey: ['reviewer-inbox-summary', p.id, 'approved'],
        queryFn: () => claimsService.getFinancialSummary({ providerId: p.id, status: APPROVED_STATUSES })
      },
      {
        queryKey: ['reviewer-inbox-summary', p.id, 'rejected'],
        queryFn: () => claimsService.getFinancialSummary({ providerId: p.id, status: REJECTED_STATUSES })
      }
    ])
  });

  const statsByProvider = useMemo(() => {
    const map = {};
    providers.forEach((p, index) => {
      const pending = summaryQueries[index * 3]?.data;
      const approved = summaryQueries[index * 3 + 1]?.data;
      const rejected = summaryQueries[index * 3 + 2]?.data;
      map[p.id] = {
        pendingCount: pending?.claimsCount ?? 0,
        approvedCount: approved?.claimsCount ?? 0,
        rejectedCount: rejected?.claimsCount ?? 0,
        pendingAmount: Number(pending?.totalClaimsAmount || 0),
        approvedAmount: Number(approved?.totalApprovedAmount || 0),
        rejectedAmount: Number(rejected?.totalClaimsAmount || 0)
      };
    });
    return map;
  }, [providers, summaryQueries]);

  const globalTotals = useMemo(() => {
    return Object.values(statsByProvider).reduce(
      (acc, s) => ({
        pendingCount: acc.pendingCount + s.pendingCount,
        approvedCount: acc.approvedCount + s.approvedCount,
        rejectedCount: acc.rejectedCount + s.rejectedCount,
        approvedAmount: acc.approvedAmount + s.approvedAmount
      }),
      { pendingCount: 0, approvedCount: 0, rejectedCount: 0, approvedAmount: 0 }
    );
  }, [statsByProvider]);

  const activeProviderStats = statsByProvider[selectedProviderId] || emptyBreakdown;
  const activeProvider = providers.find((p) => String(p.id) === String(selectedProviderId)) || null;

  const fetchClaims = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await claimsService.list({
        page: page + 1,
        size: pageSize,
        sortBy: 'createdAt',
        sortDir: 'desc',
        status: effectiveStatuses,
        providerId: selectedProviderId || undefined,
        search: search || undefined
      });
      setClaims(response.items || []);
      setTotalRows(response.total || 0);
    } catch (err) {
      setError(err.userMessage || err.response?.data?.messageAr || err.response?.data?.message || 'فشل في تحميل قائمة المطالبات');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, effectiveStatuses, selectedProviderId, search]);

  useEffect(() => {
    fetchClaims();
  }, [fetchClaims]);

  // NOTE ON DTO GAPS (documented in the phase report): ClaimResponse has no
  // dedicated "card number" field for the member — memberNationalNumber is
  // the closest available identifier and is shown alongside the member's
  // name. If neither is present, the cell shows "—", never a fabricated value.
  const getMemberCell = (row) => {
    const name = row.memberFullName || row.memberName || null;
    const idNumber = row.memberNationalNumber || null;
    if (name && idNumber) return `${name} — ${idNumber}`;
    return name || idNumber || '—';
  };

  const TABLE_COLUMNS = [
    { key: 'status', label: 'الحالة' },
    { key: 'claimNumber', label: 'رقم المطالبة الرسمي' },
    { key: 'member', label: 'المؤمن عليه' },
    { key: 'service', label: 'الخدمة' },
    { key: 'requestedAmount', label: 'المبلغ' },
    { key: 'approvedAmount', label: 'المعتمد النهائي' },
    { key: 'receivedAt', label: 'الاستلام' },
    { key: 'createdBy', label: 'أنشأها' },
    { key: 'submittedBy', label: 'أرسلها' },
    { key: 'reviewedBy', label: 'راجعها' },
    { key: 'actions', label: 'إجراء' }
  ];

  return (
    <>
      <ModernPageHeader
        title="صندوق مراجعة المطالبات"
        subtitle="مراجعة واعتماد المطالبات المقدمة من مقدمي الخدمة المعتمدين"
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

      {/* KPI strip — global totals across all assigned providers */}
      <Grid container spacing={2} sx={{ mb: '1.0rem' }}>
        <Grid size={{ xs: 6, md: 3 }}>
          <KpiCard
            icon={<ReceiptIcon fontSize="small" />}
            label="إجمالي المطالبات"
            value={globalTotals.pendingCount + globalTotals.approvedCount + globalTotals.rejectedCount}
            sub={`${providers.length} مقدم خدمة`}
            color="primary"
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <KpiCard icon={<PendingIcon fontSize="small" />} label="قيد المراجعة" value={globalTotals.pendingCount} color="info" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <KpiCard
            icon={<CheckCircleIcon fontSize="small" />}
            label="المعتمد النهائي"
            value={globalTotals.approvedAmount.toFixed(2)}
            sub="د.ل"
            color="success"
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <KpiCard icon={<CancelIcon fontSize="small" />} label="مرفوضة" value={globalTotals.rejectedCount} color="error" />
        </Grid>
      </Grid>

      {/* Two-column workspace */}
      <Grid container spacing={2}>
        {/* Provider sidebar */}
        <Grid size={{ xs: 12, md: 3.5 }}>
          <MainCard sx={{ p: '0.75rem' }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: '0.75rem', px: '0.25rem' }}>
              <Typography variant="subtitle2" fontWeight={700}>
                مقدمو الخدمة
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {providers.length} جهة
              </Typography>
            </Stack>
            <Stack spacing={1}>
              {providers.length === 0 && (
                <Typography variant="body2" color="text.secondary" sx={{ p: '0.75rem', textAlign: 'center' }}>
                  لا يوجد مقدمو خدمة معتمدون لك حالياً.
                </Typography>
              )}
              {providers.map((p) => (
                <ProviderSidebarItem
                  key={p.id}
                  provider={p}
                  stats={statsByProvider[p.id] || emptyBreakdown}
                  active={String(selectedProviderId) === String(p.id)}
                  onClick={() => {
                    setPage(0);
                    setSelectedProviderId((prev) => (String(prev) === String(p.id) ? '' : p.id));
                  }}
                />
              ))}
            </Stack>
          </MainCard>
        </Grid>

        {/* Claims workspace */}
        <Grid size={{ xs: 12, md: 8.5 }}>
          <Stack spacing={2}>
            {/* Selected provider summary */}
            <MainCard>
              <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} spacing={2}>
                <Stack direction="row" alignItems="center" spacing={1.5}>
                  <Box
                    sx={{
                      width: '2.75rem',
                      height: '2.75rem',
                      borderRadius: '0.75rem',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      bgcolor: 'primary.lighter',
                      color: 'primary.main'
                    }}
                  >
                    <BusinessIcon />
                  </Box>
                  <Box>
                    <Typography variant="subtitle1" fontWeight={700}>
                      {activeProvider ? activeProvider.name : 'كل مقدمي الخدمة المعتمدين لي'}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {activeProviderStats.pendingCount + activeProviderStats.approvedCount + activeProviderStats.rejectedCount} مطالبة
                    </Typography>
                  </Box>
                </Stack>
                <Stack direction="row" spacing={1.5}>
                  <Box sx={{ minWidth: '7.5rem', border: 1, borderColor: 'divider', borderRadius: '0.5rem', px: '0.75rem', py: '0.5rem' }}>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      المطلوب
                    </Typography>
                    <Typography variant="body2" fontWeight={700} color="info.main">
                      {(activeProviderStats.pendingAmount + activeProviderStats.approvedAmount + activeProviderStats.rejectedAmount).toFixed(2)}
                    </Typography>
                  </Box>
                  <Box sx={{ minWidth: '7.5rem', border: 1, borderColor: 'divider', borderRadius: '0.5rem', px: '0.75rem', py: '0.5rem' }}>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      المعتمد
                    </Typography>
                    <Typography variant="body2" fontWeight={700} color="success.main">
                      {activeProviderStats.approvedAmount.toFixed(2)}
                    </Typography>
                  </Box>
                </Stack>
              </Stack>

              {/* Status tabs */}
              <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: '1rem', pt: '0.75rem', borderTop: 1, borderColor: 'divider' }}>
                {STATUS_TABS.map((tab) => {
                  const count =
                    tab.key === 'ALL'
                      ? activeProviderStats.pendingCount + activeProviderStats.approvedCount + activeProviderStats.rejectedCount
                      : tab.key === 'PENDING_REVIEW'
                        ? activeProviderStats.pendingCount
                        : tab.key === 'APPROVED'
                          ? activeProviderStats.approvedCount
                          : activeProviderStats.rejectedCount;
                  const isActive = statusTab === tab.key;
                  return (
                    <Chip
                      key={tab.key}
                      clickable
                      color={isActive ? tab.tone || 'primary' : 'default'}
                      variant={isActive ? 'filled' : 'outlined'}
                      onClick={() => {
                        setPage(0);
                        setStatusTab(tab.key);
                      }}
                      label={`${tab.label}: ${count}`}
                    />
                  );
                })}
              </Stack>
            </MainCard>

            {/* Filter bar */}
            <Stack direction="row" spacing={1.5} alignItems="center">
              <TextField
                fullWidth
                size="small"
                placeholder="بحث برقم المطالبة الرسمي أو اسم المؤمن عليه"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    setPage(0);
                    fetchClaims();
                  }
                }}
                InputProps={{ startAdornment: <SearchIcon fontSize="small" sx={{ color: 'text.disabled', mr: 1 }} /> }}
              />
              <Tooltip title="المرشحات المتقدمة قيد التطوير">
                <span>
                  <Button variant="outlined" startIcon={<FilterIcon />} disabled>
                    مرشحات متقدمة
                  </Button>
                </span>
              </Tooltip>
            </Stack>

            {/* Claims table — plain MUI Table (CLAIM-REVIEW-INBOX-LOVABLE-POLISH-1
                post-review fix): the previous DataGrid used rem-string
                width/minWidth values on column defs, which DataGrid requires
                as plain pixel numbers — the invalid values collapsed every
                column to effectively zero width, so rows existed in the DOM
                but rendered with no visible content. A plain Table sidesteps
                that whole class of bug and needs no column-width plumbing. */}
            <MainCard sx={{ p: 0 }}>
              <TableContainer sx={{ maxHeight: '31.25rem' }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      {TABLE_COLUMNS.map((col) => (
                        <TableCell key={col.key} align={col.key === 'actions' ? 'center' : 'right'} sx={{ fontWeight: 700 }}>
                          {col.label}
                        </TableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {loading && (
                      <TableRow>
                        <TableCell colSpan={TABLE_COLUMNS.length} align="center" sx={{ py: '2.5rem' }}>
                          <CircularProgress size={28} />
                        </TableCell>
                      </TableRow>
                    )}
                    {!loading && claims.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={TABLE_COLUMNS.length} align="center" sx={{ py: '2.5rem', color: 'text.secondary' }}>
                          لا توجد مطالبات مطابقة
                        </TableCell>
                      </TableRow>
                    )}
                    {!loading &&
                      claims.map((row) => (
                        <TableRow
                          key={row.id}
                          hover
                          onClick={() =>
                            navigate(`/claims/${row.id}/medical-review`, {
                              state: { claimIds: claims.map((c) => c.id) }
                            })
                          }
                          sx={{ cursor: 'pointer' }}
                        >
                          <TableCell align="right">{getStatusChip(row.status)}</TableCell>
                          <TableCell align="right">{row.claimNumber || '—'}</TableCell>
                          <TableCell align="right">{getMemberCell(row)}</TableCell>
                          <TableCell align="right">{formatServiceSummary(row)}</TableCell>
                          <TableCell align="right">{row.requestedAmount != null ? Number(row.requestedAmount).toFixed(2) : '—'}</TableCell>
                          <TableCell align="right">{row.approvedAmount != null ? Number(row.approvedAmount).toFixed(2) : '—'}</TableCell>
                          <TableCell align="right">{formatRelativeTime(row.createdAt)}</TableCell>
                          <TableCell align="right">{row.createdBy || '—'}</TableCell>
                          <TableCell align="right">{row.submittedBy || '—'}</TableCell>
                          <TableCell align="right">{row.reviewedBy || '—'}</TableCell>
                          <TableCell align="center">
                            <Tooltip title="عرض">
                              <IconButton
                                size="small"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  navigate(`/claims/${row.id}/medical-review`, {
                                    state: { claimIds: claims.map((c) => c.id) }
                                  });
                                }}
                              >
                                <ViewIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </TableCell>
                        </TableRow>
                      ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <TablePagination
                component="div"
                count={totalRows}
                page={page}
                onPageChange={(_e, newPage) => setPage(newPage)}
                rowsPerPage={pageSize}
                onRowsPerPageChange={(e) => {
                  setPageSize(parseInt(e.target.value, 10));
                  setPage(0);
                }}
                rowsPerPageOptions={[10, 20, 50]}
                labelRowsPerPage="عدد الصفوف:"
                labelDisplayedRows={({ from, to, count }) => `${from}–${to} من ${count}`}
              />
            </MainCard>
          </Stack>
        </Grid>
      </Grid>
    </>
  );
};

export default ClaimReviewInbox;
