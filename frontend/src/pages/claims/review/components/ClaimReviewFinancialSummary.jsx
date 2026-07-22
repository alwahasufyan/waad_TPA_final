import { Box, Divider, Stack, Typography } from '@mui/material';
import { AttachMoney as CostIcon } from '@mui/icons-material';

import SectionCard from './SectionCard';
import { formatCurrency } from 'utils/formatters';

/**
 * Financial summary for the reviewer workspace.
 *
 * Labels follow CLAIMS-AMOUNT-LABEL-1 exactly — never a bare "المعتمد".
 * The claim's persisted `approvedAmount` (post-discount, final) is shown once a
 * decision has actually been recorded; before that, the reviewer's in-progress
 * line selection total is shown separately and marked as not yet saved (see
 * ClaimReviewServiceLinesPanel — line-level decisions are not persisted until
 * CLAIM-REVIEW-SPLIT-2C).
 */
const ClaimReviewFinancialSummary = ({ normalizedClaim, selectedApprovedAmount, selectedServicesCount }) => {
  const hasFinalDecision = Number(normalizedClaim?.approvedAmount) > 0;

  return (
    <SectionCard title="ملخص التكاليف" icon={CostIcon}>
      <Stack spacing={1}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
          <Typography variant="body2">إجمالي المطالبة</Typography>
          <Typography variant="body2" fontWeight={600}>
            {formatCurrency(normalizedClaim?.claimedAmount ?? 0)}
          </Typography>
        </Box>
        <Divider />

        {hasFinalDecision ? (
          <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
            <Typography variant="body2">المعتمد النهائي</Typography>
            <Typography variant="body2" fontWeight={600} color="success.main">
              {formatCurrency(normalizedClaim?.approvedAmount ?? 0)}
            </Typography>
          </Box>
        ) : (
          <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="body2">إجمالي الخدمات المختارة للاعتماد (مؤقت، غير محفوظ بعد)</Typography>
              <Typography variant="body2" fontWeight={600} color="success.main">
                {formatCurrency(selectedApprovedAmount || 0)}
              </Typography>
            </Box>
            <Typography variant="caption" color="text.secondary">
              هذا الإجمالي مبني على اختيارات المراجع الحالية على مستوى الخدمة، وهي محلية فقط ولم يتم اعتمادها بعد.
            </Typography>
          </Box>
        )}

        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
          <Typography variant="caption" color="text.secondary">الخدمات المحددة للموافقة</Typography>
          <Typography variant="caption" color="text.secondary">{selectedServicesCount || 0} خدمة</Typography>
        </Box>

        {Number(normalizedClaim?.copayAmount) > 0 && (
          <>
            <Divider />
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="body2">حصة العضو</Typography>
              <Typography variant="body2" fontWeight={600} color="warning.main">
                {formatCurrency(normalizedClaim?.copayAmount ?? 0)}
              </Typography>
            </Box>
          </>
        )}
      </Stack>
    </SectionCard>
  );
};

export default ClaimReviewFinancialSummary;
