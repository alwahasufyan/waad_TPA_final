import { Box, Card, CardContent, Typography, Stack, Chip, CircularProgress, alpha } from '@mui/material';
import { Lock as LockIcon } from '@mui/icons-material';
import { formatCurrency } from 'utils/currency-formatter';
import { LABELS } from '../constants';

// ══════════════════════════════════════════════════════════════════════════════
// STYLED COMPONENTS / SECTION COMPONENTS
// Moved verbatim from the original ProviderClaimsSubmission.jsx (Stage 3A extraction).
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Section Header Component
 */
export const SectionHeader = ({ icon: Icon, title, subtitle, color = 'primary', action }) => (
  <Box sx={{ mb: '1.25rem' }}>
    <Stack direction="row" spacing={1.5} alignItems="center" justifyContent="space-between">
      <Stack direction="row" spacing={1.5} alignItems="center">
        <Box
          sx={{
            width: '2.5rem',
            height: '2.5rem',
            borderRadius: '0.25rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: (theme) => alpha(theme.palette[color].main, 0.1),
            color: `${color}.main`
          }}
        >
          <Icon />
        </Box>
        <Box>
          <Typography variant="h6" fontWeight={600} color="text.primary">
            {title}
          </Typography>
          {subtitle && (
            <Typography variant="caption" color="text.secondary">
              {subtitle}
            </Typography>
          )}
        </Box>
      </Stack>
      {action}
    </Stack>
  </Box>
);

/**
 * Read-Only Info Field
 */
export const ReadOnlyField = ({ icon: Icon, label, value, highlight = false, dense = false }) => (
  <Box sx={{ mb: dense ? '0.4rem' : '1.0rem' }}>
    <Typography
      variant="caption"
      color="text.secondary"
      sx={{
        display: 'block',
        mb: dense ? 0.25 : 0.5,
        fontWeight: 500,
        textTransform: 'uppercase',
        letterSpacing: 0.5,
        fontSize: dense ? '0.65rem' : '0.7rem'
      }}
    >
      {label}
    </Typography>
    <Stack direction="row" spacing={1} alignItems="center">
      {Icon && <Icon fontSize="small" color="action" sx={{ opacity: 0.7, fontSize: dense ? '1rem' : undefined }} />}
      <Typography
        variant={dense ? 'body2' : 'body1'}
        fontWeight={highlight ? 600 : 400}
        color={highlight ? 'primary.main' : 'text.primary'}
      >
        {value || '—'}
      </Typography>
    </Stack>
  </Box>
);

/**
 * Info Card (Read-Only)
 */
export const InfoCard = ({ children, bgcolor = 'grey.50' }) => (
  <Card
    variant="outlined"
    sx={{
      height: '100%',
      bgcolor,
      borderColor: 'divider',
      borderRadius: '0.25rem',
      transition: 'box-shadow 0.2s',
      '&:hover': {
        boxShadow: 1
      }
    }}
  >
    <CardContent sx={{ p: '1.5rem' }}>{children}</CardContent>
  </Card>
);

/**
 * Form Section Card
 */
export const FormSection = ({ children, highlighted = false }) => (
  <Card
    variant="outlined"
    sx={{
      borderRadius: '0.25rem',
      borderColor: highlighted ? 'primary.main' : 'divider',
      borderWidth: highlighted ? 2 : 1,
      bgcolor: highlighted ? (theme) => alpha(theme.palette.primary.main, 0.02) : 'background.paper'
    }}
  >
    <CardContent sx={{ p: '1.5rem' }}>{children}</CardContent>
  </Card>
);

/**
 * Contract Price Chip
 */
export const ContractPriceChip = ({ loading, price, hasContract, error }) => {
  if (loading) return <CircularProgress size={16} />;
  if (error) return <Chip label={error} color="error" size="small" />;
  if (!hasContract) return <Chip label={LABELS.noContract} color="warning" size="small" />;
  return <Chip icon={<LockIcon fontSize="small" />} label={formatCurrency(price)} color="success" size="small" sx={{ fontWeight: 600 }} />;
};
