import { Alert, Box, Button, Card, CardContent, Chip, Divider, Grid, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import {
  Person as PersonIcon,
  Business as EmployerIcon,
  LocalHospital as ProviderIcon,
  CalendarMonth as DateIcon,
  Assessment as DiagnosisIcon,
  NavigateBefore as PreviousIcon,
  NavigateNext as NextIcon,
  ArrowForward as BackIcon,
  Print as PrintIcon
} from '@mui/icons-material';

import SectionCard from './SectionCard';
import InfoRow from './InfoRow';
import { formatDate } from 'utils/formatters';

const hasValue = (value) => value !== null && value !== undefined && `${value}`.trim() !== '';

const STATUS_CHIP_CONFIG = {
  DRAFT: { label: 'مسودة', color: 'default' },
  SUBMITTED: { label: 'مقدمة', color: 'info' },
  UNDER_REVIEW: { label: 'قيد المراجعة', color: 'info' },
  NEEDS_CORRECTION: { label: 'معلقة للمراجعة', color: 'warning' },
  APPROVAL_IN_PROGRESS: { label: 'جاري المعالجة', color: 'info' },
  APPROVED: { label: 'معتمدة', color: 'success' },
  REJECTED: { label: 'مرفوضة', color: 'error' },
  BATCHED: { label: 'في دفعة تسوية', color: 'success' },
  SETTLED: { label: 'تمت التسوية', color: 'success' }
};

// Compact "at a glance" item — same visual role as the Lovable reference's
// ContextItem, rebuilt with MUI. Only real claim fields are ever passed in.
const ContextItem = ({ icon: Icon, label, value, hint }) => (
  <Stack direction="row" spacing={0.75} alignItems="center">
    <Icon sx={{ fontSize: '1.0rem', color: 'primary.main', opacity: 0.7 }} />
    <Typography variant="caption" color="text.secondary">
      {label}:
    </Typography>
    <Typography variant="caption" fontWeight={700}>
      {hasValue(value) ? value : '—'}
    </Typography>
    {hasValue(hint) && (
      <Typography variant="caption" color="text.disabled">
        ({hint})
      </Typography>
    )}
  </Stack>
);

/**
 * Read-only claim identity, navigation, and context (member / employer /
 * provider / diagnosis). No provider-entry actions live here.
 *
 * Prev/next navigate only within the exact claim list the reviewer was
 * browsing (passed down as `hasPrev`/`hasNext`/`onNavigatePrev`/
 * `onNavigateNext` from the workspace, itself sourced from router state set
 * by the inbox) — never a naive id±1 guess, which previously 400/409'd on
 * arbitrary/unauthorized claim ids since claim ids are not sequential.
 *
 * CLAIM-REVIEW-WORKSPACE-LOVABLE-POLISH-1: rebuilt as a single compact
 * context strip (matching the attached reference) instead of three stacked
 * SectionCards. Every field previously shown is still shown — the less
 * frequently needed ones (civil id, phone, policy number, coverage type,
 * secondary diagnosis) moved into a collapsed-by-default "تفاصيل إضافية"
 * section rather than being removed. The member card number is shown
 * directly beside the member name in the main context strip (not buried in
 * the collapsed section) per explicit feedback that it matters at a glance.
 * The "رقم الموافقة المسبقة" (pre-approval reference) field was removed
 * entirely per explicit feedback — the claim's own official claim number
 * (shown prominently above) is the only reference a reviewer needs here.
 */
const ClaimReviewContextHeader = ({
  normalizedClaim,
  navigate,
  draftBanner,
  reviewLock,
  onNavigatePrev,
  onNavigateNext,
  hasPrev,
  hasNext
}) => {
  if (!normalizedClaim) return null;

  const statusConfig = STATUS_CHIP_CONFIG[normalizedClaim.status] || { label: normalizedClaim.status || '—', color: 'default' };

  const memberLabel = hasValue(normalizedClaim.memberCardNumber)
    ? `${normalizedClaim.memberName || '—'} — بطاقة: ${normalizedClaim.memberCardNumber}`
    : normalizedClaim.memberName;

  const hasSecondaryDetails =
    hasValue(normalizedClaim.memberCivilId) ||
    hasValue(normalizedClaim.memberPhone) ||
    hasValue(normalizedClaim.policyNumber) ||
    hasValue(normalizedClaim.coverageType) ||
    hasValue(normalizedClaim.secondaryDiagnosis);

  return (
    <>
      <Card sx={{ borderRadius: '0.5rem', border: 1, borderColor: 'divider', boxShadow: 1 }}>
        <CardContent sx={{ py: '0.625rem', '&:last-child': { pb: '0.625rem' } }}>
          {/* Top row: back link, claim identity, prev/next/print */}
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            spacing={1}
          >
            <Stack direction="row" spacing={1} alignItems="center">
              <Button size="small" startIcon={<BackIcon />} onClick={() => navigate('/claims/review')} sx={{ color: 'text.secondary' }}>
                صندوق المراجعة
              </Button>
              <Divider orientation="vertical" flexItem />
              <Box>
                <Typography variant="subtitle1" fontWeight={700}>
                  مطالبة {normalizedClaim.claimNumber}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  مراجعة طبية{hasValue(normalizedClaim.providerName) ? ` · ${normalizedClaim.providerName}` : ''}
                </Typography>
              </Box>
            </Stack>
            <Stack direction="row" spacing={1} alignItems="center">
              <Tooltip title={hasPrev ? '' : 'لا توجد مطالبة سابقة في هذه القائمة'}>
                <span>
                  <Button size="small" variant="outlined" startIcon={<PreviousIcon />} onClick={onNavigatePrev} disabled={!hasPrev}>
                    السابقة
                  </Button>
                </span>
              </Tooltip>
              <Tooltip title={hasNext ? '' : 'لا توجد مطالبة تالية في هذه القائمة'}>
                <span>
                  <Button size="small" variant="outlined" endIcon={<NextIcon />} onClick={onNavigateNext} disabled={!hasNext}>
                    التالية
                  </Button>
                </span>
              </Tooltip>
              <Tooltip title="طباعة">
                <IconButton size="small" onClick={() => window.print()}>
                  <PrintIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Chip size="small" color={statusConfig.color} label={statusConfig.label} sx={{ fontWeight: 700 }} />
            </Stack>
          </Stack>

          <Divider sx={{ my: '0.625rem' }} />

          {/* Context strip */}
          <Stack direction="row" flexWrap="wrap" columnGap={3} rowGap={1}>
            <ContextItem icon={PersonIcon} label="المنتفع" value={memberLabel} />
            <ContextItem icon={EmployerIcon} label="جهة العمل" value={normalizedClaim.employerName} />
            <ContextItem icon={ProviderIcon} label="مقدم الخدمة" value={normalizedClaim.providerName} />
            <ContextItem icon={DateIcon} label="تاريخ المطالبة" value={formatDate(normalizedClaim.claimDate)} />
            <ContextItem
              icon={DiagnosisIcon}
              label="التشخيص"
              value={normalizedClaim.primaryDiagnosis}
              hint={normalizedClaim.icdCode}
            />
          </Stack>
        </CardContent>
      </Card>

      {draftBanner}

      {reviewLock.locked && reviewLock.message && (
        <Alert severity={reviewLock.severity} sx={{ py: 1 }}>
          {reviewLock.message}
        </Alert>
      )}

      {hasSecondaryDetails && (
        <SectionCard title="تفاصيل إضافية عن المنتفع والتغطية" icon={PersonIcon} defaultExpanded={false}>
          <Grid container spacing={1.5}>
            {hasValue(normalizedClaim.memberCivilId) && (
              <Grid size={{ xs: 12, md: 4 }}>
                <InfoRow label="الرقم المدني" value={normalizedClaim.memberCivilId} />
              </Grid>
            )}
            {hasValue(normalizedClaim.memberPhone) && (
              <Grid size={{ xs: 12, md: 4 }}>
                <InfoRow label="رقم الجوال" value={normalizedClaim.memberPhone} />
              </Grid>
            )}
            {hasValue(normalizedClaim.policyNumber) && (
              <Grid size={{ xs: 12, md: 4 }}>
                <InfoRow label="رقم البوليصة" value={normalizedClaim.policyNumber} />
              </Grid>
            )}
            {hasValue(normalizedClaim.coverageType) && (
              <Grid size={{ xs: 12, md: 4 }}>
                <InfoRow label="نوع التغطية" value={normalizedClaim.coverageType} />
              </Grid>
            )}
            {hasValue(normalizedClaim.secondaryDiagnosis) && (
              <Grid size={12}>
                <InfoRow label="تشخيص ثانوي" value={normalizedClaim.secondaryDiagnosis} />
              </Grid>
            )}
          </Grid>
        </SectionCard>
      )}
    </>
  );
};

export default ClaimReviewContextHeader;
