import { Box, Button, Chip, Paper, Stack, Typography } from '@mui/material';
import { CheckCircle as ApproveIcon, Cancel as RejectIcon, HelpOutline as ClarifyIcon } from '@mui/icons-material';

import { formatCurrency } from 'utils/formatters';

/**
 * Sticky bottom action bar: running (temporary, unsaved) selection total plus
 * the three reviewer decision actions. Calls straight through to the existing,
 * unchanged backend endpoints (POST /approve, /reject, /return-for-info) via
 * the handlers passed down from ClaimReviewWorkspace — no new endpoints, no
 * calculation change.
 */
const ClaimReviewActionBar = ({
  selectedApprovedAmount,
  reviewLock,
  submitting,
  selectedServicesCount,
  onApprove,
  onReject,
  onRequestInfo
}) => {
  return (
    <Paper
      elevation={6}
      sx={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        borderTop: 1,
        borderColor: 'divider',
        bgcolor: 'background.paper',
        zIndex: (theme) => theme.zIndex.drawer
      }}
    >
      <Box sx={{ maxWidth: '87.5rem', mx: 'auto', px: '1.0rem', py: '0.625rem' }}>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          alignItems={{ xs: 'stretch', md: 'center' }}
          justifyContent="space-between"
          spacing={1.5}
        >
          <Typography variant="body2" fontWeight={700}>
            إجمالي الخدمات المختارة للاعتماد: {formatCurrency(selectedApprovedAmount || 0)}
          </Typography>
          {reviewLock.locked ? (
            <Chip
              color={reviewLock.severity === 'warning' ? 'warning' : 'success'}
              label={reviewLock.message || 'لا يمكن تنفيذ قرار جديد على هذه المطالبة'}
              variant="outlined"
            />
          ) : (
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <Button
                variant="contained"
                color="success"
                startIcon={<ApproveIcon />}
                onClick={onApprove}
                disabled={submitting || selectedServicesCount <= 0}
                sx={{ boxShadow: 2 }}
              >
                موافقة
              </Button>
              <Button variant="contained" color="error" startIcon={<RejectIcon />} onClick={onReject} disabled={submitting} sx={{ boxShadow: 2 }}>
                رفض
              </Button>
              <Button
                variant="contained"
                color="info"
                startIcon={<ClarifyIcon />}
                onClick={onRequestInfo}
                disabled={submitting}
                sx={{ boxShadow: 2 }}
              >
                طلب معلومات
              </Button>
            </Stack>
          )}
        </Stack>
      </Box>
    </Paper>
  );
};

export default ClaimReviewActionBar;
