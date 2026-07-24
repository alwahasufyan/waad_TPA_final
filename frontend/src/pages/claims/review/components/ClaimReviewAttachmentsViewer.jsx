import SharedAttachmentViewer from 'components/attachments/SharedAttachmentViewer';

/**
 * Claims Review tab content — thin wrapper around the shared attachment
 * viewer (DOCUMENTS-INTEGRITY-1) used identically in the Provider Portal.
 *
 * Previously this component was a download-only list with no preview at
 * all (a deliberate regression from an earlier "polish" pass — see git
 * history). It now supports real inline preview (PDF/image) and print,
 * with Word/Excel always downloading to be opened externally.
 */
const ClaimReviewAttachmentsViewer = ({ attachments, claimId, onRefresh }) => (
  <SharedAttachmentViewer
    attachments={attachments}
    claimId={claimId}
    onRefresh={onRefresh}
    emptyMessage="لا توجد مستندات مرفقة بهذه المطالبة"
  />
);

export default ClaimReviewAttachmentsViewer;
