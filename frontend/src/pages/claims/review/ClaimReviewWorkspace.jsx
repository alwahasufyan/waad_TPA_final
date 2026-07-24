/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Claim Review Workspace — dedicated reviewer/company-side claim review screen
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * CLAIM-REVIEW-SPLIT-2A: extracted from the previous monolithic
 * ClaimViewMedicalReview.jsx into this workspace + its component tree
 * (see ./components). Behavior, endpoints, and calculations are unchanged —
 * this phase is a structural extraction plus clearer financial labeling
 * (CLAIMS-AMOUNT-LABEL-1) and explicit "not yet persisted" notices on the
 * local-only line decisions and notes (closed in later phases).
 *
 * Mounted at /claims/:id/medical-review (route unchanged).
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Alert, Box, CircularProgress, Stack, Chip, Typography } from '@mui/material';
import { useSnackbar } from 'notistack';

import PageErrorBoundary from 'components/SafeStates/PageErrorBoundary';

import { claimsService } from 'services/api';
import { getClaimAttachments } from 'services/api/files.service';
import { getMemberRemainingLimit } from 'services/api/members.service';

import ClaimReviewContextHeader from './components/ClaimReviewContextHeader';
import ClaimReviewServiceLinesPanel, { SERVICE_DECISION, REJECTION_REASONS } from './components/ClaimReviewServiceLinesPanel';
import ClaimReviewFinancialSummary from './components/ClaimReviewFinancialSummary';
import ClaimReviewBottomTabs from './components/ClaimReviewBottomTabs';
import ClaimReviewDecisionPanel from './components/ClaimReviewDecisionPanel';
import ClaimReviewActionBar from './components/ClaimReviewActionBar';

