import { useMemo, useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

// material-ui
import { Box, Grid, Stack, Typography, Chip, Button, IconButton, Skeleton, Divider, alpha } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import AppsIcon from '@mui/icons-material/Apps';
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
import PriorityModuleCard from 'components/dashboard/PriorityModuleCard';
import DailyWorkItem from 'components/dashboard/DailyWorkItem';
import SystemCategoriesDialog from 'components/dashboard/SystemCategoriesDialog';

// contexts / hooks
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import { useDashboardStats } from 'hooks/useDashboardStats';
import useRBACSidebar from 'hooks/useRBACSidebar';
import useDailyWorkItems from 'hooks/useDailyWorkItems';
import useAuth from 'hooks/useAuth';
import { getDefaultRouteForRole } from 'utils/roleRoutes';

// config / tokens
import { resolveAccessibleModules, QUICK_ACCESS_IDS } from 'config/dashboardCategories';
import { dashboardNeutral, dashboardShape, dashboardStatus, resolveDashboardPrimary } from 'themes/dashboardTokens';

export default function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { settings } = useCompanySettings();
  const primaryColor = resolveDashboardPrimary(settings?.primaryColor);

  const [categoriesOpen, setCategoriesOpen] = useState(false);

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
    if (isMedicalReviewer) {
      navigate(getDefaultRouteForRole('MEDICAL_REVIEWER'), { replace: true });
      return;
    }
    if (isProviderRole) navigate(getDefaultRouteForRole('PROVIDER_STAFF'), { replace: true });
  }, [isMedicalReviewer, isProviderRole, navigate]);

  // ── Data (operational only — no financial fields used) ─────────────────────
  const {
    summary,
    loading: summaryLoading,
    refresh: refreshSummary
  } = useDashboardStats({
    enabled: !isMedicalReviewer && !isProviderRole,
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
    refresh: refreshDaily
  } = useDailyWorkItems({ openClaims, enabled: !isMedicalReviewer && !isProviderRole });

  const { sidebarGroups } = useRBACSidebar();

  const quickAccess = useMemo(() => {
    const accessible = resolveAccessibleModules(sidebarGroups);
    const byId = Object.fromEntries(accessible.map((m) => [m.id, m]));
    return QUICK_ACCESS_IDS.map((id) => byId[id]).filter(Boolean);
  }, [sidebarGroups]);

  const countFor = useCallback(
    (mod) => {
      if (!mod?.countKey || !summary) return undefined;
      const v = summary[mod.countKey];
      return typeof v === 'number' ? v : undefined;
    },
    [summary]
  );

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

  if (isMedicalReviewer || isProviderRole) return null; // redirecting

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
      {/* ── Hero + Quick access ─────────────────────────────────────────────── */}
      <Grid container spacing={2.5} alignItems="stretch">
        <Grid size={{ xs: 12, md: 7 }}>
          <Box
            sx={{
              height: '100%',
              p: { xs: 2, sm: 3 },
              borderRadius: `${dashboardShape.radius + 2}px`,
              bgcolor: dashboardNeutral.surface,
              border: '1px solid',
              borderColor: dashboardNeutral.border,
              boxShadow: dashboardShape.shadowSoft,
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center'
            }}
          >
            <Chip
              label="منصة متكاملة لإدارة النفقات الطبية"
              size="small"
              icon={<VerifiedUserIcon sx={{ fontSize: '1rem !important', color: `${primaryColor} !important` }} />}
              sx={{ alignSelf: 'flex-start', bgcolor: alpha(primaryColor, 0.08), color: primaryColor, fontWeight: 700, mb: 1.5 }}
            />
            <Typography
              sx={{ fontSize: { xs: '1.5rem', sm: '2rem' }, fontWeight: 800, color: dashboardNeutral.textPrimary, lineHeight: 1.2 }}
            >
              مرحباً بك في {settings?.companyName || 'وعد'} الطبي
            </Typography>
            <Stack direction="row" spacing={1.5} sx={{ mt: 2.5 }} flexWrap="wrap" useFlexGap>
              <Button
                variant="contained"
                startIcon={<AppsIcon />}
                onClick={() => setCategoriesOpen(true)}
                sx={{
                  bgcolor: primaryColor,
                  fontWeight: 700,
                  borderRadius: `${dashboardShape.radiusSm}px`,
                  boxShadow: 'none',
                  '&:hover': { bgcolor: primaryColor, filter: 'brightness(0.94)', boxShadow: dashboardShape.shadowSoft }
                }}
              >
                افتح فئات النظام
              </Button>
              <Button
                variant="outlined"
                onClick={handleRefreshAll}
                startIcon={<RefreshIcon />}
                sx={{
                  color: primaryColor,
                  borderColor: alpha(primaryColor, 0.4),
                  fontWeight: 700,
                  borderRadius: `${dashboardShape.radiusSm}px`,
                  '&:hover': { borderColor: primaryColor, bgcolor: alpha(primaryColor, 0.04) }
                }}
              >
                تحديث البيانات
              </Button>
            </Stack>
          </Box>
        </Grid>

        <Grid size={{ xs: 12, md: 5 }}>
          <Box
            sx={{
              height: '100%',
              p: { xs: 1.5, sm: 2 },
              borderRadius: `${dashboardShape.radius + 2}px`,
              bgcolor: dashboardNeutral.surface,
              border: '1px solid',
              borderColor: dashboardNeutral.border,
              boxShadow: dashboardShape.shadowSoft
            }}
          >
            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1.25 }}>
              <Typography sx={{ fontSize: '0.85rem', fontWeight: 800, color: dashboardNeutral.textPrimary }}>وصول سريع</Typography>
              <EmployerFilterSelector size="small" />
            </Stack>
            <Grid container spacing={1.5}>
              {quickAccess.map((mod) => (
                <Grid key={mod.id} size={{ xs: 6 }}>
                  <PriorityModuleCard
                    title={mod.title}
                    iconKey={mod.iconKey}
                    count={countFor(mod)}
                    countLabel={mod.countLabel}
                    highlight={!!mod.highlight}
                    primaryColor={settings?.primaryColor}
                    onClick={() => navigate(mod.url)}
                  />
                </Grid>
              ))}
              <Grid size={{ xs: 6 }}>
                <PriorityModuleCard
                  title="عرض كل الفئات"
                  iconKey="all"
                  ctaText="فتح النافذة"
                  primaryColor={settings?.primaryColor}
                  onClick={() => setCategoriesOpen(true)}
                />
              </Grid>
            </Grid>
          </Box>
        </Grid>
      </Grid>

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

      <SystemCategoriesDialog
        open={categoriesOpen}
        onClose={() => setCategoriesOpen(false)}
        summary={summary}
        primaryColor={settings?.primaryColor}
      />
    </Box>
  );
}
