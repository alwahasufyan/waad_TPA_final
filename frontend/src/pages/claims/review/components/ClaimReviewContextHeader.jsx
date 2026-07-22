import { Alert, Box, Button, Card, CardContent, Grid, Stack, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import {
  Person as PersonIcon,
  Business as EmployerIcon,
  CreditCard as PolicyIcon,
  Assessment as DiagnosisIcon,
  NavigateBefore as PreviousIcon,
  NavigateNext as NextIcon
} from '@mui/icons-material';

import SectionCard from './SectionCard';
import InfoRow from './InfoRow';
import { formatDate } from 'utils/formatters';

const hasValue = (value) => value !== null && value !== undefined && `${value}`.trim() !== '';

/**
 * Read-only claim identity, navigation, and context (member / policy / diagnosis).
 * No provider-entry actions live here — this is purely display + prev/next navigation.
 */
const ClaimReviewContextHeader = ({ id, normalizedClaim, navigate, draftBanner, reviewLock }) => {
  if (!normalizedClaim) return null;

  return (
    <>
      <Card
        sx={{
          borderRadius: '0.25rem',
          border: 1,
          borderColor: 'divider',
          bgcolor: (theme) => alpha(theme.palette.info.main, 0.08),
          boxShadow: 1
        }}
      >
        <CardContent sx={{ py: '0.75rem', '&:last-child': { pb: '0.75rem' } }}>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            justifyContent="space-between"
            spacing={1}
            alignItems={{ xs: 'flex-start', md: 'center' }}
          >
            <Box>
              <Typography variant="subtitle1" fontWeight={700}>
                {normalizedClaim.memberName || 'عضو غير معروف'}
              </Typography>
              <Typography variant="body2" color="text.secondary" fontWeight={600}>
                رقم المطالبة: {normalizedClaim.claimNumber}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1}>
              <Button
                size="small"
                variant="outlined"
                startIcon={<PreviousIcon />}
                onClick={() => navigate(`/claims/${Math.max(Number(id) - 1, 1)}/medical-review`)}
                disabled={!Number.isFinite(Number(id)) || Number(id) <= 1}
              >
                المطالبة السابقة
              </Button>
              <Button
                size="small"
                variant="outlined"
                endIcon={<NextIcon />}
                onClick={() => navigate(`/claims/${Number(id) + 1}/medical-review`)}
                disabled={!Number.isFinite(Number(id))}
              >
                المطالبة التالية
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {draftBanner}

      {reviewLock.locked && reviewLock.message && (
        <Alert severity={reviewLock.severity} sx={{ py: 1 }}>
          {reviewLock.message}
        </Alert>
      )}

      <SectionCard title="معلومات المنتفع" icon={PersonIcon}>
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, md: 6 }}>
            <InfoRow label="الاسم الكامل" value={normalizedClaim.memberName} icon={PersonIcon} />
          </Grid>
          {hasValue(normalizedClaim.memberCivilId) && (
            <Grid size={{ xs: 12, md: 6 }}>
              <InfoRow label="الرقم المدني" value={normalizedClaim.memberCivilId} />
            </Grid>
          )}
          {hasValue(normalizedClaim.memberCardNumber) && (
            <Grid size={{ xs: 12, md: 6 }}>
              <InfoRow label="رقم البطاقة" value={normalizedClaim.memberCardNumber} />
            </Grid>
          )}
          {hasValue(normalizedClaim.memberPhone) && (
            <Grid size={{ xs: 12, md: 6 }}>
              <InfoRow label="رقم الجوال" value={normalizedClaim.memberPhone} />
            </Grid>
          )}
        </Grid>
      </SectionCard>

      <SectionCard title="بيانات التأمين" icon={PolicyIcon}>
        <Grid container spacing={1.5}>
          {hasValue(normalizedClaim.employerName) && (
            <Grid size={{ xs: 12, md: 6 }}>
              <InfoRow label="جهة العمل" value={normalizedClaim.employerName} icon={EmployerIcon} />
            </Grid>
          )}
          {hasValue(normalizedClaim.policyNumber) && (
            <Grid size={{ xs: 12, md: 6 }}>
              <InfoRow label="رقم البوليصة" value={normalizedClaim.policyNumber} />
            </Grid>
          )}
          {hasValue(normalizedClaim.coverageType) && (
            <Grid size={{ xs: 12, md: 6 }}>
              <InfoRow label="نوع التغطية" value={normalizedClaim.coverageType} />
            </Grid>
          )}
          <Grid size={{ xs: 12, md: 6 }}>
            <InfoRow label="تاريخ المطالبة" value={formatDate(normalizedClaim.claimDate)} />
          </Grid>
          {hasValue(normalizedClaim.preApprovalReferenceNumber) && (
            <Grid size={{ xs: 12, md: 6 }}>
              <InfoRow label="رقم الموافقة المسبقة" value={normalizedClaim.preApprovalReferenceNumber} />
            </Grid>
          )}
        </Grid>
      </SectionCard>

      <SectionCard title="التشخيص" icon={DiagnosisIcon}>
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, md: hasValue(normalizedClaim.icdCode) ? 6 : 12 }}>
            <InfoRow label="التشخيص الأساسي" value={normalizedClaim.primaryDiagnosis} />
          </Grid>
          {hasValue(normalizedClaim.icdCode) && (
            <Grid size={{ xs: 12, md: 6 }}>
              <InfoRow label="ICD Code" value={normalizedClaim.icdCode} />
            </Grid>
          )}
          {normalizedClaim.secondaryDiagnosis && (
            <Grid size={12}>
              <InfoRow label="تشخيص ثانوي" value={normalizedClaim.secondaryDiagnosis} />
            </Grid>
          )}
        </Grid>
      </SectionCard>
    </>
  );
};

export default ClaimReviewContextHeader;
