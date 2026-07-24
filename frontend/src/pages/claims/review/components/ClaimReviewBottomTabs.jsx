import { useState } from 'react';
import { Box, Card, Tab, Tabs } from '@mui/material';
import { AttachFile as AttachFileIcon, Chat as ChatIcon, History as HistoryIcon } from '@mui/icons-material';

import ClaimReviewAttachmentsViewer from './ClaimReviewAttachmentsViewer';
import ClaimReviewNotesPanel from './ClaimReviewNotesPanel';
import ClaimReviewHistoryPanel from './ClaimReviewHistoryPanel';

/**
 * REVIEW-WORKSPACE-TABS-1: documents / conversation / history, previously
 * three separate cards stacked in a side column, are now one tabbed card
 * placed BELOW the full-width services table — matching the reference
 * layout's "table is the star of the page" priority and removing the
 * side-by-side column split that squeezed the table's width.
 */
const ClaimReviewBottomTabs = ({
  attachments,
  claimId,
  onRefreshAttachments,
  chatMessages,
  chatInput,
  onChatInputChange,
  onSendChatMessage
}) => {
  const [tab, setTab] = useState('docs');
  const attachmentsCount = Array.isArray(attachments) ? attachments.length : 0;

  return (
    <Card sx={{ border: 1, borderColor: 'divider', boxShadow: 1 }}>
      <Tabs
        value={tab}
        onChange={(_event, value) => setTab(value)}
        variant="fullWidth"
        sx={{ borderBottom: 1, borderColor: 'divider', minHeight: '2.75rem' }}
      >
        <Tab
          value="docs"
          label={`المستندات (${attachmentsCount})`}
          icon={<AttachFileIcon fontSize="small" />}
          iconPosition="start"
          sx={{ minHeight: '2.75rem' }}
        />
        <Tab value="chat" label="محادثة" icon={<ChatIcon fontSize="small" />} iconPosition="start" sx={{ minHeight: '2.75rem' }} />
        <Tab value="history" label="السجل" icon={<HistoryIcon fontSize="small" />} iconPosition="start" sx={{ minHeight: '2.75rem' }} />
      </Tabs>

      <Box sx={{ p: '0.875rem' }}>
        {tab === 'docs' && <ClaimReviewAttachmentsViewer attachments={attachments} claimId={claimId} onRefresh={onRefreshAttachments} />}
        {tab === 'chat' && (
          <ClaimReviewNotesPanel
            chatMessages={chatMessages}
            chatInput={chatInput}
            onChatInputChange={onChatInputChange}
            onSendChatMessage={onSendChatMessage}
          />
        )}
        {tab === 'history' && <ClaimReviewHistoryPanel />}
      </Box>
    </Card>
  );
};

export default ClaimReviewBottomTabs;
