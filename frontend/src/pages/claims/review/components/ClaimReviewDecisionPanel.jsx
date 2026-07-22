import { TextField } from '@mui/material';
import { Cancel as RejectIcon } from '@mui/icons-material';

import SectionCard from './SectionCard';

/**
 * Rejection-decision notes. Shown only when at least one service line has been
 * locally marked REJECT, or the claim is already REJECTED — matches the
 * previous monolith's behavior exactly. This text feeds the reviewerComment /
 * rejectionReason sent on submit (see ClaimReviewWorkspace's handleReject).
 */
const ClaimReviewDecisionPanel = ({ visible, medicalNotes, onNotesChange }) => {
  if (!visible) return null;

  return (
    <SectionCard title="ملاحظات قرار الرفض" icon={RejectIcon} defaultExpanded={false}>
      <TextField
        fullWidth
        multiline
        rows={3}
        value={medicalNotes}
        onChange={(event) => onNotesChange(event.target.value)}
        placeholder="تظهر هذه الخانة عند الحاجة فقط (مثل الرفض)"
      />
    </SectionCard>
  );
};

export default ClaimReviewDecisionPanel;
