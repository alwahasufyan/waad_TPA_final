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
import { useParams, useNavigate } from 'react-router-dom';
import { Alert, Box, CircularProgress, Stack } from '@mui/material';
import { Receipt as ClaimIcon } from '@mui/icons-material';
import { useSnackbar } from 'notistack';

import { ModernPageHeader } from 'components/tba';
import { MedicalReviewLayout } from 'components/medical-review';
import PageErrorBoundary from 'components/SafeStates/PageErrorBoundary';

import { claimsService } from 'services/api';
import { getClaimAttachments, downloadClaimAttachment } from 'services/api/files.service';

import ClaimReviewContextHeader from './components/ClaimReviewContextHeader';
import ClaimReviewServiceLinesPanel, { SERVICE_DECISION, REJECTION_REASONS } from './components/ClaimReviewServiceLinesPanel';
import ClaimReviewFinancialSummary from './components/ClaimReviewFinancialSummary';
import ClaimReviewAttachmentsViewer from './components/ClaimReviewAttachmentsViewer';
import ClaimReviewNotesPanel from './components/ClaimReviewNotesPanel';
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

const normalizeText = (value) =>
  `${value || ''}`
    .toLowerCase()
    .replace(/[ً-ٟ]/g, '')
    .replace(/[^؀-ۿa-z0-9\s]/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();

const ClaimReviewWorkspaceInner = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();

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
  const [selectedAttachmentId, setSelectedAttachmentId] = useState(null);

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
      memberName: claim.memberName || claim.memberFullName || claim.member?.fullName || claim.member?.name,
      memberCivilId: claim.memberNationalNumber || claim.memberCivilId || claim.member?.nationalId || claim.member?.civilId,
      memberCardNumber: claim.memberCardNumber || claim.member?.cardNumber,
      memberPhone: claim.memberPhone || claim.member?.phone,
      employerName: claim.employerName || claim.employer?.name,
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
      preApprovalReferenceNumber: claim.preApprovalReferenceNumber
    };
  }, [claim, id]);

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
        nextDecisions[service.serviceKey] = {
          decision: persistedLocalDecision,
          reason: service.rejectionReason || ''
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

  const selectedApprovedAmount = useMemo(() => {
    if (!normalizedClaim?.services?.length) {
      return 0;
    }

    return normalizedClaim.services
      .filter((service) => serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.APPROVE)
      .reduce((sum, service) => sum + Number(service.totalAmount || 0), 0);
  }, [normalizedClaim?.services, serviceDecisions]);

  const selectedServicesCount = useMemo(() => {
    if (!normalizedClaim?.services?.length) return 0;
    return normalizedClaim.services.filter((service) => serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.APPROVE).length;
  }, [normalizedClaim?.services, serviceDecisions]);

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

  const resolveLinkedAttachmentId = useCallback(
    (service) => {
      if (!attachments.length || !service) return null;

      const serviceCode = normalizeText(service.serviceCode);
      const serviceName = normalizeText(service.serviceName);

      const matched = attachments.find((attachment) => {
        const candidate = normalizeText(`${attachment.fileName || ''} ${attachment.name || ''}`);
        return (serviceCode && candidate.includes(serviceCode)) || (serviceName && candidate.includes(serviceName));
      });

      return matched?.id || null;
    },
    [attachments]
  );

  // CLAIM-REVIEW-SPLIT-2C: persists one line's decision to the server. Never
  // touches claim-level financial fields — the backend endpoint this calls
  // only saves the ClaimLine row (see ClaimService.submitLineDecision).
  const persistServiceDecision = useCallback(
    async (service, decision, reason) => {
      if (!service?.id) return; // no backend line id to persist against
      setSavingServiceKey(service.serviceKey);
      try {
        await claimsService.submitLineDecision(id, service.id, {
          decision: LOCAL_DECISION_TO_SERVER[decision],
          reason: decision === SERVICE_DECISION.APPROVE ? undefined : reason,
          reviewerNotes: undefined
        });
        enqueueSnackbar('تم حفظ قرار المراجعة', { variant: 'success' });
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
    (serviceKey, decision) => {
      const reason =
        decision === SERVICE_DECISION.REJECT || decision === SERVICE_DECISION.CLARIFY
          ? serviceDecisions[serviceKey]?.reason || REJECTION_REASONS[0]
          : '';
      setServiceDecisions((previous) => ({
        ...previous,
        [serviceKey]: { decision, reason }
      }));
      const service = normalizedClaim?.services?.find((entry) => entry.serviceKey === serviceKey);
      if (service) {
        persistServiceDecision(service, decision, reason);
      }
    },
    [serviceDecisions, normalizedClaim?.services, persistServiceDecision]
  );

  const handleServiceReason = useCallback(
    (serviceKey, reason) => {
      const decision = serviceDecisions[serviceKey]?.decision || SERVICE_DECISION.REJECT;
      setServiceDecisions((previous) => ({
        ...previous,
        [serviceKey]: { ...(previous[serviceKey] || { decision: SERVICE_DECISION.REJECT }), decision, reason }
      }));
      const service = normalizedClaim?.services?.find((entry) => entry.serviceKey === serviceKey);
      if (service) {
        persistServiceDecision(service, decision, reason);
      }
    },
    [serviceDecisions, normalizedClaim?.services, persistServiceDecision]
  );

  const handleServiceRowClick = useCallback(
    (service) => {
      setActiveServiceKey(service.serviceKey);
      const linkedId = resolveLinkedAttachmentId(service);
      if (linkedId) {
        setSelectedAttachmentId(linkedId);
      }
    },
    [resolveLinkedAttachmentId]
  );

  const handleApprove = useCallback(
    async (notes) => {
      setSubmitting(true);
      try {
        if (reviewLock.locked) {
          enqueueSnackbar(reviewLock.message || 'لا يمكن تنفيذ الموافقة في الحالة الحالية.', { variant: 'warning' });
          return;
        }

        if (!selectedServicesCount || selectedApprovedAmount <= 0) {
          enqueueSnackbar('يجب تحديد خدمة واحدة على الأقل للموافقة', { variant: 'warning' });
          return;
        }

        await claimsService.approve(id, {
          notes: notes?.trim() || `تمت الموافقة على ${selectedServicesCount} خدمة`,
          useSystemCalculation: true
        });

        localStorage.removeItem(draftStorageKey);
        enqueueSnackbar('تم إرسال الموافقة وجاري المعالجة', { variant: 'success' });
        navigate('/claims');
      } catch (error) {
        console.error('Error approving claim:', error);
        enqueueSnackbar(error.message || 'فشل في الموافقة على المطالبة', { variant: 'error' });
      } finally {
        setSubmitting(false);
      }
    },
    [id, reviewLock, selectedServicesCount, selectedApprovedAmount, draftStorageKey, navigate, enqueueSnackbar]
  );

  const handleReject = useCallback(
    async (notes) => {
      setSubmitting(true);
      try {
        if (reviewLock.locked) {
          enqueueSnackbar(reviewLock.message || 'لا يمكن تنفيذ الرفض في الحالة الحالية.', { variant: 'warning' });
          return;
        }

        const rejectedReasons = (normalizedClaim?.services || [])
          .filter((service) => serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.REJECT)
          .map((service) => `${service.serviceName}: ${serviceDecisions[service.serviceKey]?.reason || 'مرفوضة'}`);

        const composedReason = [notes?.trim(), ...rejectedReasons].filter(Boolean).join(' | ');

        await claimsService.reject(id, {
          rejectionReason: composedReason.length >= 10 ? composedReason : 'رفض المطالبة بعد المراجعة الطبية'
        });

        localStorage.removeItem(draftStorageKey);
        enqueueSnackbar('تم رفض المطالبة', { variant: 'info' });
        navigate('/claims');
      } catch (error) {
        console.error('Error rejecting claim:', error);
        enqueueSnackbar(error.message || 'فشل في رفض المطالبة', { variant: 'error' });
      } finally {
        setSubmitting(false);
      }
    },
    [id, reviewLock, normalizedClaim?.services, serviceDecisions, draftStorageKey, navigate, enqueueSnackbar]
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
        navigate('/claims');
      } catch (error) {
        console.error('Error requesting info:', error);
        enqueueSnackbar(error.message || 'فشل في طلب المعلومات', { variant: 'error' });
      } finally {
        setSubmitting(false);
      }
    },
    [id, reviewLock, ensureClaimUnderReview, draftStorageKey, navigate, enqueueSnackbar]
  );

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
    navigate('/claims');
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

  const handleDownload = useCallback(
    async (attachment) => {
      try {
        const blob = await downloadClaimAttachment(id, attachment.id);
        const fileName = attachment?.fileName || `attachment-${attachment?.id || 'file'}`;
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
      } catch (error) {
        console.error('Error downloading attachment:', error);
        enqueueSnackbar('فشل في تحميل الملف', { variant: 'error' });
      }
    },
    [id, enqueueSnackbar]
  );

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

  const centerPanel = (
    <Box sx={{ maxWidth: '65.0rem', mx: 'auto', width: '100%', pb: '7.0rem' }}>
      <Stack spacing={1.5}>
        <ClaimReviewContextHeader
          id={id}
          normalizedClaim={normalizedClaim}
          navigate={navigate}
          reviewLock={reviewLock}
          draftBanner={null}
        />

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
        />

        <ClaimReviewFinancialSummary
          normalizedClaim={normalizedClaim}
          selectedApprovedAmount={selectedApprovedAmount}
          selectedServicesCount={selectedServicesCount}
        />

        <ClaimReviewNotesPanel
          draftSavedAt={draftSavedAt}
          onSaveDraftNow={handleSaveDraftNow}
          onRestoreDraft={handleRestoreDraft}
          onClearDraft={handleClearDraft}
          submitting={submitting}
          chatMessages={chatMessages}
          chatInput={chatInput}
          onChatInputChange={setChatInput}
          onSendChatMessage={handleSendChatMessage}
        />

        <ClaimReviewDecisionPanel
          visible={hasRejectedServices || normalizedClaim.status === 'REJECTED'}
          medicalNotes={medicalNotes}
          onNotesChange={setMedicalNotes}
        />
      </Stack>
    </Box>
  );

  return (
    <Box
      sx={{
        bgcolor: 'grey.50',
        minHeight: '100vh',
        fontFamily: 'Tajawal, IBM Plex Sans Arabic, Noto Sans Arabic, sans-serif'
      }}
    >
      <ModernPageHeader
        title={`مطالبة رقم ${normalizedClaim.claimNumber}`}
        subtitle="مراجعة طبية"
        icon={ClaimIcon}
        breadcrumbs={[{ label: 'الرئيسية', href: '/' }, { label: 'المطالبات', href: '/claims' }, { label: `#${normalizedClaim.claimNumber}` }]}
      />

      <MedicalReviewLayout
        leftPanel={
          <ClaimReviewAttachmentsViewer
            attachments={attachments}
            onDownload={handleDownload}
            onRefresh={fetchAttachments}
            selectedAttachmentId={selectedAttachmentId}
            onSelectionChange={setSelectedAttachmentId}
          />
        }
        centerPanel={centerPanel}
        rightPanel={null}
        documentsCount={attachments.length}
        showLeftPanel={true}
        showRightPanel={false}
        collapsible={true}
      />

      <ClaimReviewActionBar
        selectedApprovedAmount={selectedApprovedAmount}
        reviewLock={reviewLock}
        submitting={submitting}
        selectedServicesCount={selectedServicesCount}
        onApprove={() => handleApprove(medicalNotes)}
        onReject={() => handleReject(medicalNotes)}
        onRequestInfo={() => handleRequestInfo(medicalNotes)}
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
