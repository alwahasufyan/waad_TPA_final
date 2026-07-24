import { Box, Stack, Typography, Paper, TextField, Button } from '@mui/material';
import { Chat as ChatIcon } from '@mui/icons-material';
import { SectionHeader } from './ClaimSectionPrimitives';

/**
 * Claim-side conversation between provider and reviewer.
 *
 * TODO (tracked, not fixed here — out of PROVIDER-PORTAL-RECOVERY-RESTORE-1's
 * scope, see CLAIM-REVIEW-NOTES-1): this is still localStorage-only, exactly
 * as it was in the pre-Phase-3B monolith and as the reviewer-side
 * `ClaimReviewNotesPanel.jsx` (Claims Review track) explicitly still is —
 * provider and reviewer each see only their own browser's copy, messages
 * never actually reach the other party. Not a regression introduced by this
 * restore; extracted verbatim, unchanged behavior.
 */
export function ClaimConversationPanel({ providerChatMessages, providerChatInput, setProviderChatInput, handleSendProviderChatMessage }) {
  return (
    <Box sx={{ mt: '1.5rem' }}>
      <SectionHeader icon={ChatIcon} title="محادثة المطالبة" subtitle="تواصل داخلي حول المطالبة" color="info" />
      <Box
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: '0.25rem',
          p: '0.75rem',
          bgcolor: 'background.default',
          maxHeight: '13.75rem',
          overflowY: 'auto',
          mb: '1.0rem'
        }}
      >
        {providerChatMessages.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            لا توجد رسائل بعد
          </Typography>
        ) : (
          <Stack spacing={1}>
            {providerChatMessages.map((message) => (
              <Paper key={message.id} variant="outlined" sx={{ p: '0.625rem', borderRadius: '0.25rem' }}>
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 0.5 }}>
                  <Typography variant="caption" fontWeight={600}>
                    {message.senderName}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {new Date(message.createdAt).toLocaleString('ar-LY')}
                  </Typography>
                </Stack>
                <Typography variant="body2">{message.text}</Typography>
              </Paper>
            ))}
          </Stack>
        )}
      </Box>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
        <TextField
          fullWidth
          size="small"
          value={providerChatInput}
          onChange={(event) => setProviderChatInput(event.target.value)}
          placeholder="اكتب رسالة داخل المطالبة..."
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault();
              handleSendProviderChatMessage();
            }
          }}
        />
        <Button variant="contained" onClick={handleSendProviderChatMessage} disabled={!providerChatInput.trim()}>
          إرسال
        </Button>
      </Stack>
    </Box>
  );
}
