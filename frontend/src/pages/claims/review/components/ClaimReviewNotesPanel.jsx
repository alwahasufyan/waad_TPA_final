import { Alert, Box, Button, Stack, TextField, Typography } from '@mui/material';
import { Chat as ChatIcon, Send as SendIcon } from '@mui/icons-material';

import SectionCard from './SectionCard';

const formatDateTime = (value) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return `${value}`;
  return date.toLocaleString('ar-LY', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

/**
 * Reviewer notes / claim conversation panel.
 *
 * CLAIM-REVIEW-SPLIT-2A: this remains local-only (browser localStorage), exactly
 * as in the previous monolith — it is NOT yet persisted server-side and is not
 * visible to a second reviewer or to the provider. CLAIM-REVIEW-NOTES-1 is the
 * phase scoped to move this to real server persistence; the banner below makes
 * the current limitation explicit rather than silently implying otherwise.
 */
const ClaimReviewNotesPanel = ({
  draftSavedAt,
  onSaveDraftNow,
  onRestoreDraft,
  onClearDraft,
  submitting,
  chatMessages,
  chatInput,
  onChatInputChange,
  onSendChatMessage
}) => {
  const safeChatMessages = Array.isArray(chatMessages) ? chatMessages : [];

  return (
    <>
      <Alert severity="info" sx={{ py: 1 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
          <Typography variant="caption" fontWeight={600}>
            {draftSavedAt ? `آخر حفظ للمسودة (محلي فقط): ${formatDateTime(draftSavedAt)}` : 'لا توجد مسودة محفوظة محليًا'}
          </Typography>
          <Stack direction="row" spacing={0.5}>
            <Button size="small" variant="outlined" onClick={onSaveDraftNow} disabled={submitting}>
              حفظ وخروج
            </Button>
            <Button size="small" variant="outlined" onClick={onRestoreDraft} disabled={submitting}>
              استعادة
            </Button>
            <Button size="small" color="error" variant="outlined" onClick={onClearDraft} disabled={submitting || !draftSavedAt}>
              مسح
            </Button>
          </Stack>
        </Stack>
      </Alert>

      <SectionCard title="محادثة المطالبة" icon={ChatIcon} defaultExpanded={false}>
        <Stack spacing={1.5}>
          <Alert severity="warning" sx={{ py: 0.5 }}>
            <Typography variant="caption">
              هذه المحادثة محفوظة محليًا في متصفحك فقط ولم يتم ربطها بالخادم بعد — لن يراها مراجع آخر أو مقدم
              الخدمة حاليًا.
            </Typography>
          </Alert>

          <Box
            sx={{
              maxHeight: '13.75rem',
              overflowY: 'auto',
              border: 1,
              borderColor: 'divider',
              borderRadius: 1,
              p: '0.75rem',
              bgcolor: 'background.default'
            }}
          >
            {safeChatMessages.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                لا توجد رسائل بعد
              </Typography>
            ) : (
              <Stack spacing={1}>
                {safeChatMessages.map((message) => (
                  <Box
                    key={message.id}
                    sx={{ p: 1, bgcolor: 'background.paper', borderRadius: 1, border: 1, borderColor: 'divider' }}
                  >
                    <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 0.5 }}>
                      <Typography variant="caption" fontWeight={600}>
                        {message.senderName}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatDateTime(message.createdAt)}
                      </Typography>
                    </Stack>
                    <Typography variant="body2">{message.text}</Typography>
                  </Box>
                ))}
              </Stack>
            )}
          </Box>

          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
            <TextField
              fullWidth
              size="small"
              value={chatInput}
              onChange={(event) => onChatInputChange(event.target.value)}
              placeholder="اكتب رسالة داخل المطالبة..."
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault();
                  onSendChatMessage();
                }
              }}
            />
            <Button variant="contained" startIcon={<SendIcon />} onClick={onSendChatMessage} disabled={!chatInput.trim()}>
              إرسال
            </Button>
          </Stack>
        </Stack>
      </SectionCard>
    </>
  );
};

export default ClaimReviewNotesPanel;
