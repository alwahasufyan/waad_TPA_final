import { useMemo, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

// material-ui
import { Box, Grid, Stack, Typography, IconButton, Skeleton, Divider } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import PendingIcon from '@mui/icons-material/Pending';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PeopleIcon from '@mui/icons-material/People';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import DescriptionIcon from '@mui/icons-material/Description';
import TaskAltIcon from '@mui/icons-material/TaskAlt';

// project imports
import EmployerFilterSelector from 'components/tba/EmployerFilterSelector';
import DashboardKpiCard from 'components/dashboard/DashboardKpiCard';
import SystemCategoryCard from 'components/dashboard/SystemCategoryCard';
import { colorForKey } from 'components/dashboard/tileColor';
import DailyWorkItem from 'components/dashboard/DailyWorkItem';

// contexts / hooks
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import { useDashboardStats } from 'hooks/useDashboardStats';
import useRBACSidebar from 'hooks/useRBACSidebar';
import useDailyWorkItems from 'hooks/useDailyWorkItems';
import useAuth from 'hooks/useAuth';
import { getDefaultRouteForRole } from 'utils/roleRoutes';

// config / tokens
import { CATEGORY_GROUPS, resolveAccessibleModules } from 'config/dashboardCategories';
import { dashboardNeutral, dashboardShape, dashboardStatus, resolveDashboardPrimary } from 'themes/dashboardTokens';

export default function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { settings } = useCompanySettings();
  const primaryColor = resolveDashboardPrimary(settings?.primaryColor);

  // ── Role-based redirect (unchanged behaviour) ──────────────────────────────
  const userRoles = useMemo(() => {
    try {
      let localUser = {};
      try {
        const stored = localStorage.getItem('user');
        if (stored && stored !== 'undefined' && stored !== 'null') localUser = JSON.parse(stored);
      } catch {
        /* ignore */
      }
      const current = user || localUser;
      const roles = [];
      if (Array.isArray(current?.roles)) roles.push(...current.roles.map((r) => (typeof r === 'string' ? r : r?.name)).filter(Boolean));
      if (typeof current?.role === 'string' && current.role.trim()) roles.push(current.role.trim());
      return [...new Set(roles.map((r) => r.toUpperCase()))];
    } catch {
      return [];
    }
  }, [user]);

  const isMedicalReviewer = userRoles.includes('MEDICAL_REVIEWER');
  const isProviderRole = userRoles.includes('PROVIDER_STAFF') || userRoles.includes('PROVIDER');

  useEffect(() => {
    if (isProviderRole) navigate(getDefaultRouteForRole('PROVIDER_STAFF'), { replace: true });
  }, [isMedicalReviewer, isProviderRole, navigate]);

  // ── Data (operational only — no financial fields used) ─────────────────────
  const {
    summary,
    loading: summaryLoading,
    refresh: refreshSummary
  } = useDashboardStats({
    // WAAD-RBAC-REVIEWER-PROVIDER-SCOPING-2: MEDICAL_REVIEWER used to be
    // excluded here entirely (dashboard always showed empty state) — the
    // backend now scopes this data to the reviewer's assigned providers
    // (DashboardService), so it's safe to fetch for reviewers too.
    enabled: !isProviderRole,
    silentOnForbidden: true
  });

  const totalClaims = summary?.totalClaims || 0;
  const openClaims = summary?.openClaims || 0;
  const approvedClaims = summary?.approvedClaims || 0;
  const totalMembers = summary?.totalMembers || 0;
  const activeMembers = summary?.activeMembers || 0;
  const totalProviders = summary?.totalProviders || 0;
  const activeProviders = summary?.activeProviders || 0;
  const totalContracts = summary?.totalContracts || 0;
  const activeContracts = summary?.activeContracts || 0;

  const {
    items: dailyItems,
    allClear,
    loading: dailyLoading,
    preAuthError,
    preAuthForbidden,
    preAuthPendingCount,
    refresh: refreshDaily
  } = useDailyWorkItems({ openClaims, enabled: !isProviderRole });

  const { sidebarGroups } = useRBACSidebar();

  // WAAD-DASHBOARD-REDESIGN-1: the welcome hero + "quick access" card (a
  // curated 3-module subset behind an extra "open all categories" dialog)
  // were removed in favour of showing every RBAC-accessible module inline,
  // grouped by category, right at the top of the page — a reviewer's
  // category set is small enough that a dedicated dialog just added a click
  // for no benefit; an admin's larger set still scans fine as a grid.
  const categorySections = useMemo(() => {
    const modules = resolveAccessibleModules(sidebarGroups || []);
    return CATEGORY_GROUPS.map((group) => ({ ...group, modules: modules.filter((item) => item.group === group.key) })).filter(
      (group) => group.modules.length > 0
    );
  }, [sidebarGroups]);

  const handleRefreshAll = useCallback(() => {
    refreshSummary();
    refreshDaily();
  }, [refreshSummary, refreshDaily]);

  // ── KPI cards (operational counters, real data only — no financial) ─────────
  // Claims counters do not navigate: this system has no operational claims-LIST
  // route (claims are worked via /claims/batches + per-claim medical review),
  // so linking them anywhere would be semantically wrong. People/network
  // counters link to their real list routes.
  const kpis = [
    {
      key: 'total',
      title: 'إجمالي المطالبات',
      value: totalClaims,
      subtitle: 'إجمالي المطالبات',
      icon: ReceiptLongIcon,
      colorKey: 'info',
      to: null
    },
    {
      key: 'open',
      title: 'مطالبات قيد المراجعة',
      value: openClaims,
      subtitle: openClaims > 0 ? 'بحاجة لتدخّل' : 'لا توجد مطالبات معلّقة',
      icon: PendingIcon,
      colorKey: 'warning',
      to: null
    },
    {
      key: 'approved',
      title: 'المطالبات المعتمدة',
      value: approvedClaims,
      subtitle: 'المطالبات المعتمدة',
      icon: CheckCircleIcon,
      colorKey: 'success',
      to: null
    },
    {
      key: 'members',
      title: 'المستفيدون النشطون',
      value: activeMembers,
      subtitle: `إجمالي: ${totalMembers.toLocaleString('en-US')}`,
      icon: PeopleIcon,
      colorKey: 'info',
      to: '/members'
    },
    {
      key: 'providers',
      title: 'مقدمو الخدمات النشطون',
      value: activeProviders,
      subtitle: `إجمالي: ${totalProviders.toLocaleString('en-US')}`,
      icon: LocalHospitalIcon,
      colorKey: 'pending',
      to: '/providers'
    },
    {
      key: 'contracts',
      title: 'العقود النشطة',
      value: activeContracts,
      subtitle: `إجمالي: ${totalContracts.toLocaleString('en-US')}`,
      icon: DescriptionIcon,
      colorKey: 'info',
      to: '/provider-contracts'
    }
  ];

  // WAAD-DASHBOARD-PREAUTH-ALERT-1: the KPI row previously had no
  // pre-authorization counter at all — only claims-related cards existed,
  // even though pending pre-authorizations are just as much a reviewer
  // action item. Hidden (not shown as zero) when the user isn't authorised
  // for pre-auth data, mirroring the Daily Work Box's own forbidden handling.
  if (!isProviderRole && !preAuthForbidden) {
    kpis.splice(2, 0, {
      key: 'preauth-pending',
      title: 'موافقات مسبقة قيد المراجعة',
      value: preAuthPendingCount || 0,
      subtitle: preAuthPendingCount > 0 ? 'بحاجة لتدخّل' : 'لا توجد موافقات معلّقة',
      icon: VerifiedUserIcon,
      colorKey: 'warning',
      to: '/pre-approvals/review'
    });
  }

  if (isProviderRole) return null; // provider users have a dedicated portal landing page

  return (
    <Box
      sx={{
        p: { xs: 1.5, sm: 2.5 },
        bgcolor: dashboardNeutral.pageBg,
        minHeight: 'calc(100vh - 110px)',
        display: 'flex',
        flexDirection: 'column',
        gap: 2.5
      }}
    >
      {/* ── System categories (inline, replaces the old welcome hero + quick-access dialog) ── */}
      <Box
        sx={{
          p: { xs: 1.5, sm: 2 },
          borderRadius: `${dashboardShape.radius + 2}px`,
          bgcolor: dashboardNeutral.surface,
          border: '1px solid',
          borderColor: dashboardNeutral.border,
          boxShadow: dashboardShape.shadowSoft
        }}
      >
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1.5 }} flexWrap="wrap" useFlexGap gap={1}>
          <Typography sx={{ fontSize: '0.85rem', fontWeight: 800, color: dashboardNeutral.textPrimary }}>فئات النظام</Typography>
          <Stack direction="row" alignItems="center" spacing={1}>
            <EmployerFilterSelector size="small" />
            <IconButton size="small" onClick={handleRefreshAll} aria-label="تحديث البيانات" sx={{ color: dashboardNeutral.textMuted }}>
              <RefreshIcon fontSize="small" />
            </IconButton>
          </Stack>
        </Stack>
        {categorySections.length === 0 ? (
          <Typography sx={{ color: dashboardNeutral.textMuted, textAlign: 'center', py: 3, fontSize: '0.85rem' }}>
            لا توجد وحدات متاحة لصلاحياتك الحالية.
          </Typography>
        ) : (
          <Stack spacing={2}>
            {categorySections.map((section) => (
              <Box key={section.key}>
                <Typography sx={{ px: 0.5, mb: 1, fontSize: '0.78rem', fontWeight: 800, color: primaryColor }}>{section.title}</Typography>
                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(88px, 1fr))', gap: 1 }}>
                  {section.modules.map((item) => (
                    <SystemCategoryCard key={item.id} title={item.title} icon={item.icon} color={colorForKey(item.id)} onClick={() => navigate(item.url)} />
                  ))}
                </Box>
              </Box>
            ))}
          </Stack>
        )}
      </Box>

      {/* ── KPI row ──────────────────────────────────────────────────────────── */}
      <Grid container spacing={2.5}>
        {kpis.map((k) => (
          <Grid key={k.key} size={{ xs: 6, sm: 4, md: 2 }}>
            <DashboardKpiCard
              title={k.title}
              value={k.value}
              subtitle={k.subtitle}
              icon={k.icon}
              colorKey={k.colorKey}
              loading={summaryLoading}
              onClick={k.to ? () => navigate(k.to) : undefined}
            />
          </Grid>
        ))}
      </Grid>

      {/* ── Daily Work Box ───────────────────────────────────────────────────── */}
      <Box
        sx={{
          p: { xs: 2, sm: 2.5 },
          borderRadius: `${dashboardShape.radius + 2}px`,
          bgcolor: dashboardNeutral.surface,
          border: '1px solid',
          borderColor: dashboardNeutral.border,
          boxShadow: dashboardShape.shadowSoft
        }}
      >
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1.5 }}>
          <Box>
            <Typography sx={{ fontSize: '1rem', fontWeight: 800, color: dashboardNeutral.textPrimary }}>صندوق العمل اليومي</Typography>
            <Typography sx={{ fontSize: '0.8rem', color: dashboardNeutral.textMuted }}>
              التنبيهات والعناصر التي تنتظر تدخّل المستخدم
            </Typography>
          </Box>
          <IconButton size="small" onClick={handleRefreshAll} aria-label="تحديث" sx={{ color: dashboardNeutral.textMuted }}>
            <RefreshIcon fontSize="small" />
          </IconButton>
        </Stack>
        <Divider sx={{ mb: 2, borderColor: dashboardNeutral.border }} />

        {summaryLoading || dailyLoading ? (
          <Grid container spacing={1.5}>
            {[0, 1, 2].map((i) => (
              <Grid key={i} size={{ xs: 12, md: 4 }}>
                <Skeleton variant="rounded" height={56} />
              </Grid>
            ))}
          </Grid>
        ) : dailyItems.length > 0 ? (
          <Grid container spacing={1.5}>
            {dailyItems.map((item) => (
              <Grid key={item.id} size={{ xs: 12, md: 4 }}>
                <DailyWorkItem
                  label={item.label}
                  count={item.count}
                  colorKey={item.color}
                  iconKey={item.iconKey}
                  onClick={() => navigate(item.to)}
                />
              </Grid>
            ))}
          </Grid>
        ) : allClear ? (
          <Stack alignItems="center" spacing={1} sx={{ py: 4 }}>
            <TaskAltIcon sx={{ fontSize: '2.5rem', color: dashboardStatus.success }} />
            <Typography sx={{ fontWeight: 700, color: dashboardNeutral.textPrimary }}>لا توجد معاملات تحتاج إلى تدخّل حالياً</Typography>
            <Typography sx={{ fontSize: '0.8rem', color: dashboardNeutral.textMuted }}>
              ستظهر هنا المطالبات والموافقات التي تتطلب إجراءً.
            </Typography>
          </Stack>
        ) : (
          <Typography sx={{ py: 3, textAlign: 'center', fontSize: '0.85rem', color: dashboardNeutral.textMuted }}>
            {preAuthError ? 'تعذّر تحميل بعض المؤشرات حالياً.' : 'لا توجد بيانات لعرضها.'}
          </Typography>
        )}
      </Box>

      {/* ── Footer ───────────────────────────────────────────────────────────── */}
      <Box sx={{ mt: 'auto', pt: 1 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} alignItems="center" justifyContent="space-between" spacing={1}>
          <Typography sx={{ fontSize: '0.75rem', color: dashboardNeutral.textMuted }}>
            {settings?.footerText || `© 2026 ${settings?.companyName || 'وعد'} — جميع الحقوق محفوظة`}
          </Typography>
          {settings?.email ? (
            <Typography sx={{ fontSize: '0.75rem', color: dashboardNeutral.textMuted }}>الدعم: {settings.email}</Typography>
          ) : null}
        </Stack>
      </Box>
    </Box>
  );
}
