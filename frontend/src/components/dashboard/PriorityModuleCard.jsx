import PropTypes from 'prop-types';
import { Card, CardActionArea, Box, Stack, Typography, alpha } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { dashboardNeutral, dashboardShape, resolveDashboardPrimary } from 'themes/dashboardTokens';
import { resolveModuleIcon } from './moduleIcons';

/**
 * PriorityModuleCard — compact "quick access" card on the home hero.
 * Light surface, subtle accent (primary from company settings). Optional real
 * count badge. Whole card clickable. `variant="all"` renders the neutral
 * "view all categories" tile.
 */
export default function PriorityModuleCard({
  title,
  iconKey = 'all',
  count,
  countLabel,
  onClick,
  highlight = false,
  primaryColor,
  ctaText = 'فتح الوحدة'
}) {
  const primary = resolveDashboardPrimary(primaryColor);
  const Icon = resolveModuleIcon(iconKey);
  const showCount = count !== null && count !== undefined;

  return (
    <Card
      elevation={0}
      sx={{
        height: '100%',
        borderRadius: `${dashboardShape.radius}px`,
        bgcolor: highlight ? alpha(primary, 0.05) : dashboardNeutral.surface,
        border: '1px solid',
        borderColor: highlight ? alpha(primary, 0.35) : dashboardNeutral.border,
        boxShadow: dashboardShape.shadowSoft,
        transition: `box-shadow ${dashboardShape.transition}, transform ${dashboardShape.transition}, border-color ${dashboardShape.transition}`,
        '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
        '&:hover': { boxShadow: dashboardShape.shadowHover, transform: 'translateY(-2px)', borderColor: alpha(primary, 0.5) }
      }}
    >
      <CardActionArea onClick={onClick} sx={{ height: '100%', p: '0.875rem' }}>
        <Stack spacing={1.25} sx={{ height: '100%' }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Box
              sx={{
                width: 38,
                height: 38,
                borderRadius: `${dashboardShape.radiusSm}px`,
                bgcolor: alpha(primary, 0.1),
                color: primary,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              <Icon sx={{ fontSize: '1.25rem' }} />
            </Box>
            {showCount ? (
              <Box sx={{ px: 0.875, py: 0.25, borderRadius: '999px', bgcolor: alpha(primary, 0.1) }}>
                <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, color: primary }}>
                  {typeof count === 'number' ? count.toLocaleString('en-US') : count}
                  {countLabel ? ` ${countLabel}` : ''}
                </Typography>
              </Box>
            ) : null}
          </Stack>

          <Typography sx={{ fontSize: '0.95rem', fontWeight: 800, color: dashboardNeutral.textPrimary }}>{title}</Typography>

          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mt: 'auto', color: primary }}>
            <Typography sx={{ fontSize: '0.75rem', fontWeight: 700 }}>{ctaText}</Typography>
            <ArrowBackIcon sx={{ fontSize: '0.9rem' }} />
          </Stack>
        </Stack>
      </CardActionArea>
    </Card>
  );
}

PriorityModuleCard.propTypes = {
  title: PropTypes.string.isRequired,
  iconKey: PropTypes.string,
  count: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  countLabel: PropTypes.string,
  onClick: PropTypes.func.isRequired,
  highlight: PropTypes.bool,
  primaryColor: PropTypes.string,
  ctaText: PropTypes.string
};
