import PropTypes from 'prop-types';
import { Card, CardActionArea, Box, Stack, Typography, alpha } from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import { dashboardStatus, dashboardNeutral, dashboardShape } from 'themes/dashboardTokens';
import { resolveModuleIcon } from './moduleIcons';

/**
 * DailyWorkItem — a single operational alert in the "Daily Work Box".
 * Semantic colour lives in the icon + count badge + start accent only.
 * Fully clickable to an existing route. Count is always a real API value.
 */
export default function DailyWorkItem({ label, count, colorKey = 'info', iconKey = 'all', onClick }) {
  const accent = dashboardStatus[colorKey] || dashboardStatus.info;
  const Icon = resolveModuleIcon(iconKey);

  return (
    <Card
      elevation={0}
      sx={{
        borderRadius: `${dashboardShape.radiusSm}px`,
        border: '1px solid',
        borderColor: dashboardNeutral.border,
        bgcolor: dashboardNeutral.surface,
        boxShadow: 'none',
        overflow: 'hidden',
        transition: `box-shadow ${dashboardShape.transition}, border-color ${dashboardShape.transition}`,
        '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
        '&:hover': { boxShadow: dashboardShape.shadowSoft, borderColor: alpha(accent, 0.4) }
      }}
    >
      <CardActionArea
        onClick={onClick}
        sx={{
          p: '0.75rem 0.875rem',
          borderInlineStart: '3px solid',
          borderInlineStartColor: accent
        }}
      >
        <Stack direction="row" alignItems="center" spacing={1.25}>
          <Box
            sx={{
              flexShrink: 0,
              width: 36,
              height: 36,
              borderRadius: `${dashboardShape.radiusSm}px`,
              bgcolor: alpha(accent, 0.12),
              color: accent,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <Icon sx={{ fontSize: '1.2rem' }} />
          </Box>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="body2" sx={{ fontWeight: 700, color: dashboardNeutral.textPrimary }} noWrap>
              {label}
            </Typography>
          </Box>
          <Box
            sx={{
              flexShrink: 0,
              minWidth: 30,
              px: 0.75,
              py: 0.125,
              borderRadius: '999px',
              bgcolor: alpha(accent, 0.14),
              color: accent,
              textAlign: 'center'
            }}
          >
            <Typography sx={{ fontSize: '0.8rem', fontWeight: 800, fontFamily: "'Roboto', sans-serif" }}>
              {typeof count === 'number' ? count.toLocaleString('en-US') : count}
            </Typography>
          </Box>
          <ChevronLeftIcon sx={{ fontSize: '1.1rem', color: dashboardNeutral.textMuted, flexShrink: 0 }} />
        </Stack>
      </CardActionArea>
    </Card>
  );
}

DailyWorkItem.propTypes = {
  label: PropTypes.string.isRequired,
  count: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
  colorKey: PropTypes.oneOf(['success', 'warning', 'error', 'info', 'pending']),
  iconKey: PropTypes.string,
  onClick: PropTypes.func.isRequired
};
