import { Box, Button, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { Send as SendIcon, InfoOutlined as InfoIcon } from '@mui/icons-material';

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
 * Reviewer notes / claim conversation — chat-only content.
 *
 * CLAIM-REVIEW-SPLIT-2A: this remains local-only (browser localStorage), exactly
 * as in the previous monolith — it is NOT yet persisted server-side and is not
 * visible to a second reviewer or to the provider. CLAIM-REVIEW-NOTES-1 is the
 * phase scoped to move this to real server persistence. The limitation is now
 * disclosed via a small info tooltip instead of a full-width Alert banner.
 *
 * REVIEW-WORKSPACE-TABS-1: no longer wrapped in its own SectionCard, and the
 * draft-save/restore/clear controls moved to ClaimReviewActionBar — this is
 * now rendered as one tab panel inside ClaimReviewBottomTabs.
 */
const ClaimReviewNotesPanel = ({ chatMessages, chatInput, onChatInputChange, onSendChatMessage }) => {
  const safeChatMessages = Array.isArray(chatMessages) ? chatMessages : [];

  return (
    <Stack spacing={1.5}>
      <Stack direction="row" spacing={0.75} alignItems="center">
        <Typography variant="caption" color="text.secondary">
          محادثة المطالبة
        </Typography>
        <Tooltip title="هذه المحادثة محفوظة محليًا في متصفحك فقط ولم يتم ربطها بالخادم بعد — لن يراها مراجع آخر أو مقدم الخدمة حاليًا.">
          <InfoIcon fontSize="inherit" sx={{ color: 'text.disabled' }} />
        </Tooltip>
      </Stack>

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
              <Box key={message.id} sx={{ p: 1, bgcolor: 'background.paper', borderRadius: 1, border: 1, borderColor: 'divider' }}>
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
  );
};

export default ClaimReviewNotesPanel;
