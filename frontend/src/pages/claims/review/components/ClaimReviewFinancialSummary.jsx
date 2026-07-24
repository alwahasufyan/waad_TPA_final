import { Box, Grid, Paper, Typography } from '@mui/material';

import { formatCurrency } from 'utils/formatters';

/**
 * Compact, full-width KPI strip for the reviewer workspace (replaces the
 * previous side-column cost-summary card). Kept as a horizontal row of small
 * stat boxes so the services table below can take the page's full width —
 * matching the reference layout's KPI ribbon directly above its table.
 *
 * Labels follow CLAIMS-AMOUNT-LABEL-1 exactly — never a bare "المعتمد".
 */
const KpiBox = ({ label, value, sub, tone = 'default', emphasis }) => {
  const toneColor = {
    default: 'text.primary',
    success: 'success.main',
    warning: 'warning.main',
    error: 'error.main'
  }[tone];

  return (
    <Paper
      variant="outlined"
      sx={{
        p: '0.375rem 0.625rem',
        borderRadius: '0.5rem',
        height: '100%',
        borderColor: emphasis ? 'primary.main' : 'divider',
        bgcolor: emphasis ? 'primary.lighter' : 'background.paper'
      }}
    >
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontSize: '0.6875rem', lineHeight: 1.2 }}>
        {label}
      </Typography>
      <Typography variant="body2" fontWeight={800} color={toneColor} sx={{ lineHeight: 1.3 }}>
        {value}
      </Typography>
      {sub && (
        <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.625rem' }}>
          {sub}
        </Typography>
      )}
    </Paper>
  );
};

const ClaimReviewFinancialSummary = ({
  normalizedClaim,
  selectedApprovedAmount,
  selectedServicesCount,
  rejectedCount = 0,
  clarifyCount = 0,
  memberCoverage
}) => {
  const hasFinalDecision = Number(normalizedClaim?.approvedAmount) > 0;
  const approvedValue = hasFinalDecision ? normalizedClaim?.approvedAmount : selectedApprovedAmount;
  const copay = Number(normalizedClaim?.copayAmount) || 0;
  // CLAIM-REVIEW-FOLLOWUP-1: `approvedValue` (either the backend's persisted
  // approvedAmount, or the client-side preview sum) is already the company's
  // net share — it EXCLUDES the member's copay by definition (they are two
  // separate payment streams, not a chain). Subtracting copay from it a
  // second time (the previous "net = approvedValue - copay" formula) produced
  // a number with no real financial meaning. What the provider actually
  // collects in total is the sum of both streams.
  const totalDueToProvider = Math.max(0, Number(approvedValue || 0) + copay);

  return (
    <Box sx={{ mb: '0.75rem' }}>
      <Grid container spacing={1}>
        <Grid size={{ xs: 6, sm: 3, md: 1.5 }}>
          <KpiBox label="المطلوب" value={formatCurrency(normalizedClaim?.claimedAmount ?? 0)} />
        </Grid>
        <Grid size={{ xs: 6, sm: 3, md: 1.5 }}>
          <KpiBox
            label={hasFinalDecision ? 'معتمد' : 'محدد للاعتماد (غير محفوظ)'}
            value={formatCurrency(approvedValue || 0)}
            sub={`${selectedServicesCount || 0} خدمة`}
            tone="success"
          />
        </Grid>
        <Grid size={{ xs: 6, sm: 3, md: 1.5 }}>
          <KpiBox label="حصة العضو" value={formatCurrency(copay)} tone="warning" />
        </Grid>
        <Grid size={{ xs: 6, sm: 3, md: 1.5 }}>
          <KpiBox label="مرفوض / استيضاح" value={`${rejectedCount} / ${clarifyCount}`} />
        </Grid>
        <Grid size={{ xs: 6, sm: 4, md: 1.5 }}>
          <KpiBox label="إجمالي مستحق المقدم" value={formatCurrency(totalDueToProvider)} tone="success" emphasis />
        </Grid>
        {/* CLAIM-REVIEW-FOLLOWUP-1: member coverage/benefit context — real
            data from GET /members/{id}/remaining-limit, folded into the same
            single-strip layout instead of a separate row. */}
        {memberCoverage && (
          <>
            <Grid size={{ xs: 4, sm: 4, md: 1.5 }}>
              <KpiBox label="الحد السنوي" value={formatCurrency(memberCoverage.annualLimit ?? 0)} />
            </Grid>
            <Grid size={{ xs: 4, sm: 4, md: 1.5 }}>
              <KpiBox label="المستخدم" value={formatCurrency(memberCoverage.usedAmount ?? 0)} tone="warning" />
            </Grid>
            <Grid size={{ xs: 4, sm: 4, md: 1.5 }}>
              <KpiBox label="الحد المتبقي" value={formatCurrency(memberCoverage.remainingLimit ?? 0)} tone="success" />
            </Grid>
          </>
        )}
      </Grid>
    </Box>
  );
};

export default ClaimReviewFinancialSummary;