const REVIEW_ACTION_ALLOWED_STATUSES = new Set(['SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'NEEDS_CORRECTION']);
const FINALIZED_STATUSES = new Set(['APPROVED', 'REJECTED', 'BATCHED', 'SETTLED']);

// CLAIM-REVIEW-SPLIT-2C: line-level decisions are allowed in a narrower set
// of claim statuses than the general review-action bar above — once a claim
// is APPROVED (or later), a reviewer must not be able to look like they're
// still adjusting individual line decisions on it. Must match the backend's
// ClaimService.LINE_DECISION_ALLOWED_STATUSES exactly.
const LINE_DECISION_ALLOWED_STATUSES = new Set(['SUBMITTED', 'UNDER_REVIEW', 'NEEDS_CORRECTION']);

const SERVER_DECISION_TO_LOCAL = {
  APPROVED: 'APPROVE',
  REJECTED: 'REJECT',
  CLARIFICATION_REQUIRED: 'CLARIFY'
};
const LOCAL_DECISION_TO_SERVER = {
  APPROVE: 'APPROVED',
  REJECT: 'REJECTED',
  CLARIFY: 'CLARIFICATION_REQUIRED'
};

const ClaimReviewWorkspaceInner = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { enqueueSnackbar } = useSnackbar();

  // Bug fix: prev/next used to be naive id±1, which hits arbitrary claim ids
  // (other providers, gaps, deleted rows) and 400/409s — claim ids are never
  // sequential. The reviewer inbox now passes the exact list of claim ids
  // from the page the reviewer was browsing via router state; prev/next only
  // navigate within that real, already-authorized list. With no state (direct
  // URL, refresh, deep link) there is no reliable "adjacent claim" concept,
  // so both buttons are disabled rather than guessing.
  const claimIdsInContext = useMemo(() => {
    const ids = location.state?.claimIds;
    return Array.isArray(ids) ? ids : null;
  }, [location.state]);

  const { prevClaimId, nextClaimId } = useMemo(() => {
    if (!claimIdsInContext) return { prevClaimId: null, nextClaimId: null };
    const currentIndex = claimIdsInContext.findIndex((claimId) => String(claimId) === String(id));
    if (currentIndex === -1) return { prevClaimId: null, nextClaimId: null };
    return {
      prevClaimId: currentIndex > 0 ? claimIdsInContext[currentIndex - 1] : null,
      nextClaimId: currentIndex < claimIdsInContext.length - 1 ? claimIdsInContext[currentIndex + 1] : null
    };
  }, [claimIdsInContext, id]);

  const navigateToClaim = useCallback(
    (claimId) => {
      if (!claimId) return;
      navigate(`/claims/${claimId}/medical-review`, { state: { claimIds: claimIdsInContext } });
    },
    [navigate, claimIdsInContext]
  );

  // State
  const [claim, setClaim] = useState(null);
  const [attachments, setAttachments] = useState([]);
  const [medicalNotes, setMedicalNotes] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [draftSavedAt, setDraftSavedAt] = useState(null);
  const [chatMessages, setChatMessages] = useState([]);
  const [chatInput, setChatInput] = useState('');
  const [serviceDecisions, setServiceDecisions] = useState({});
  const [activeServiceKey, setActiveServiceKey] = useState(null);
  // CLAIM-REVIEW-SPLIT-2C: serviceKey of the line currently being saved, if any.
  const [savingServiceKey, setSavingServiceKey] = useState(null);
  // DOCUMENTS-REVIEW-UX-1: real member coverage summary (annual limit / used /
  // remaining), shown as KPI cards above the services table.
  const [memberCoverage, setMemberCoverage] = useState(null);

  const draftStorageKey = useMemo(() => `claim-review-draft-${id}`, [id]);
  const chatStorageKey = useMemo(() => `claim-review-chat-${id}`, [id]);

  const currentUser = useMemo(() => {
    try {
      const localUser = localStorage.getItem('user_details');
      if (localUser) {
        return JSON.parse(localUser);
      }
    } catch (error) {
      console.warn('Unable to parse user_details from localStorage:', error);
    }

    try {
      const sessionUser = sessionStorage.getItem('user');
      if (sessionUser) {
        return JSON.parse(sessionUser);
      }
    } catch (error) {
      console.warn('Unable to parse user from sessionStorage:', error);
    }

    return {};
  }, []);

  const currentUserName = currentUser?.fullName || currentUser?.name || currentUser?.username || 'المراجع الطبي';
  const currentUserRole = currentUser?.role || (Array.isArray(currentUser?.roles) ? currentUser.roles[0] : null) || 'MEDICAL_REVIEWER';

  const mapAttachment = useCallback(async (attachment) => {
    const directUrl = attachment.fileUrl || attachment.url || attachment.downloadUrl || '';
    const mimeType = attachment.contentType || attachment.mimeType || attachment.fileType || '';

    return {
      id: attachment.id,
      fileName: attachment.fileName || attachment.originalFileName || attachment.name,
      fileSize: attachment.fileSize || attachment.size,
      mimeType,
      fileType: attachment.fileType,
      url: directUrl,
      downloadUrl: directUrl
    };
  }, []);

  const fetchAttachments = useCallback(
    async (fallbackAttachments = []) => {
      try {
        const response = await getClaimAttachments(id);
        const rawAttachments = Array.isArray(response) ? response : response?.data || response?.items || [];
        const transformed = await Promise.all(rawAttachments.map((attachment) => mapAttachment(attachment)));
        setAttachments(transformed);
        return;
      } catch (error) {
        console.error('Error fetching attachments from endpoint, using fallback:', error);
      }

      if (Array.isArray(fallbackAttachments) && fallbackAttachments.length > 0) {
        const transformed = await Promise.all(fallbackAttachments.map((attachment) => mapAttachment(attachment)));
        setAttachments(transformed);
      } else {
        setAttachments([]);
      }
    },
    [id, mapAttachment]
  );

  const normalizedClaim = useMemo(() => {
    if (!claim) return null;

    const claimServices = claim.lines || claim.services || claim.claimServices || claim.items || claim.lineItems || [];
    const services = Array.isArray(claimServices)
      ? claimServices.map((service, index) => ({
          id: service.id,
          serviceKey: service.id ? `id-${service.id}` : `${service.medicalServiceCode || service.serviceCode || 'service'}-${index}`,
          serviceName:
            service.medicalServiceName ||
            service.serviceName ||
            service.name ||
            service.description ||
            service.procedureName ||
            '-',
          serviceCode: service.medicalServiceCode || service.serviceCode || service.code || service.procedureCode || '-',
          quantity: service.quantity || 1,
          unitPrice: service.unitPrice ?? service.price ?? service.netPrice ?? 0,
          totalAmount: service.totalPrice ?? service.totalAmount ?? service.claimedAmount ?? 0,
          medicalServiceId: service.medicalServiceId,
          pricingItemId: service.pricingItemId,
          benefitLimit: service.benefitLimit,
          usedAmount: service.usedAmount,
          remainingAmount: service.remainingAmount,
          // CLAIM-REVIEW-FOLLOWUP-1: the policy's coverage percentage for
          // this line (e.g. 80%) — display only, matching the Batch entry
          // screen's "التحمل %" column. Never used to recompute money
          // client-side; the authoritative split is companyShare/patientShare.
          coveragePercent: service.coveragePercent,
          // CLAIMS-FINANCIAL-INTEGRITY-2: authoritative backend financial split —
          // never recomputed client-side from coveragePercent.
          companyShareBeforeDiscount: service.companyShareBeforeDiscount,
          providerDiscountAmount: service.providerDiscountAmount,
          companyShare: service.companyShare,
          patientShare: service.patientShare,
          refusedAmount: service.refusedAmount,
          // CLAIM-REVIEW-SPLIT-2C: server-persisted line decision fields.
          reviewerDecision: service.reviewerDecision,
          rejected: service.rejected,
          rejectionReason: service.rejectionReason,
          reviewerNotes: service.reviewerNotes
        }))
      : claim.serviceName
        ? [
            {
              serviceKey: `single-${claim.serviceCode || 'service'}`,
              serviceName: claim.serviceName,
              serviceCode: claim.serviceCode,
              quantity: claim.quantity || 1,
              unitPrice: claim.unitPrice || claim.requestedAmount || claim.claimedAmount || 0,
              totalAmount: claim.totalAmount || claim.requestedAmount || claim.claimedAmount || 0
            }
          ]
        : [];

    return {
      claimNumber: claim.claimNumber || `CLM-${claim.id || id}`,
      status: claim.status,
      allowedNextStatuses: Array.isArray(claim.allowedNextStatuses) ? claim.allowedNextStatuses : [],
      memberId: claim.memberId || claim.member?.id,
      memberName: claim.memberName || claim.memberFullName || claim.member?.fullName || claim.member?.name,
      memberCivilId: claim.memberNationalNumber || claim.memberCivilId || claim.member?.nationalId || claim.member?.civilId,
      memberCardNumber: claim.memberCardNumber || claim.member?.cardNumber,
      memberPhone: claim.memberPhone || claim.member?.phone,
      employerName: claim.employerName || claim.employer?.name,
      providerName: claim.providerName || claim.provider?.name,
      policyNumber: claim.policyNumber || claim.benefitPackageCode || claim.member?.policyNumber,
      coverageType: claim.coverageType || claim.benefitPackageName || claim.planType || claim.member?.coverageType,
      claimDate: claim.serviceDate || claim.claimDate || claim.submittedDate || claim.submissionDate || claim.createdAt,
      services,
      primaryDiagnosis: claim.diagnosisDescription || claim.primaryDiagnosis || claim.diagnosis || claim.primaryIcdDescription,
      icdCode: claim.diagnosisCode || claim.icdCode || claim.primaryIcdCode,
      secondaryDiagnosis: claim.secondaryDiagnosis,
      claimedAmount: claim.requestedAmount ?? claim.totalAmount ?? claim.claimedAmount ?? 0,
      approvedAmount: claim.approvedAmount ?? 0,
      copayAmount: claim.patientCoPay ?? claim.copayAmount ?? 0,
      medicalNotes: claim.medicalNotes || claim.reviewerComment || '',
      preApprovalReferenceNumber: claim.preApprovalReferenceNumber,
      // PROVIDER-PORTAL-REVIEW-ROUTING-2: who created/submitted/reviewed this claim.
      submissionChannel: claim.submissionChannel,
      createdBy: claim.createdBy,
      submittedBy: claim.submittedBy,
      reviewedBy: claim.reviewedBy
    };
  }, [claim, id]);

  // DOCUMENTS-REVIEW-UX-1: once the member is known, fetch their real
  // coverage summary for the KPI cards above the services table. Failure is
  // silent (cards simply don't render) — this is supplementary context, not
  // part of the review decision itself.
  useEffect(() => {
    const memberId = normalizedClaim?.memberId;
    if (!memberId) {
      setMemberCoverage(null);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const data = await getMemberRemainingLimit(memberId);
        if (!cancelled) setMemberCoverage(data);
      } catch (error) {
        if (!cancelled) setMemberCoverage(null);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [normalizedClaim?.memberId]);

  // Fetch claim data
  useEffect(() => {
    if (id) {
      fetchClaim();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const fetchClaim = async () => {
    try {
      setLoading(true);
      const data = await claimsService.getById(id);
      setClaim(data);

      const draftPayload = localStorage.getItem(draftStorageKey);
      if (draftPayload) {
        try {
          const parsedDraft = JSON.parse(draftPayload);
          setMedicalNotes(parsedDraft?.notes || data.medicalNotes || data.reviewerComment || '');
          setDraftSavedAt(parsedDraft?.updatedAt || null);
        } catch (draftError) {
          console.warn('Failed to parse draft payload, fallback to claim notes:', draftError);
          setMedicalNotes(data.medicalNotes || data.reviewerComment || '');
        }
      } else {
        setMedicalNotes(data.medicalNotes || data.reviewerComment || '');
      }

      await fetchAttachments(data?.attachments || []);
    } catch (error) {
      console.error('Error fetching claim:', error);
      enqueueSnackbar('فشل في تحميل المطالبة', { variant: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    try {
      const existingChat = localStorage.getItem(chatStorageKey);
      if (existingChat) {
        const parsedMessages = JSON.parse(existingChat);
        setChatMessages(Array.isArray(parsedMessages) ? parsedMessages : []);
      } else {
        setChatMessages([]);
      }
    } catch (error) {
      console.warn('Failed to parse chat history:', error);
      setChatMessages([]);
    }
  }, [chatStorageKey]);

  useEffect(() => {
    if (!normalizedClaim?.services?.length) {
      setServiceDecisions({});
      return;
    }

    setServiceDecisions((previousDecisions) => {
      const nextDecisions = {};
      normalizedClaim.services.forEach((service) => {
        const previous = previousDecisions[service.serviceKey];
        if (previous) {
          // Preserve in-session edits across other unrelated re-renders.
          nextDecisions[service.serviceKey] = previous;
          return;
        }
        // CLAIM-REVIEW-SPLIT-2C: seed from the server-persisted decision on
        // first load/reload, instead of always defaulting to APPROVE.
        const persistedLocalDecision = SERVER_DECISION_TO_LOCAL[service.reviewerDecision] || SERVICE_DECISION.APPROVE;
        // CLAIM-REVIEW-FOLLOWUP-1: on a fresh load (no in-session entry yet),
        // reconstruct whether a REJECTED line was a partial or full rejection
        // from the fields the server already returns — `rejected` is only
        // ever true when the FULL companyShareBeforeDiscount was refused (see
        // ClaimService.submitLineDecision); a REJECTED line with
        // rejected === false was a partial rejection of exactly
        // `refusedAmount`. Without this, the caption below always fell back
        // to "رفض كلي" after any page reload, even for partial rejections.
        const persistedManualRefusedAmount =
          persistedLocalDecision === SERVICE_DECISION.REJECT && !service.rejected && Number(service.refusedAmount) > 0
            ? Number(service.refusedAmount)
            : undefined;
        nextDecisions[service.serviceKey] = {
          decision: persistedLocalDecision,
          reason: service.rejectionReason || '',
          reviewerNotes: service.reviewerNotes || '',
          manualRefusedAmount: persistedManualRefusedAmount
        };
      });
      return nextDecisions;
    });
  }, [normalizedClaim?.services]);

  useEffect(() => {
    if (loading) return;

    const saveTimer = setTimeout(() => {
      const payload = {
        notes: medicalNotes || '',
        updatedAt: new Date().toISOString()
      };
      localStorage.setItem(draftStorageKey, JSON.stringify(payload));
      setDraftSavedAt(payload.updatedAt);
    }, 600);

    return () => clearTimeout(saveTimer);
  }, [medicalNotes, draftStorageKey, loading]);

  useEffect(() => {
    return () => {
      attachments.forEach((attachment) => {
        if (typeof attachment?.url === 'string' && attachment.url.startsWith('blob:')) {
          URL.revokeObjectURL(attachment.url);
        }
      });
    };
  }, [attachments]);

  // Decision handlers
  const ensureClaimUnderReview = useCallback(async () => {
    const latestClaim = await claimsService.getById(id);
    if (latestClaim?.status !== 'SUBMITTED') {
      return latestClaim;
    }

    try {
      await claimsService.startReview(id);
    } catch (error) {
      const isTransitionConflict = error?.status === 409 && error?.errorCode === 'INVALID_CLAIM_TRANSITION';
      if (!isTransitionConflict) {
        throw error;
      }
    }

    const afterStartReview = await claimsService.getById(id);
    if (afterStartReview?.status === 'SUBMITTED') {
      const transitionError = new Error('تعذر نقل المطالبة إلى قيد المراجعة. أعد تشغيل خدمة الباك-إند ثم أعد المحاولة.');
      transitionError.status = 409;
      transitionError.errorCode = 'INVALID_CLAIM_TRANSITION';
      throw transitionError;
    }

    return afterStartReview;
  }, [id]);

  // CLAIMS-FINANCIAL-SOURCE-OF-TRUTH-1: this is a client-side PREVIEW of what
  // the claim-level approved amount will be once "تمت المراجعة" is pressed —
  // it must sum each service's authoritative net company share
  // (companyShare, already net of the contract discount), not the gross
  // requested total. Summing totalAmount here previously overstated the
  // preview (e.g. showing 200 instead of the real 135), inconsistent with
  // the actual approvedAmount the backend computes on approval.
  const selectedApprovedAmount = useMemo(() => {
    if (!normalizedClaim?.services?.length) {
      return 0;
    }

    return normalizedClaim.services
      .filter((service) => serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.APPROVE)
      .reduce((sum, service) => sum + Number(service.companyShare ?? service.totalAmount ?? 0), 0);
  }, [normalizedClaim?.services, serviceDecisions]);

  const selectedServicesCount = useMemo(() => {
    if (!normalizedClaim?.services?.length) return 0;
    return normalizedClaim.services.filter((service) => serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.APPROVE).length;
  }, [normalizedClaim?.services, serviceDecisions]);

  const rejectedServicesCount = useMemo(
    () => Object.values(serviceDecisions).filter((entry) => entry?.decision === SERVICE_DECISION.REJECT).length,
    [serviceDecisions]
  );

  const clarifyServicesCount = useMemo(
    () => Object.values(serviceDecisions).filter((entry) => entry?.decision === SERVICE_DECISION.CLARIFY).length,
    [serviceDecisions]
  );

  const claimStatus = normalizedClaim?.status || '';

  const reviewLock = useMemo(() => {
    if (!claimStatus) {
      return { locked: false, severity: 'info', message: '' };
    }

    if (claimStatus === 'APPROVAL_IN_PROGRESS') {
      return {
        locked: true,
        severity: 'info',
        message: 'تم إرسال الموافقة مسبقًا، والمطالبة قيد المعالجة. لا يمكن إرسال قرار جديد الآن.'
      };
    }

    if (FINALIZED_STATUSES.has(claimStatus)) {
      const finalizedMessageByStatus = {
        APPROVED: 'تمت الموافقة على المطالبة مسبقًا، لذلك تم إخفاء أزرار الموافقة والرفض.',
        REJECTED: 'تم رفض المطالبة مسبقًا، لذلك لا يمكن تنفيذ موافقة أو رفض جديد.',
        BATCHED: 'المطالبة انتقلت إلى مرحلة الدفعات المالية، ولا يمكن تعديل قرار المراجعة الطبية.',
        SETTLED: 'المطالبة تمت تسويتها ماليًا، ولا يمكن تعديل قرار المراجعة الطبية.'
      };

      return {
        locked: true,
        severity: claimStatus === 'REJECTED' ? 'warning' : 'success',
        message: finalizedMessageByStatus[claimStatus] || 'حالة المطالبة الحالية لا تسمح باتخاذ قرار جديد.'
      };
    }

    if (!REVIEW_ACTION_ALLOWED_STATUSES.has(claimStatus)) {
      return {
        locked: true,
        severity: 'warning',
        message: `لا يمكن تنفيذ قرار جديد في الحالة الحالية (${claimStatus}).`
      };
    }

    return { locked: false, severity: 'info', message: '' };
  }, [claimStatus]);

  // CLAIM-REVIEW-SPLIT-2C: line decisions have their own, narrower lock than
  // the general review-action bar's `reviewLock` above.
  const lineDecisionsLocked = useMemo(() => !LINE_DECISION_ALLOWED_STATUSES.has(claimStatus), [claimStatus]);

  const hasRejectedServices = useMemo(() => {
    return Object.values(serviceDecisions).some((entry) => entry?.decision === SERVICE_DECISION.REJECT);
  }, [serviceDecisions]);

  // CLAIM-REVIEW-SPLIT-2C / DOCUMENTS-REVIEW-UX-1: persists one line's
  // decision to the server. A REJECTED decision now genuinely recomputes
  // that line's refusedAmount/companyShare server-side (see
  // ClaimService.submitLineDecision), so the claim is refetched afterward to
  // reflect the real, backend-computed split — this is no longer a purely
  // cosmetic action.
  const persistServiceDecision = useCallback(
    async (service, decision, options = {}) => {
      if (!service?.id) return; // no backend line id to persist against
      const { reason, manualRefusedAmount, reviewerNotes, silent } = options;
      setSavingServiceKey(service.serviceKey);
      try {
        await claimsService.submitLineDecision(id, service.id, {
          decision: LOCAL_DECISION_TO_SERVER[decision],
          reason: decision === SERVICE_DECISION.APPROVE ? undefined : reason,
          reviewerNotes: reviewerNotes || undefined,
          manualRefusedAmount: decision === SERVICE_DECISION.REJECT ? manualRefusedAmount : undefined
        });
        if (!silent) {
          enqueueSnackbar('تم حفظ قرار المراجعة', { variant: 'success' });
          fetchClaim();
        }
      } catch (error) {
        const message = error?.response?.data?.messageAr || error?.userMessage || 'تعذر حفظ قرار المراجعة';
        enqueueSnackbar(message, { variant: 'error' });
        // Revert local UI state back to the last known server truth.
        fetchClaim();
      } finally {
        setSavingServiceKey(null);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [id, enqueueSnackbar]
  );

  const handleServiceDecision = useCallback(
    (serviceKey, decision, options = {}) => {
      const previousEntry = serviceDecisions[serviceKey] || {};
      const reason =
        decision === SERVICE_DECISION.REJECT || decision === SERVICE_DECISION.CLARIFY
          ? options.reason || previousEntry.reason || REJECTION_REASONS[0]
          : '';
      const reviewerNotes = options.reviewerNotes ?? previousEntry.reviewerNotes ?? '';
      const manualRefusedAmount = decision === SERVICE_DECISION.REJECT ? options.manualRefusedAmount : undefined;

      setServiceDecisions((previous) => ({
        ...previous,
        [serviceKey]: { decision, reason, reviewerNotes, manualRefusedAmount }
      }));
      const service = normalizedClaim?.services?.find((entry) => entry.serviceKey === serviceKey);
      if (service) {
        persistServiceDecision(service, decision, { reason, manualRefusedAmount, reviewerNotes });
      }
    },
    [serviceDecisions, normalizedClaim?.services, persistServiceDecision]
  );

  const handleServiceReason = useCallback(
    (serviceKey, reason) => {
      const decision = serviceDecisions[serviceKey]?.decision || SERVICE_DECISION.REJECT;
      const reviewerNotes = serviceDecisions[serviceKey]?.reviewerNotes;
      const manualRefusedAmount = serviceDecisions[serviceKey]?.manualRefusedAmount;
      setServiceDecisions((previous) => ({
        ...previous,
        [serviceKey]: { ...(previous[serviceKey] || { decision: SERVICE_DECISION.REJECT }), decision, reason }
      }));
      const service = normalizedClaim?.services?.find((entry) => entry.serviceKey === serviceKey);
      if (service) {
        persistServiceDecision(service, decision, { reason, manualRefusedAmount, reviewerNotes });
      }
    },
    [serviceDecisions, normalizedClaim?.services, persistServiceDecision]
  );

  // DOCUMENTS-REVIEW-UX-1: free-text note explaining a CLARIFY (or REJECT)
  // decision to the provider — saved onBlur (not per-keystroke) to avoid a
  // network call on every character typed.
  const handleServiceNotes = useCallback(
    (serviceKey, reviewerNotes) => {
      const decision = serviceDecisions[serviceKey]?.decision || SERVICE_DECISION.CLARIFY;
      const reason = serviceDecisions[serviceKey]?.reason;
      const manualRefusedAmount = serviceDecisions[serviceKey]?.manualRefusedAmount;
      setServiceDecisions((previous) => ({
        ...previous,
        [serviceKey]: { ...(previous[serviceKey] || {}), decision, reason, reviewerNotes }
      }));
      const service = normalizedClaim?.services?.find((entry) => entry.serviceKey === serviceKey);
      if (service) {
        persistServiceDecision(service, decision, { reason, manualRefusedAmount, reviewerNotes });
      }
    },
    [serviceDecisions, normalizedClaim?.services, persistServiceDecision]
  );

  // DOCUMENTS-REVIEW-UX-1: bulk-approve every service line in one action, for
  // the common case where the whole claim is being accepted as submitted.
  const handleApproveAll = useCallback(async () => {
    if (!normalizedClaim?.services?.length) return;
    const pending = normalizedClaim.services.filter(
      (service) => serviceDecisions[service.serviceKey]?.decision !== SERVICE_DECISION.APPROVE
    );
    if (!pending.length) return;

    setServiceDecisions((previous) => {
      const next = { ...previous };
      pending.forEach((service) => {
        next[service.serviceKey] = { decision: SERVICE_DECISION.APPROVE, reason: '', reviewerNotes: '' };
      });
      return next;
    });

    for (const service of pending) {
      // eslint-disable-next-line no-await-in-loop
      await persistServiceDecision(service, SERVICE_DECISION.APPROVE, { silent: true });
    }
    enqueueSnackbar(`تم اعتماد ${pending.length} خدمة`, { variant: 'success' });
    fetchClaim();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [normalizedClaim?.services, serviceDecisions, persistServiceDecision, enqueueSnackbar]);

  const handleServiceRowClick = useCallback((service) => {
    setActiveServiceKey(service.serviceKey);
  }, []);

  const handleApprove = useCallback(
    async (notes) => {
      setSubmitting(true);
      try {
        if (reviewLock.locked) {
          enqueueSnackbar(reviewLock.message || 'لا يمكن تنفيذ الموافقة في الحالة الحالية.', { variant: 'warning' });
          return;
        }

        if (!normalizedClaim?.services?.length) {
          enqueueSnackbar('لا توجد خدمات في هذه المطالبة', { variant: 'warning' });
          return;
        }

        // CLAIM-REVIEW-FOLLOWUP-1: a claim where every line was rejected is a
        // legitimate outcome (net approved = 0) — the previous guard blocked
        // finishing the review in that case, forcing reviewers to use the
        // now-removed whole-claim "رفض" button instead.
        await claimsService.approve(id, {
          notes: notes?.trim() || `تمت مراجعة ${selectedServicesCount} خدمة معتمدة من ${normalizedClaim.services.length}`,
          useSystemCalculation: true
        });

        localStorage.removeItem(draftStorageKey);
        enqueueSnackbar('تم إرسال الموافقة وجاري المعالجة', { variant: 'success' });
        navigate('/claims/review');
      } catch (error) {
        console.error('Error approving claim:', error);
        enqueueSnackbar(error.message || 'فشل في الموافقة على المطالبة', { variant: 'error' });
      } finally {
        setSubmitting(false);
      }
    },
    [id, reviewLock, selectedServicesCount, normalizedClaim?.services, draftStorageKey, navigate, enqueueSnackbar]
  );

  const handleRequestInfo = useCallback(
    async (notes) => {
      setSubmitting(true);
      try {
        if (reviewLock.locked) {
          enqueueSnackbar(reviewLock.message || 'لا يمكن طلب معلومات إضافية في الحالة الحالية.', { variant: 'warning' });
          return;
        }

        await ensureClaimUnderReview();

        await claimsService.returnForInfo(id, {
          reason: notes && notes.trim().length >= 10 ? notes.trim() : 'يرجى استكمال البيانات الطبية والمستندات المطلوبة'
        });

        localStorage.removeItem(draftStorageKey);
        enqueueSnackbar('تم طلب معلومات إضافية', { variant: 'info' });
        navigate('/claims/review');
      } catch (error) {
        console.error('Error requesting info:', error);
        enqueueSnackbar(error.message || 'فشل في طلب المعلومات', { variant: 'error' });
      } finally {
        setSubmitting(false);
      }
    },
    [id, reviewLock, ensureClaimUnderReview, draftStorageKey, navigate, enqueueSnackbar]
  );

  // CLAIM-REVIEW-FOLLOWUP-1: replaces the three separate bottom buttons
  // (موافقة / رفض / طلب معلومات) with a single "تمت المراجعة" action —
  // per-service approve/reject/clarify decisions are already made in the
  // table itself, so the bottom bar's only remaining job is to finalize
  // whatever was decided per line. If any line is still marked "استيضاح"
  // (CLARIFY), finishing the review means asking the provider for that
  // clarification (the claim goes back to them); otherwise it means
  // submitting the approve/reject split already recorded on the lines.
  const handleFinishReview = useCallback(async () => {
    if (clarifyServicesCount > 0) {
      const clarifyNotes = (normalizedClaim?.services || [])
        .filter((service) => serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.CLARIFY)
        .map((service) => {
          const entry = serviceDecisions[service.serviceKey];
          const detail = entry?.reviewerNotes || entry?.reason || '';
          return detail ? `${service.serviceName}: ${detail}` : service.serviceName;
        });
      const composed = [medicalNotes?.trim(), ...clarifyNotes].filter(Boolean).join(' | ');
      await handleRequestInfo(composed);
      return;
    }
    await handleApprove(medicalNotes);
  }, [clarifyServicesCount, normalizedClaim?.services, serviceDecisions, medicalNotes, handleRequestInfo, handleApprove]);

  const handleSendChatMessage = useCallback(() => {
    const text = chatInput.trim();
    if (!text) return;

    const message = {
      id: `${Date.now()}`,
      text,
      senderName: currentUserName,
      senderRole: currentUserRole,
      createdAt: new Date().toISOString()
    };

    setChatMessages((previousMessages) => {
      const updatedMessages = [...previousMessages, message];
      localStorage.setItem(chatStorageKey, JSON.stringify(updatedMessages));
      return updatedMessages;
    });

    setChatInput('');
  }, [chatInput, chatStorageKey, currentUserName, currentUserRole]);

  const handleSaveDraftNow = useCallback(() => {
    const payload = {
      notes: medicalNotes || '',
      updatedAt: new Date().toISOString()
    };
    localStorage.setItem(draftStorageKey, JSON.stringify(payload));
    setDraftSavedAt(payload.updatedAt);
    enqueueSnackbar('تم حفظ المسودة والعودة إلى قائمة المطالبات', { variant: 'success' });
    navigate('/claims/review');
  }, [medicalNotes, draftStorageKey, navigate, enqueueSnackbar]);

  const handleRestoreDraft = useCallback(() => {
    const draftPayload = localStorage.getItem(draftStorageKey);
    if (!draftPayload) {
      enqueueSnackbar('لا توجد مسودة محفوظة', { variant: 'info' });
      return;
    }

    try {
      const parsedDraft = JSON.parse(draftPayload);
      setMedicalNotes(parsedDraft?.notes || '');
      setDraftSavedAt(parsedDraft?.updatedAt || null);
      enqueueSnackbar('تم استعادة المسودة', { variant: 'success' });
    } catch (error) {
      console.error('Failed to restore draft:', error);
      enqueueSnackbar('تعذر استعادة المسودة', { variant: 'error' });
    }
  }, [draftStorageKey, enqueueSnackbar]);

  const handleClearDraft = useCallback(() => {
    localStorage.removeItem(draftStorageKey);
    setDraftSavedAt(null);
    enqueueSnackbar('تم مسح المسودة', { variant: 'info' });
  }, [draftStorageKey, enqueueSnackbar]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '25.0rem' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!normalizedClaim) {
    return <Alert severity="error">لم يتم العثور على المطالبة</Alert>;
  }

  return (
    <Box
      sx={{
        bgcolor: 'grey.50',
        minHeight: '100vh',
        fontFamily: 'Tajawal, IBM Plex Sans Arabic, Noto Sans Arabic, sans-serif'
      }}
    >
      {/* CLAIM-REVIEW-WORKSPACE-LOVABLE-POLISH-1 (post-review compact layout
          correction): the app-level breadcrumb header ("الرئيسية / المطالبات
          / #رقم") was dropped for this page — it duplicated the claim number
          already shown in the compact context card below and added a full
          extra header row of vertical space on a page whose whole point is
          fitting near one screen. The context card's own top row now covers
          navigation (a single "الفئات" grid-icon link back to the system,
          print, back-to-inbox, prev/next). */}

      {/* PROVIDER-PORTAL-REVIEW-ROUTING-2: who created/submitted/reviewed this claim */}
      {(normalizedClaim.createdBy || normalizedClaim.submittedBy || normalizedClaim.reviewedBy) && (
        <Stack direction="row" spacing={1.5} flexWrap="wrap" sx={{ px: '1.5rem', pt: '0.75rem' }} alignItems="center">
          {normalizedClaim.createdBy && (
            <Chip
              size="small"
              variant="outlined"
              label={
                <Typography variant="caption">
                  أنشأها: <strong>{normalizedClaim.createdBy}</strong>
                  {normalizedClaim.submissionChannel === 'PROVIDER_PORTAL' ? ' (بوابة مقدم الخدمة)' : ''}
                </Typography>
              }
            />
          )}
          {normalizedClaim.submittedBy && (
            <Chip size="small" variant="outlined" label={<Typography variant="caption">أرسلها للمراجعة: <strong>{normalizedClaim.submittedBy}</strong></Typography>} />
          )}
          {normalizedClaim.reviewedBy && (
            <Chip size="small" variant="outlined" color="primary" label={<Typography variant="caption">راجعها/قرر بشأنها: <strong>{normalizedClaim.reviewedBy}</strong></Typography>} />
          )}
        </Stack>
      )}

      {/* REVIEW-WORKSPACE-TABS-1: single full-width column — the services
          table is the page's main focus (matches the attached reference),
          with documents/conversation/history moved into one tabbed card
          below it instead of a side column that squeezed the table's width. */}
      <Box sx={{ maxWidth: '87.5rem', mx: 'auto', width: '100%', px: '1.5rem', pt: '0.75rem', pb: '6.0rem' }}>
        <Box sx={{ mb: '1.0rem' }}>
          <ClaimReviewContextHeader
            normalizedClaim={normalizedClaim}
            navigate={navigate}
            reviewLock={reviewLock}
            draftBanner={null}
            onNavigatePrev={() => navigateToClaim(prevClaimId)}
            onNavigateNext={() => navigateToClaim(nextClaimId)}
            hasPrev={!!prevClaimId}
            hasNext={!!nextClaimId}
          />
        </Box>

        <ClaimReviewFinancialSummary
          normalizedClaim={normalizedClaim}
          selectedApprovedAmount={selectedApprovedAmount}
          selectedServicesCount={selectedServicesCount}
          rejectedCount={rejectedServicesCount}
          clarifyCount={clarifyServicesCount}
          memberCoverage={memberCoverage}
        />

        <Stack spacing={1.25}>
          <ClaimReviewServiceLinesPanel
            services={normalizedClaim.services}
            serviceDecisions={serviceDecisions}
            activeServiceKey={activeServiceKey}
            reviewLock={reviewLock}
            submitting={submitting}
            selectedServicesCount={selectedServicesCount}
            savingServiceKey={savingServiceKey}
            lineDecisionsLocked={lineDecisionsLocked}
            onRowClick={handleServiceRowClick}
            onDecisionChange={handleServiceDecision}
            onReasonChange={handleServiceReason}
            onNotesChange={handleServiceNotes}
            onApproveAll={handleApproveAll}
          />

          <ClaimReviewDecisionPanel
            visible={hasRejectedServices || normalizedClaim.status === 'REJECTED'}
            medicalNotes={medicalNotes}
            onNotesChange={setMedicalNotes}
          />

          <ClaimReviewBottomTabs
            attachments={attachments}
            claimId={id}
            onRefreshAttachments={fetchAttachments}
            chatMessages={chatMessages}
            chatInput={chatInput}
            onChatInputChange={setChatInput}
            onSendChatMessage={handleSendChatMessage}
          />
        </Stack>
      </Box>

      <ClaimReviewActionBar
        selectedApprovedAmount={selectedApprovedAmount}
        reviewLock={reviewLock}
        submitting={submitting}
        selectedServicesCount={selectedServicesCount}
        hasClarifyServices={clarifyServicesCount > 0}
        draftSavedAt={draftSavedAt}
        onSaveDraftNow={handleSaveDraftNow}
        onRestoreDraft={handleRestoreDraft}
        onClearDraft={handleClearDraft}
        onFinishReview={handleFinishReview}
      />
    </Box>
  );
};

/**
 * CLAIM-REVIEW-SPLIT-2A repair: wrap the workspace in a page-level error
 * boundary (same mechanism already used elsewhere in the app, e.g. the
 * Dashboard layout) so any runtime error here renders a visible error card
 * with a retry option instead of leaving a blank screen with no feedback.
 */
const ClaimReviewWorkspace = () => (
  <PageErrorBoundary pageName="مراجعة المطالبة">
    <ClaimReviewWorkspaceInner />
  </PageErrorBoundary>
);

export default ClaimReviewWorkspace;
