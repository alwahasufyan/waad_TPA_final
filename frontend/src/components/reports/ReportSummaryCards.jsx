import PropTypes from 'prop-types';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Skeleton from '@mui/material/Skeleton';
import { alpha } from '@mui/material/styles';

/**
 * ReportSummaryCards — concise, read-only KPI/totals row for a report.
 * Values are supplied by the caller from the backend summary (never computed
 * client-side for financial figures).
 *
 * items: [{ key, label, value, hint?, color?, icon?, currency? }]
 */
export default function ReportSummaryCards({ items = [], loading = false, columns = { xs: 6, sm: 4, md: 3 } }) {
  if (!loading && (!items || items.length === 0)) return null;

  const display = loading ? Array.from({ length: 4 }).map((_, i) => ({ key: `sk-${i}` })) : items;

  return (
    <Grid container spacing={1.5}>
      {display.map((item) => {
        const color = item.color || 'primary.main';
        const Icon = item.icon;
        return (
          <Grid key={item.key} size={columns}>
            <Paper
              variant="outlined"
              sx={{
                p: 1.5,
                borderRadius: 2,
                height: '100%',
                borderTop: '3px solid',
                borderTopColor: loading ? 'divider' : color
              }}
            >
              <Stack direction="row" alignItems="flex-start" justifyContent="space-between" spacing={1}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block' }} noWrap>
                    {loading ? <Skeleton width={80} /> : item.label}
                  </Typography>
                  <Typography sx={{ fontSize: '1.4rem', fontWeight: 800, lineHeight: 1.2 }}>
                    {loading ? <Skeleton width={64} /> : item.value}
                  </Typography>
                  {!loading && item.hint && (
                    <Typography variant="caption" sx={{ color: 'text.disabled' }} noWrap>
                      {item.hint}
                    </Typography>
                  )}
                </Box>
                {!loading && Icon && (
                  <Box
                    sx={{
                      flexShrink: 0,
                      width: 34,
                      height: 34,
                      borderRadius: 1.5,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      bgcolor: (theme) => alpha(theme.palette.primary.main, 0.08),
                      color
                    }}
                  >
                    <Icon sx={{ fontSize: '1.2rem' }} />
                  </Box>
                )}
              </Stack>
            </Paper>
          </Grid>
        );
      })}
    </Grid>
  );
}

ReportSummaryCards.propTypes = {
  items: PropTypes.array,
  loading: PropTypes.bool,
  columns: PropTypes.object
};
