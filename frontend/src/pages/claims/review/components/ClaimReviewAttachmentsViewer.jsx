import { UnifiedAttachmentViewer } from 'components/medical-review';

/**
 * Read-only attachment viewer for the reviewer workspace.
 *
 * Thin wrapper around the existing, already reviewer-appropriate
 * UnifiedAttachmentViewer — no upload/delete controls are wired here, matching
 * the existing behavior (reviewers only ever download/preview, never modify
 * provider-submitted attachments).
 */
const ClaimReviewAttachmentsViewer = ({ attachments, onDownload, onRefresh, selectedAttachmentId, onSelectionChange }) => (
  <UnifiedAttachmentViewer
    attachments={Array.isArray(attachments) ? attachments : []}
    loading={false}
    onDownload={onDownload}
    onRefresh={onRefresh}
    selectedAttachmentId={selectedAttachmentId}
    onSelectionChange={onSelectionChange}
    emptyMessage="لا توجد مستندات مرفقة بهذه المطالبة"
  />
);

export default ClaimReviewAttachmentsViewer;
