/**
 * Workflow Step Definitions
 * For Claims and Pre-Approvals timeline visualization
 */

export const CLAIM_WORKFLOW_STEPS = {
  ar: [
    { key: 'DRAFT', label: 'مسودة', labelEn: 'Draft' },
    { key: 'SUBMITTED', label: 'مقدمة', labelEn: 'Submitted' },
    { key: 'UNDER_REVIEW', label: 'قيد المراجعة', labelEn: 'Under Review' },
    { key: 'APPROVAL_IN_PROGRESS', label: 'جاري معالجة الموافقة', labelEn: 'Approval In Progress' },
    { key: 'NEEDS_CORRECTION', label: 'تحتاج تصحيح', labelEn: 'Needs Correction' },
    { key: 'APPROVED', label: 'موافق عليها', labelEn: 'Approved' },
    { key: 'BATCHED', label: 'ضمن دفعة تسوية', labelEn: 'Batched' },
    { key: 'REJECTED', label: 'مرفوضة', labelEn: 'Rejected' },
    { key: 'SETTLED', label: 'مسددة', labelEn: 'Settled' }
  ],
  en: [
    { key: 'DRAFT', label: 'Draft', labelEn: 'Draft' },
    { key: 'SUBMITTED', label: 'Submitted', labelEn: 'Submitted' },
    { key: 'UNDER_REVIEW', label: 'Under Review', labelEn: 'Under Review' },
    { key: 'APPROVAL_IN_PROGRESS', label: 'Approval In Progress', labelEn: 'Approval In Progress' },
    { key: 'NEEDS_CORRECTION', label: 'Needs Correction', labelEn: 'Needs Correction' },
    { key: 'APPROVED', label: 'Approved', labelEn: 'Approved' },
    { key: 'BATCHED', label: 'Batched', labelEn: 'Batched' },
    { key: 'REJECTED', label: 'Rejected', labelEn: 'Rejected' },
    { key: 'SETTLED', label: 'Settled', labelEn: 'Settled' }
  ]
};

export const PREAPPROVAL_WORKFLOW_STEPS = {
  ar: [
    { key: 'PENDING', label: 'معلقة', labelEn: 'Pending' },
    { key: 'UNDER_REVIEW', label: 'قيد المراجعة', labelEn: 'Under Review' },
    { key: 'APPROVAL_IN_PROGRESS', label: 'جاري معالجة الموافقة', labelEn: 'Approval In Progress' },
    { key: 'APPROVED', label: 'موافق عليه', labelEn: 'Approved' },
    { key: 'ACKNOWLEDGED', label: 'تم الاطلاع', labelEn: 'Acknowledged' },
    { key: 'NEEDS_CORRECTION', label: 'تحتاج تصحيح', labelEn: 'Needs Correction' },
    { key: 'REJECTED', label: 'مرفوض', labelEn: 'Rejected' }
  ],
  en: [
    { key: 'PENDING', label: 'Pending', labelEn: 'Pending' },
    { key: 'UNDER_REVIEW', label: 'Under Review', labelEn: 'Under Review' },
    { key: 'APPROVAL_IN_PROGRESS', label: 'Approval In Progress', labelEn: 'Approval In Progress' },
    { key: 'APPROVED', label: 'Approved', labelEn: 'Approved' },
    { key: 'ACKNOWLEDGED', label: 'Acknowledged', labelEn: 'Acknowledged' },
    { key: 'NEEDS_CORRECTION', label: 'Needs Correction', labelEn: 'Needs Correction' },
    { key: 'REJECTED', label: 'Rejected', labelEn: 'Rejected' }
  ]
};

/**
 * Get workflow steps based on current status
 * Returns only relevant steps (removes branching paths based on current state)
 */
