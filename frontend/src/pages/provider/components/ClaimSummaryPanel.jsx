import { Stack, Box, Typography, Paper, Chip, Alert, LinearProgress, Divider, alpha } from '@mui/material';
import { AccountBalance as LimitIcon } from '@mui/icons-material';
import { formatCurrency } from 'utils/currency-formatter';
import { InfoCard, SectionHeader } from './ClaimSectionPrimitives';
import { LABELS } from '../constants';

/**
 * Coverage/limit summary + readiness checklist. Originally the Stage 3B right
 * rail (own column); per owner feedback it now sits stacked under
 * MemberContextPanel in the same side column instead of a separate one, so
 * `compact` shrinks it to a 2×2 stat grid instead of four full-width blocks.
 */
export function ClaimSummaryPanel({ memberLimit, totalClaimAmount, hasVisitAndDiagnosis, hasServicesReady, compact = false }) {
  const stat = (label, value, color) =>
    compact ? (
      <Box sx={{ p: 0.75, borderRadius: '0.25rem', bgcolor: (theme) => alpha(theme.palette[color].main, 0.08) }}>
        <Typography variant="caption" color="text.secondary" display="block" sx={{ fontSize: '0.65rem' }}>
          {label}
        </Typography>
        <Typography variant="caption" fontWeight={700} color={`${color}.main`} sx={{ fontSize: '0.75rem' }}>
          {formatCurrency(value || 0)}
        </Typography>
      </Box>
    ) : (
      <Box sx={{ p: 1, borderRadius: '0.375rem', bgcolor: (theme) => alpha(theme.palette[color].main, 0.08) }}>
        <Typography variant="caption" color="text.secondary" display="block">
          {label}
        </Typography>
        <Typography variant="body2" fontWeight={700} color={`${color}.main`}>
          {formatCurrency(value || 0)}
        </Typography>
      </Box>
    );

  return (
    <Stack spacing={compact ? 1 : 1.5}>
      <InfoCard bgcolor={(theme) => alpha(theme.palette.success.main, 0.05)}>
        {!compact && (
          <>
            <SectionHeader icon={LimitIcon} title={LABELS.coverageInfo} subtitle="تغطية المؤمن عليه" color="success" />
            <Divider sx={{ mb: '0.75rem', mt: 1 }} />
          </>
        )}
        {compact && (
          <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mb: '0.5rem' }}>
            <LimitIcon fontSize="small" color="success" />
            <Typography variant="caption" fontWeight={700}>
              {LABELS.coverageInfo}
            </Typography>
          </Stack>
        )}
        {memberLimit ? (
          <Stack spacing={compact ? 0.5 : 1}>
            {compact ? (
              <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 0.5 }}>
                {stat(LABELS.annualLimit, memberLimit.annualLimit, 'warning')}
                {stat(LABELS.usedAmount, memberLimit.usedAmount, 'error')}
                {stat(LABELS.remainingLimit, memberLimit.remainingLimit, 'success')}
                {stat(LABELS.totalClaimAmount, totalClaimAmount, 'primary')}
              </Box>
            ) : (
              <>
                {stat(LABELS.annualLimit, memberLimit.annualLimit, 'warning')}
                {stat(LABELS.usedAmount, memberLimit.usedAmount, 'error')}
                {stat(LABELS.remainingLimit, memberLimit.remainingLimit, 'success')}
                {stat(LABELS.totalClaimAmount, totalClaimAmount, 'primary')}
              </>
            )}
            <LinearProgress
              variant="determinate"
              value={memberLimit.usagePercentage || 0}
              color={memberLimit.usagePercentage >= 80 ? 'error' : 'success'}
              sx={{ mt: 0.5, height: '0.375rem', borderRadius: 1 }}
            />
          </Stack>
        ) : (
          <Alert severity="info" sx={{ borderRadius: '0.25rem' }}>
            لا تتوفر بيانات التغطية حالياً
          </Alert>
        )}
      </InfoCard>

      <Paper variant="outlined" sx={{ p: compact ? '0.5rem' : '0.75rem', borderRadius: '0.25rem', bgcolor: 'common.white' }}>
        {!compact && (
          <Typography variant="body2" fontWeight={700} sx={{ mb: 1 }}>
            حالة الجاهزية
          </Typography>
        )}
        <Stack spacing={0.5}>
          <Chip
            size="small"
            label={hasVisitAndDiagnosis ? '✓ بيانات الزيارة مكتملة' : '• أكمل التشخيص'}
            color={hasVisitAndDiagnosis ? 'success' : 'default'}
          />
          <Chip
            size="small"
            label={hasServicesReady ? '✓ الخدمات مكتملة' : '• أضف الخدمات المطلوبة'}
            color={hasServicesReady ? 'success' : 'default'}
          />
          <Chip size="small" label="✓ المرفقات اختيارية" color="success" />
        </Stack>
      </Paper>
    </Stack>
  );
}
