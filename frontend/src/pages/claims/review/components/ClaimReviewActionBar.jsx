import { Box, Button, Chip, Paper, Stack, Tooltip, Typography } from '@mui/material';
import {
  CheckCircle as ApproveIcon,
  Save as SaveIcon,
  RestorePage as RestoreIcon,
  DeleteOutline as ClearIcon
} from '@mui/icons-material';

import { formatCurrency } from 'utils/formatters';

const formatDateTime = (value) => {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return `${value}`;
  return date.toLocaleTimeString('ar-LY', { hour: '2-digit', minute: '2-digit' });
};

/**
 * Sticky bottom action bar: running (temporary, unsaved) selection total,
 * draft save/restore/clear (local-only), plus a single finalize action.
 *
 * CLAIM-REVIEW-FOLLOWUP-1: approve/reject/clarify are decided per service in
 * the table above — this bar's only remaining job is to finalize whatever
 * was decided. The previous separate "رفض" (whole-claim reject) and "طلب
 * معلومات" buttons were redundant with per-line decisions and were removed;
 * "تمت المراجعة" now contextually calls POST /approve or /return-for-info
 * depending on whether any line is still marked "استيضاح" (see
 * ClaimReviewWorkspace.handleFinishReview).
 *
 * REVIEW-WORKSPACE-TABS-1: the draft save/restore/clear buttons (previously a
 * separate full-width Alert banner above the notes card) moved here to keep
 * them available without taking dedicated page space.
 */
const ClaimReviewActionBar = ({
  selectedApprovedAmount,
  reviewLock,
  submitting,
  selectedServicesCount,
  hasClarifyServices,
  draftSavedAt,
  onSaveDraftNow,
  onRestoreDraft,
  onClearDraft,
  onFinishReview
}) => {
  const lastSavedLabel = formatDateTime(draftSavedAt);

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
          <Stack direction="row" alignItems="center" spacing={1.5} flexWrap="wrap">
            <Typography variant="body2" fontWeight={700}>
              إجمالي الخدمات المختارة للاعتماد: {formatCurrency(selectedApprovedAmount || 0)}
            </Typography>
            <Tooltip title={lastSavedLabel ? `آخر حفظ للمسودة (محلي فقط): ${lastSavedLabel}` : 'لا توجد مسودة محفوظة محليًا'}>
              <Chip size="small" variant="outlined" label={lastSavedLabel ? `مسودة: ${lastSavedLabel}` : 'لا توجد مسودة'} />
            </Tooltip>
            <Button size="small" startIcon={<SaveIcon fontSize="small" />} onClick={onSaveDraftNow} disabled={submitting}>
              حفظ وخروج
            </Button>
            <Button size="small" startIcon={<RestoreIcon fontSize="small" />} onClick={onRestoreDraft} disabled={submitting}>
              استعادة
            </Button>
            <Button
              size="small"
              color="error"
              startIcon={<ClearIcon fontSize="small" />}
              onClick={onClearDraft}
              disabled={submitting || !draftSavedAt}
            >
              مسح
            </Button>
          </Stack>

          {reviewLock.locked ? (
            <Chip
              color={reviewLock.severity === 'warning' ? 'warning' : 'success'}
              label={reviewLock.message || 'لا يمكن تنفيذ قرار جديد على هذه المطالبة'}
              variant="outlined"
            />
          ) : (
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <Tooltip
                title={
                  hasClarifyServices
                    ? 'يوجد خدمة بانتظار استيضاح — سيتم إرسال المطالبة لمقدم الخدمة لاستكمال ما طُلب.'
                    : ''
                }
              >
                <span>
                  <Button
                    variant="contained"
                    color={hasClarifyServices ? 'info' : 'success'}
                    startIcon={<ApproveIcon />}
                    onClick={onFinishReview}
                    disabled={submitting}
                    sx={{ boxShadow: 2 }}
                  >
                    تمت المراجعة
                  </Button>
                </span>
              </Tooltip>
            </Stack>
          )}
        </Stack>
      </Box>
    </Paper>
  );
};

export default ClaimReviewActionBar;
