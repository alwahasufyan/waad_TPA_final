import PropTypes from 'prop-types';
import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Dialog, Box, Stack, Typography, IconButton, Divider, alpha } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';

import useRBACSidebar from 'hooks/useRBACSidebar';
import { dashboardNeutral, dashboardShape, resolveDashboardPrimary } from 'themes/dashboardTokens';
import SystemCategoryCard from './SystemCategoryCard';

// Calm, colourful palette — each tile gets a stable colour from its own key.
const TILE_PALETTE = ['#3B6F91', '#2E7D52', '#6C63A8', '#C58A16', '#147D75', '#B64B43', '#2F7DA1', '#B5729E', '#4C8C7D', '#7A5C3E'];
const colorForKey = (key = '') => {
  let h = 0;
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) >>> 0;
  return TILE_PALETTE[h % TILE_PALETTE.length];
};

/**
 * SystemCategoriesDialog — the "System Categories" launcher (Odoo-style).
 * - Light, white container sized to its content (no large empty box).
 * - Shows EVERY leaf the user's role can reach (the whole RBAC-filtered menu,
 *   including sub-items), each as a small colourful app tile. No group headings.
 * - MUI Dialog provides Esc-to-close, focus trap, scroll lock and RTL.
 */
export default function SystemCategoriesDialog({ open, onClose, primaryColor }) {
  const navigate = useNavigate();
  const { sidebarItems } = useRBACSidebar();
  const primary = resolveDashboardPrimary(primaryColor);

  // Every navigable leaf (RBAC-filtered), de-duplicated by URL.
  const tiles = useMemo(() => {
    const seen = new Set();
    const list = [];
    (sidebarItems || []).forEach((it) => {
      if (!it?.url || !it?.title || seen.has(it.url)) return;
      seen.add(it.url);
      list.push({ id: it.id || it.url, title: it.title, url: it.url, icon: it.icon, color: colorForKey(it.id || it.url) });
    });
    return list;
  }, [sidebarItems]);

  const cols = Math.min(Math.max(tiles.length, 1), 6);

  const handleOpen = (url) => {
    if (url) navigate(url);
    onClose?.();
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth={false}
      aria-labelledby="system-categories-title"
      slotProps={{
        backdrop: { sx: { bgcolor: alpha('#0E1F22', 0.45), backdropFilter: 'blur(2px)' } },
        paper: {
          sx: {
            width: 'fit-content',
            maxWidth: 'min(92vw, 680px)',
            m: 2,
            bgcolor: dashboardNeutral.pageBg,
            border: '1px solid',
            borderColor: dashboardNeutral.border,
            borderRadius: `${dashboardShape.radius + 4}px`,
            color: dashboardNeutral.textPrimary,
            boxShadow: '0 24px 64px rgba(22,38,37,0.22)'
          }
        }
      }}
    >
      {/* Header */}
      <Box sx={{ p: { xs: 2, sm: 2.5 }, pb: 1.5 }}>
        <Stack direction="row" alignItems="flex-start" justifyContent="space-between" spacing={2}>
          <Box>
            <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, letterSpacing: 1.5, color: primary }}>WAAD TPA</Typography>
            <Typography
              id="system-categories-title"
              sx={{ fontSize: '1.2rem', fontWeight: 800, color: dashboardNeutral.textPrimary, mt: 0.5 }}
            >
              فئات النظام
            </Typography>
            <Typography sx={{ fontSize: '0.82rem', color: dashboardNeutral.textMuted, mt: 0.5 }}>
              اختر الوحدة للوصول السريع إليها.
            </Typography>
          </Box>
          <IconButton
            onClick={onClose}
            aria-label="إغلاق"
            sx={{ color: dashboardNeutral.textMuted, '&:hover': { color: dashboardNeutral.textPrimary, bgcolor: alpha(primary, 0.08) } }}
          >
            <CloseIcon />
          </IconButton>
        </Stack>
      </Box>
      <Divider sx={{ borderColor: dashboardNeutral.border }} />

      {/* Body — flat grid of all accessible leaves, sized to content */}
      <Box sx={{ p: { xs: 1.5, sm: 2 }, maxHeight: '64vh', overflowY: 'auto', overflowX: 'auto' }}>
        {tiles.length === 0 ? (
          <Typography sx={{ color: dashboardNeutral.textMuted, textAlign: 'center', py: 6 }}>
            لا توجد وحدات متاحة لصلاحياتك الحالية.
          </Typography>
        ) : (
          <Box sx={{ display: 'grid', gridTemplateColumns: `repeat(${cols}, 96px)`, gap: 1, justifyContent: 'center' }}>
            {tiles.map((t) => (
              <SystemCategoryCard key={t.id} title={t.title} icon={t.icon} color={t.color} onClick={() => handleOpen(t.url)} />
            ))}
          </Box>
        )}
      </Box>

      {/* Footer */}
      <Divider sx={{ borderColor: dashboardNeutral.border }} />
      <Box sx={{ px: { xs: 2, sm: 2.5 }, py: 1.25 }}>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ color: dashboardNeutral.textMuted }}>
          <LockOutlinedIcon sx={{ fontSize: '1rem', color: primary }} />
          <Typography sx={{ fontSize: '0.72rem' }}>وصول آمن وفق صلاحيات المستخدم.</Typography>
        </Stack>
      </Box>
    </Dialog>
  );
}

SystemCategoriesDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  primaryColor: PropTypes.string
};