export const getWorkflowSteps = (type, currentStatus, language = 'ar') => {
  const allSteps =
    type === 'claim'
      ? CLAIM_WORKFLOW_STEPS[language] || CLAIM_WORKFLOW_STEPS.ar
      : PREAPPROVAL_WORKFLOW_STEPS[language] || PREAPPROVAL_WORKFLOW_STEPS.ar;

  // For claim workflow, determine the path based on status
  if (type === 'claim') {
    const baseSteps = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW'];

    if (currentStatus === 'NEEDS_CORRECTION') {
      return allSteps.filter((s) => [...baseSteps, 'NEEDS_CORRECTION', 'SUBMITTED'].includes(s.key));
    }

    if (currentStatus === 'APPROVAL_IN_PROGRESS') {
      return allSteps.filter((s) => [...baseSteps, 'APPROVAL_IN_PROGRESS'].includes(s.key));
    }

    if (currentStatus === 'BATCHED') {
      return allSteps.filter((s) => [...baseSteps, 'APPROVED', 'BATCHED', 'SETTLED'].includes(s.key));
    }

    if (currentStatus === 'APPROVED' || currentStatus === 'SETTLED') {
      return allSteps.filter((s) => [...baseSteps, 'APPROVED', 'SETTLED'].includes(s.key));
    }

    if (currentStatus === 'REJECTED') {
      return allSteps.filter((s) => [...baseSteps, 'REJECTED'].includes(s.key));
    }

    // Default: show path to approval
    return allSteps.filter((s) => [...baseSteps, 'APPROVED', 'SETTLED'].includes(s.key));
  }

  // For pre-approval workflow
  const baseSteps = ['PENDING', 'UNDER_REVIEW'];

  if (currentStatus === 'NEEDS_CORRECTION') {
    return allSteps.filter((s) => [...baseSteps, 'NEEDS_CORRECTION', 'PENDING'].includes(s.key));
  }

  if (currentStatus === 'APPROVAL_IN_PROGRESS') {
    return allSteps.filter((s) => [...baseSteps, 'APPROVAL_IN_PROGRESS'].includes(s.key));
  }

  if (currentStatus === 'ACKNOWLEDGED') {
    return allSteps.filter((s) => [...baseSteps, 'APPROVED', 'ACKNOWLEDGED'].includes(s.key));
  }

  if (currentStatus === 'APPROVED') {
    return allSteps.filter((s) => [...baseSteps, 'APPROVED'].includes(s.key));
  }

  if (currentStatus === 'REJECTED') {
    return allSteps.filter((s) => [...baseSteps, 'REJECTED'].includes(s.key));
  }

  // Default: show path to approval
  return allSteps.filter((s) => [...baseSteps, 'APPROVED'].includes(s.key));
};

/**
 * Map backend status to workflow step
 */
export const mapStatusToWorkflowStep = (backendStatus) => {
  const statusMap = {
    // Claim statuses
    DRAFT: 'DRAFT',
    SUBMITTED: 'SUBMITTED',
    UNDER_REVIEW: 'UNDER_REVIEW',
    APPROVAL_IN_PROGRESS: 'APPROVAL_IN_PROGRESS',
    APPROVED: 'APPROVED',
    BATCHED: 'BATCHED',
    REJECTED: 'REJECTED',
    NEEDS_CORRECTION: 'NEEDS_CORRECTION',
    SETTLED: 'SETTLED',

    // Pre-approval statuses
    PENDING: 'PENDING',
    UNDER_REVIEW: 'UNDER_REVIEW',
    APPROVAL_IN_PROGRESS: 'APPROVAL_IN_PROGRESS',
    APPROVED: 'APPROVED',
    ACKNOWLEDGED: 'ACKNOWLEDGED',
    REJECTED: 'REJECTED',
    NEEDS_CORRECTION: 'NEEDS_CORRECTION',
    EXPIRED: 'EXPIRED',
    CANCELLED: 'CANCELLED',
    USED: 'USED'
  };

  return statusMap[backendStatus] || backendStatus;
};

export default {
  CLAIM_WORKFLOW_STEPS,
  PREAPPROVAL_WORKFLOW_STEPS,
  getWorkflowSteps,
  mapStatusToWorkflowStep
};
