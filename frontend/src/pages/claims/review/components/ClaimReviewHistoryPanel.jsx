import { Box, Typography } from '@mui/material';

/**
 * "سجل المطالبة" (claim history/audit timeline) tab content.
 *
 * CLAIM-REVIEW-WORKSPACE-LOVABLE-POLISH-1: the attached reference shows a
 * claim activity timeline, but no backend endpoint currently exposes claim
 * audit history to the frontend (confirmed: no `/claims/{id}/audit-history`
 * route, no matching frontend service method) — `ClaimAuditLog` entries exist
 * server-side but are not exposed via any API today. Rather than fabricate
 * timeline events, this shows an honest placeholder. Deferred: wiring a real
 * audit endpoint + this panel to it.
 *
 * REVIEW-WORKSPACE-TABS-1: no longer wrapped in its own SectionCard — this is
 * now rendered as one tab panel inside ClaimReviewBottomTabs.
 */
const ClaimReviewHistoryPanel = () => (
  <Box sx={{ py: '1rem', textAlign: 'center' }}>
    <Typography variant="body2" color="text.secondary">
      لا يوجد سجل متاح للعرض حالياً
    </Typography>
  </Box>
);

export default ClaimReviewHistoryPanel;
