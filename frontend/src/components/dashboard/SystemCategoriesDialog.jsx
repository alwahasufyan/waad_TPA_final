import PropTypes from 'prop-types';
import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Dialog, Box, Stack, Typography, IconButton, Divider, alpha } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import useRBACSidebar from 'hooks/useRBACSidebar';
import { dashboardNeutral, dashboardShape, resolveDashboardPrimary } from 'themes/dashboardTokens';
import { CATEGORY_GROUPS, resolveAccessibleModules } from 'config/dashboardCategories';
import SystemCategoryCard from './SystemCategoryCard';
import { colorForKey } from './tileColor';

export default function SystemCategoriesDialog({ open, onClose, primaryColor }) {
  const navigate = useNavigate();
  const { sidebarGroups } = useRBACSidebar();
  const primary = resolveDashboardPrimary(primaryColor);
  const sections = useMemo(() => {
    const modules = resolveAccessibleModules(sidebarGroups || []);
    return CATEGORY_GROUPS.map((group) => ({ ...group, modules: modules.filter((item) => item.group === group.key) }))
      .filter((group) => group.modules.length > 0);
  }, [sidebarGroups]);

  const handleOpen = (url) => { if (url) navigate(url); onClose?.(); };

  return (
    <Dialog open={open} onClose={onClose} maxWidth={false} aria-labelledby="system-categories-title"
      slotProps={{ backdrop: { sx: { bgcolor: alpha('#0E1F22', 0.45), backdropFilter: 'blur(2px)' } }, paper: { sx: {
        width: 'min(92vw, 760px)', m: 2, bgcolor: dashboardNeutral.pageBg, border: '1px solid', borderColor: dashboardNeutral.border,
        borderRadius: `${dashboardShape.radius + 4}px`, color: dashboardNeutral.textPrimary, boxShadow: '0 24px 64px rgba(22,38,37,0.22)'
      } } }}>
      <Box sx={{ p: { xs: 2, sm: 2.5 }, pb: 1.5 }}>
        <Stack direction="row" alignItems="flex-start" justifyContent="space-between" spacing={2}>
          <Box><Typography sx={{ fontSize: '0.7rem', fontWeight: 700, letterSpacing: 1.5, color: primary }}>WAAD TPA</Typography>
            <Typography id="system-categories-title" sx={{ fontSize: '1.2rem', fontWeight: 800, mt: 0.5 }}>فئات النظام</Typography>
            <Typography sx={{ fontSize: '0.82rem', color: dashboardNeutral.textMuted, mt: 0.5 }}>الوحدات المتاحة حسب صلاحيات المستخدم.</Typography></Box>
          <IconButton onClick={onClose} aria-label="إغلاق"><CloseIcon /></IconButton>
        </Stack>
      </Box>
      <Divider sx={{ borderColor: dashboardNeutral.border }} />
      <Box sx={{ p: { xs: 1.5, sm: 2 }, maxHeight: '64vh', overflowY: 'auto' }}>
        {sections.length === 0 ? <Typography sx={{ color: dashboardNeutral.textMuted, textAlign: 'center', py: 6 }}>لا توجد وحدات متاحة لصلاحياتك الحالية.</Typography> :
          <Stack spacing={2}>{sections.map((section) => <Box key={section.key}>
            <Typography sx={{ px: 0.5, mb: 1, fontWeight: 800, color: primary }}>{section.title}</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(96px, 1fr))', gap: 1 }}>
              {section.modules.map((item) => <SystemCategoryCard key={item.id} title={item.title} icon={item.icon} color={colorForKey(item.id)} onClick={() => handleOpen(item.url)} />)}
            </Box>
          </Box>)}</Stack>}
      </Box>
      <Divider sx={{ borderColor: dashboardNeutral.border }} />
      <Box sx={{ px: { xs: 2, sm: 2.5 }, py: 1.25 }}><Stack direction="row" alignItems="center" spacing={1} sx={{ color: dashboardNeutral.textMuted }}>
        <LockOutlinedIcon sx={{ fontSize: '1rem', color: primary }} /><Typography sx={{ fontSize: '0.72rem' }}>وصول آمن وفق صلاحيات المستخدم.</Typography>
      </Stack></Box>
    </Dialog>
  );
}
SystemCategoriesDialog.propTypes = { open: PropTypes.bool.isRequired, onClose: PropTypes.func.isRequired, primaryColor: PropTypes.string };
