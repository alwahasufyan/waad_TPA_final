import PropTypes from 'prop-types';
import { Card, CardActionArea, CardContent, Box, Stack, Typography, Skeleton, alpha } from '@mui/material';
import { dashboardStatus, dashboardNeutral, dashboardShape } from 'themes/dashboardTokens';

/**
 * DashboardKpiCard — calm, enterprise-medical KPI card.
 * Status colour appears only in the icon chip + top accent border, never as a
 * full-card gradient. Number/label are high-contrast. Optional onClick.
 *
 * All values are supplied by the caller from the real dashboard summary API —
 * this component renders, it never fabricates data.
 */
export default function DashboardKpiCard({ title, value, subtitle, icon: Icon, colorKey = 'info', loading = false, onClick }) {
  const accent = dashboardStatus[colorKey] || dashboardStatus.info;

  const body = (
    <CardContent sx={{ p: '1rem', '&:last-child': { pb: '1rem' } }}>
      <Stack direction="row" alignItems="flex-start" justifyContent="space-between" spacing={1}>
        <Stack spacing={0.5} sx={{ minWidth: 0 }}>
          <Typography variant="caption" sx={{ color: dashboardNeutral.textMuted, fontWeight: 700, fontSize: '0.75rem' }} noWrap>
            {title}
          </Typography>
          {loading ? (
            <Skeleton variant="text" width={64} height={34} />
          ) : (
            <Typography
              sx={{
                fontSize: '1.6rem',
                lineHeight: 1.15,
                fontWeight: 800,
                color: dashboardNeutral.textPrimary,
                fontFamily: "'Roboto', sans-serif"
              }}
            >
              {typeof value === 'number' ? value.toLocaleString('en-US') : (value ?? 0)}
            </Typography>
          )}
          {subtitle ? (
            <Typography variant="caption" sx={{ color: dashboardNeutral.textMuted, fontSize: '0.72rem' }} noWrap>
              {subtitle}
            </Typography>
          ) : null}
        </Stack>
        {Icon ? (
          <Box
            sx={{
              flexShrink: 0,
              width: 40,
              height: 40,
              borderRadius: `${dashboardShape.radiusSm}px`,
              bgcolor: alpha(accent, 0.1),
              color: accent,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <Icon sx={{ fontSize: '1.35rem' }} />
          </Box>
        ) : null}
      </Stack>
    </CardContent>
  );

  return (
    <Card
      elevation={0}
      sx={{
        height: '100%',
        borderRadius: `${dashboardShape.radius}px`,
        bgcolor: dashboardNeutral.surface,
        border: '1px solid',
        borderColor: dashboardNeutral.border,
        boxShadow: dashboardShape.shadowSoft,
        borderTop: '3px solid',
        borderTopColor: accent,
        overflow: 'hidden',
        transition: `box-shadow ${dashboardShape.transition}, transform ${dashboardShape.transition}`,
        '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
        '&:hover': onClick
          ? { boxShadow: dashboardShape.shadowHover, transform: 'translateY(-2px)' }
          : { boxShadow: dashboardShape.shadowHover }
      }}
    >
      {onClick ? (
        <CardActionArea onClick={onClick} sx={{ height: '100%' }}>
          {body}
        </CardActionArea>
      ) : (
        body
      )}
    </Card>
  );
}

DashboardKpiCard.propTypes = {
  title: PropTypes.string.isRequired,
  value: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  subtitle: PropTypes.string,
  icon: PropTypes.elementType,
  colorKey: PropTypes.oneOf(['success', 'warning', 'error', 'info', 'pending']),
  loading: PropTypes.bool,
  onClick: PropTypes.func
};
