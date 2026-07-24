import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import { useTheme } from '@mui/material/styles';
import { useAuth } from 'contexts/AuthContext';
import axiosClient from 'utils/axios';
import { resolveApiErrorMessage } from 'utils/apiErrorMessage.mjs';
import { MEDICAL_COLORS } from 'themes/provider-theme';
import claimBatchesService from 'services/api/claim-batches.service';
import { LABELS, MAX_UPLOAD_SIZE_MB, MAX_UPLOAD_SIZE_BYTES, ALLOWED_FILE_EXTENSIONS } from '../constants';

/**
 * All claim-submission state, effects, and handlers — moved verbatim out of the
 * original ProviderClaimsSubmission.jsx (Stage 3A extraction). No behavior,
 * validation, payload, calculation, or API-call-order change. Presentational
 * components (in ./components) receive everything this hook returns as props.
 */
export function useProviderClaimSubmission() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();

  const parseNumericParam = useCallback(
    (stateValue, queryKey) => {
      const candidate = stateValue ?? searchParams.get(queryKey);
      if (candidate === null || candidate === undefined || candidate === '') return null;
      const parsed = Number(candidate);
      return Number.isNaN(parsed) ? null : parsed;
    },
    [searchParams]
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // THEME (MEDICAL THEME)
  // ═══════════════════════════════════════════════════════════════════════════
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const tableHeaderBg = isDark ? '#70c470ef' : MEDICAL_COLORS.primary.main;
  const tableHeaderColor = '#FFFFFF';

  // ═══════════════════════════════════════════════════════════════════════════
  // VISIT CONTEXT (FROM STATE OR URL PARAMS - supports refresh)
  // ═══════════════════════════════════════════════════════════════════════════
  const fromVisitLog = location.state?.fromVisitLog || searchParams.get('fromVisitLog') === 'true';
  const linkedVisitId = parseNumericParam(location.state?.visitId, 'visitId');
  const linkedMemberId = parseNumericParam(location.state?.memberId, 'memberId');
  const draftClaimId = parseNumericParam(location.state?.claimId, 'claimId');
  const linkedMemberName = location.state?.memberName || searchParams.get('memberName') || null;
  const linkedMemberCivilId = location.state?.memberCivilId || searchParams.get('memberCivilId') || null;
  const linkedMemberCardNumber = location.state?.memberCardNumber || searchParams.get('cardNumber') || null;
  const linkedEmployerName = location.state?.employerName || searchParams.get('employer') || null;
  const linkedMemberPhone = location.state?.memberPhone || searchParams.get('phone') || null;
  const linkedVisitDate = location.state?.visitDate || searchParams.get('visitDate') || null;
  const linkedVisitTime = location.state?.visitTime || searchParams.get('visitTime') || null;
  const linkedVisitType = location.state?.visitType || searchParams.get('visitType') || null;
  const linkedProviderName = location.state?.providerName || searchParams.get('providerName') || null;

  // SUPER_ADMIN check
  const isSuperAdmin = user?.roles?.includes('SUPER_ADMIN');

  // ARCHITECTURAL ENFORCEMENT: Block direct access (SUPER_ADMIN can bypass).
  // Editing an existing draft claim is explicitly allowed without visit params.
  const accessBlocked = !linkedVisitId && !draftClaimId && !isSuperAdmin;

  // Provider from user session
  const userProviderId = user?.providerId || null;
  const userProviderName = user?.providerName || null;

  // ═══════════════════════════════════════════════════════════════════════════
  // STATE
  // ═══════════════════════════════════════════════════════════════════════════
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitMode, setSubmitMode] = useState(null);
  const [attemptedSubmit, setAttemptedSubmit] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Visit details loaded from backend
  const [visitDetails, setVisitDetails] = useState(null);

  // Member remaining limit
  const [memberLimit, setMemberLimit] = useState(null);

  // Medical services from Provider Contract
  const [availableServices, setAvailableServices] = useState([]);
  const [loadingServices, setLoadingServices] = useState(false);

  // Medical Categories
  const [medicalCategories, setMedicalCategories] = useState([]);
  const [loadingCategories, setLoadingCategories] = useState(false);

  // Claim Lines
  const [claimLines, setClaimLines] = useState([]);
  const [lineIdCounter, setLineIdCounter] = useState(1);

  // Form Data
  // CLAIM-REVIEW-FOLLOWUP-1: preAuthorizationId removed — pre-authorizations
  // are handled exclusively on the dedicated Pre-Authorization page now;
  // services requiring one are blocked at selection time (see
  // handleServiceSelect), so a normal claim never needs to carry one.
  const [formData, setFormData] = useState({
    diagnosisCode: '',
    diagnosisDescription: '',
    doctorName: '',
    notes: ''
  });

  // Attachments State
  const [pendingFiles, setPendingFiles] = useState([]);
  const [existingAttachments, setExistingAttachments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [activeClaimId, setActiveClaimId] = useState(draftClaimId);
  const [draftLoaded, setDraftLoaded] = useState(false);
  const [autosaveStatus, setAutosaveStatus] = useState('idle');
  const [autosaveAt, setAutosaveAt] = useState(null);
  const [localDraftRestored, setLocalDraftRestored] = useState(false);
  const [providerChatMessages, setProviderChatMessages] = useState([]);
  const [providerChatInput, setProviderChatInput] = useState('');
  const [activeBatchId, setActiveBatchId] = useState(null);
  const [loadingBatch, setLoadingBatch] = useState(false);

  // Custom Service Addition States
  const [customServiceDialogOpen, setCustomServiceDialogOpen] = useState(false);
  const [activeLineIdForCustomService, setActiveLineIdForCustomService] = useState(null);
  const [customServiceData, setCustomServiceData] = useState({
    mainCategoryId: '',
    subCategoryId: '',
    serviceName: '',
    serviceCode: '',
    contractPrice: ''
  });
  const [customServiceError, setCustomServiceError] = useState(null);
  const [addingCustomService, setAddingCustomService] = useState(false);

  const normalizeId = useCallback((value) => {
    if (value === null || value === undefined || value === '') return null;
    const parsed = Number(value);
    return Number.isNaN(parsed) ? value : parsed;
  }, []);

  const normalizeText = useCallback((value) => (value || '').toString().trim().toLowerCase(), []);

  const handleOpenCustomServiceDialog = (lineId) => {
    const line = claimLines.find((l) => l.id === lineId);
    let initialMainCategoryId = '';
    let initialSubCategoryId = '';

    if (line && line.medicalCategoryId) {
      const category = medicalCategories.find((c) => normalizeId(c.id) === normalizeId(line.medicalCategoryId));
      if (category) {
        if (category.parentId) {
          initialMainCategoryId = normalizeId(category.parentId);
          initialSubCategoryId = normalizeId(category.id);
        } else {
          initialMainCategoryId = normalizeId(category.id);
        }
      }
    }

    setCustomServiceData({
      mainCategoryId: initialMainCategoryId,
      subCategoryId: initialSubCategoryId,
      serviceName: '',
      serviceCode: '',
      contractPrice: ''
    });
    setCustomServiceError(null);
    setActiveLineIdForCustomService(lineId);
    setCustomServiceDialogOpen(true);
  };

  const handleCloseCustomServiceDialog = () => {
    setCustomServiceDialogOpen(false);
    setActiveLineIdForCustomService(null);
  };

  const handleCustomServiceDataChange = (field, value) => {
    setCustomServiceData((prev) => {
      const next = { ...prev, [field]: value };
      if (field === 'mainCategoryId') {
        next.subCategoryId = '';
      }
      return next;
    });
  };

  const handleSubmitCustomService = async () => {
    setCustomServiceError(null);

    // Validation
    if (!customServiceData.mainCategoryId) {
      setCustomServiceError('يرجير اختيار التصنيف الرئيسي');
      return;
    }
    if (!customServiceData.serviceName.trim()) {
      setCustomServiceError('يرجى إدخال اسم الخدمة');
      return;
    }
    const priceNum = parseFloat(customServiceData.contractPrice);
    if (isNaN(priceNum) || priceNum <= 0) {
      setCustomServiceError('يرجى إدخال سعر تعاقدي صحيح أكبر من صفر');
      return;
    }

    setAddingCustomService(true);
    try {
      // Determine final category id (subCategory if chosen, else mainCategory)
      const finalCategoryId = customServiceData.subCategoryId || customServiceData.mainCategoryId;

      // Auto-generate service code if not provided
      const finalServiceCode = customServiceData.serviceCode.trim() || `SRV-${Date.now().toString().slice(-6)}`;

      const payload = {
        serviceName: customServiceData.serviceName.trim(),
        serviceCode: finalServiceCode,
        medicalCategoryId: normalizeId(finalCategoryId),
        contractPrice: priceNum,
        basePrice: priceNum,
        unit: 'service',
        currency: 'LYD'
      };

      const response = await axiosClient.post('/provider/my-contract/pricing', payload);
      const createdItem = response.data?.data || response.data;

      // Map the created item to match client-side service DTO structure.
      // PROVIDER-PORTAL-DATA-1: pricingItemId is the newly-created pricing item's own id
      // (always present); medicalServiceId is only set if the backend genuinely linked a
      // real catalog entry (rare for a freshly-added custom service) — never substitute
      // one for the other.
      const newPricingItemId = normalizeId(createdItem.pricingItemId ?? createdItem.id);
      const newMedicalServiceId = normalizeId(createdItem.medicalServiceId) || null;
      const newServiceId = newPricingItemId ?? newMedicalServiceId;
      const matchingCategory = medicalCategories.find((c) => normalizeId(c.id) === normalizeId(finalCategoryId)) || null;

      const newServiceObject = {
        id: newServiceId,
        medicalServiceId: newMedicalServiceId,
        pricingItemId: newPricingItemId,
        code: finalServiceCode,
        name: payload.serviceName,
        categoryId: normalizeId(finalCategoryId),
        category: matchingCategory ? matchingCategory.name : '',
        categoryCode: matchingCategory ? matchingCategory.code : '',
        requiresPA: false,
        price: priceNum,
        basePrice: priceNum,
        hasContract: true
      };

      setAvailableServices((prev) => {
        if (prev.some((s) => normalizeId(s.id) === newServiceId)) return prev;
        return [...prev, newServiceObject];
      });

      // Update the active claim line to select this newly added service
      if (activeLineIdForCustomService) {
        setClaimLines((prev) =>
          prev.map((line) => {
            if (line.id !== activeLineIdForCustomService) return line;

            const nextCategoryServices = [
              ...(line.filteredServices || []).filter((s) => normalizeId(s.id) !== newServiceId),
              newServiceObject
            ];

            return {
              ...line,
              medicalCategoryId: normalizeId(finalCategoryId),
              medicalCategoryName: matchingCategory ? matchingCategory.name : '',
              medicalServiceId: newMedicalServiceId,
              pricingItemId: newPricingItemId,
              serviceName: payload.serviceName,
              serviceCode: finalServiceCode,
              unitPrice: priceNum,
              hasContract: true,
              filteredServices: nextCategoryServices,
              priceError: null,
              requiresPA: false
            };
          })
        );
      }

      setCustomServiceDialogOpen(false);
      // Trigger background refetch to be absolutely sync'd
      fetchAvailableServices();
    } catch (err) {
      console.error('Failed to add custom service pricing:', err);
      setCustomServiceError(
        resolveApiErrorMessage(err?.response?.data, 'فشل في حفظ الخدمة الجديدة في قائمة أسعارك. تأكد من صحة البيانات.')
      );
    } finally {
      setAddingCustomService(false);
    }
  };

  const providerChatStorageKey = useMemo(
    () => `provider-claim-chat-${activeClaimId || linkedVisitId || 'new'}`,
    [activeClaimId, linkedVisitId]
  );

  const localDraftStorageKey = useMemo(
    () => `provider-claim-local-draft-${activeClaimId || draftClaimId || linkedVisitId || 'new'}`,
    [activeClaimId, draftClaimId, linkedVisitId]
  );

  const providerSenderName = user?.fullName || user?.name || user?.username || 'مقدم الخدمة';

  useEffect(() => {
    try {
      const existingChat = localStorage.getItem(providerChatStorageKey);
      if (existingChat) {
        const parsed = JSON.parse(existingChat);
        setProviderChatMessages(Array.isArray(parsed) ? parsed : []);
      } else {
        setProviderChatMessages([]);
      }
    } catch (error) {
      console.warn('Failed to parse provider chat history:', error);
      setProviderChatMessages([]);
    }
  }, [providerChatStorageKey]);

  const handleSendProviderChatMessage = useCallback(() => {
    const text = providerChatInput.trim();
    if (!text) return;

    const message = {
      id: `${Date.now()}`,
      text,
      senderName: providerSenderName,
      senderRole: 'PROVIDER',
      createdAt: new Date().toISOString()
    };

    setProviderChatMessages((previousMessages) => {
      const updatedMessages = [...previousMessages, message];
      localStorage.setItem(providerChatStorageKey, JSON.stringify(updatedMessages));
      return updatedMessages;
    });

    setProviderChatInput('');
  }, [providerChatInput, providerSenderName, providerChatStorageKey]);

  const doesServiceMatchCategory = useCallback(
    (service, category) => {
      if (!service || !category) return false;

      const serviceCategoryId = normalizeId(service.categoryId || service.serviceCategoryId || service.medicalCategoryId);
      const selectedCategoryId = normalizeId(category.id);
      const byId = serviceCategoryId !== null && selectedCategoryId !== null && serviceCategoryId === selectedCategoryId;

      const serviceCategoryCode = normalizeText(service.categoryCode || service.category);
      const selectedCategoryCode = normalizeText(category.code);
      const byCode = !!serviceCategoryCode && !!selectedCategoryCode && serviceCategoryCode === selectedCategoryCode;

      const serviceCategoryName = normalizeText(service.categoryName || service.category);
      const selectedCategoryName = normalizeText(category.name);
      const byName = !!serviceCategoryName && !!selectedCategoryName && serviceCategoryName === selectedCategoryName;

      return byId || byCode || byName;
    },
    [normalizeId, normalizeText]
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // INITIALIZATION
  // ═══════════════════════════════════════════════════════════════════════════
  useEffect(() => {
    if (linkedVisitId && !accessBlocked) {
      initializePage();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [linkedVisitId, accessBlocked]);

  useEffect(() => {
    if (!accessBlocked && draftClaimId && !draftLoaded && !loadingServices && !loadingCategories) {
      loadDraftClaim();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessBlocked, draftClaimId, draftLoaded, loadingServices, loadingCategories]);

  useEffect(() => {
    if (accessBlocked || draftClaimId || localDraftRestored || loadingServices || loadingCategories) return;

    try {
      const raw = localStorage.getItem(localDraftStorageKey);
      if (!raw) {
        setLocalDraftRestored(true);
        return;
      }

      const parsed = JSON.parse(raw);

      if (parsed?.formData) {
        setFormData((prev) => ({ ...prev, ...parsed.formData }));
      }

      if (Array.isArray(parsed?.claimLines) && parsed.claimLines.length > 0) {
        setClaimLines(parsed.claimLines);
        const maxId = parsed.claimLines.reduce((max, line) => Math.max(max, Number(line?.id || 0)), 0);
        setLineIdCounter(Math.max(maxId + 1, 1));
      }

      if (parsed?.savedAt) {
        setAutosaveAt(parsed.savedAt);
        setAutosaveStatus('saved');
      }
    } catch (restoreError) {
      console.warn('Failed to restore local provider claim draft:', restoreError);
    } finally {
      setLocalDraftRestored(true);
    }
  }, [accessBlocked, draftClaimId, localDraftRestored, loadingServices, loadingCategories, localDraftStorageKey]);

  useEffect(() => {
    if (accessBlocked || !localDraftRestored || submitting || success) return;

    const hasDraftContent =
      claimLines.length > 0 || !!formData.diagnosisCode || !!formData.diagnosisDescription || !!formData.notes;

    if (!hasDraftContent) return;

    setAutosaveStatus('saving');

    const timer = setTimeout(() => {
      try {
        const payload = {
          formData,
          claimLines,
          visitId: linkedVisitId,
          memberId: linkedMemberId,
          savedAt: new Date().toISOString()
        };
        localStorage.setItem(localDraftStorageKey, JSON.stringify(payload));
        setAutosaveAt(payload.savedAt);
        setAutosaveStatus('saved');
      } catch (saveError) {
        console.warn('Failed to autosave provider claim draft:', saveError);
        setAutosaveStatus('error');
      }
    }, 1200);

    return () => clearTimeout(timer);
  }, [accessBlocked, localDraftRestored, submitting, success, claimLines, formData, linkedVisitId, linkedMemberId, localDraftStorageKey]);

  const initializePage = async () => {
    setLoading(true);
    try {
      // 1. Fetch visit details FIRST to get employerId for batching
      const visitData = await fetchVisitDetails();

      // 2. Fetch other resources in parallel
      const results = await Promise.allSettled([
        fetchAvailableServices(),
        fetchMemberLimit(),
        fetchMedicalCategories(),
        ensureActiveBatch(visitData?.employerId)
      ]);

      results.forEach((result, index) => {
        const names = ['Services', 'Member Limit', 'Medical Categories', 'Batch Linkage'];
        if (result.status === 'rejected') {
          console.warn(`Failed to load ${names[index]}:`, result.reason);
        }
      });
    } catch (err) {
      console.error('Initialization error:', err);
      setError('فشل في تحميل البيانات');
    } finally {
      setLoading(false);
    }
  };

  /**
   * PHASE 11: Mandatory Batch Linkage
   * Ensures an active monthly batch exists for the current Provider + Employer.
   * If missing (404), triggers creation.
   */
  const ensureActiveBatch = async (retryEmployerId = null) => {
    const providerId = user?.providerId;
    // Live bug fix (2026-07-20): the visit response has a flat `employerId` field, not
    // `member.employer.id` — that path was always undefined, and the previous fallback to
    // `linkedMemberId` was outright wrong (a MEMBER id is never an employer id), causing
    // `GET /claim-batches/current` to 404 with an employerId that was actually the member's.
    const employerId = retryEmployerId || visitDetails?.employerId;

    if (!providerId || !employerId) {
      console.warn('⚠️ Cannot ensure batch: providerId or employerId missing', { providerId, employerId });
      return;
    }

    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;

    setLoadingBatch(true);
    try {
      console.log(`🔍 Checking batch for Provider:${providerId}, Employer:${employerId}, Period:${month}/${year}`);
      let batch = await claimBatchesService.getCurrentBatch(providerId, employerId, year, month);

      if (!batch) {
        console.log('🆕 No active batch found. Triggering mandatory creation workflow...');
        batch = await claimBatchesService.openOrGetBatch(providerId, employerId, year, month);
      }

      if (batch) {
        setActiveBatchId(batch.id);
        console.log(`✅ Active batch confirmed: ${batch.batchNumber} (ID: ${batch.id})`);
      }
    } catch (err) {
      console.error('❌ Failed to ensure active claim batch:', err);
      // Don't block the whole page, but log the error
    } finally {
      setLoadingBatch(false);
    }
  };

  const loadDraftClaim = async () => {
    if (!draftClaimId) return;

    try {
      const response = await axiosClient.get(`/claims/${draftClaimId}`);
      const claim = response.data?.data || response.data;

      if (!claim) {
        throw new Error('المطالبة غير موجودة');
      }

      if (claim.status && !['DRAFT', 'NEEDS_CORRECTION'].includes(claim.status)) {
        setError('هذه المطالبة ليست في حالة مسودة ولا يمكن تعديلها من بوابة مقدم الخدمة');
        setDraftLoaded(true);
        setActiveClaimId(claim.id || draftClaimId);
        return;
      }

      const claimLinesFromApi = claim.lines || claim.claimLines || [];
      const mappedLines = claimLinesFromApi.map((line, index) => {
        const serviceId = normalizeId(line.medicalServiceId ?? line.serviceId ?? line.medicalService?.id ?? null);
        // PROVIDER-PORTAL-DATA-1: pricingItemId comes straight from the backend's own claim
        // line response — the authoritative identity for contract-priced services.
        const pricingItemId = normalizeId(line.pricingItemId ?? null);
        const selectedService =
          availableServices.find((s) => pricingItemId != null && normalizeId(s.pricingItemId) === pricingItemId) ||
          availableServices.find((s) => serviceId != null && normalizeId(s.medicalServiceId) === serviceId) ||
          availableServices.find((s) => s.code && (s.code === line.serviceCode || s.code === line.medicalServiceCode));

        const rawCategoryId =
          line.serviceCategoryId ??
          line.medicalCategoryId ??
          line.categoryId ??
          line.medicalService?.categoryId ??
          selectedService?.categoryId ??
          null;
        const resolvedCategoryId = normalizeId(rawCategoryId);
        const matchedCategory = medicalCategories.find((c) => normalizeId(c.id) === resolvedCategoryId) || null;
        const categoryServices = matchedCategory
          ? availableServices.filter((s) => doesServiceMatchCategory(s, matchedCategory))
          : resolvedCategoryId
            ? availableServices.filter((s) => normalizeId(s.categoryId) === resolvedCategoryId)
            : selectedService
              ? availableServices.filter((s) => normalizeId(s.categoryId) === normalizeId(selectedService.categoryId))
              : [];

        const unitPrice =
          line.unitPrice ??
          line.requestedUnitPrice ??
          line.priceAtSubmission ??
          line.netAmount ??
          line.totalAmount ??
          selectedService?.price ??
          0;

        return {
          id: index + 1,
          medicalCategoryId: resolvedCategoryId || normalizeId(selectedService?.categoryId),
          medicalCategoryName:
            line.serviceCategoryName ||
            line.medicalCategoryName ||
            line.medicalService?.categoryName ||
            matchedCategory?.name ||
            selectedService?.category ||
            '',
          medicalServiceId: serviceId || selectedService?.medicalServiceId || null,
          pricingItemId: pricingItemId || selectedService?.pricingItemId || null,
          serviceName: line.serviceName || line.medicalServiceName || line.medicalService?.name || selectedService?.name || '',
          serviceCode: line.serviceCode || line.medicalServiceCode || line.medicalService?.code || selectedService?.code || '',
          quantity: line.quantity || 1,
          unitPrice,
          hasContract: true,
          loadingPrice: false,
          priceError: null,
          requiresPA: selectedService?.requiresPA || false,
          filteredServices: categoryServices
        };
      });

      setFormData((prev) => ({
        ...prev,
        diagnosisCode: claim.diagnosisCode || '',
        diagnosisDescription: claim.diagnosisDescription || '',
        doctorName: claim.doctorName || '',
        notes: claim.notes || ''
      }));

      setClaimLines(mappedLines);
      setPendingFiles([]);
      setExistingAttachments(
        (claim.attachments || []).map((att) => ({
          id: att.id,
          fileName: att.fileName,
          fileType: att.fileType,
          attachmentType: att.description || att.attachmentType || 'OTHER',
          fileUrl: att.fileUrl,
          uploadedAt: att.uploadedAt || att.createdAt
        }))
      );
      setLineIdCounter((mappedLines?.length || 0) + 1);
      setDraftLoaded(true);
      const loadedClaimId = claim.id || draftClaimId;
      setActiveClaimId(loadedClaimId);
      if (loadedClaimId) {
        await fetchClaimAttachments(loadedClaimId);
      }
    } catch (err) {
      console.error('Failed to load draft claim:', err);
      setError(resolveApiErrorMessage(err?.response?.data, 'فشل في تحميل المطالبة المحفوظة كمسودة'));
      setDraftLoaded(true);
    }
  };

  const fetchMedicalCategories = async () => {
    setLoadingCategories(true);
    try {
      const response = await axiosClient.get('/provider/medical-categories');
      const categories = response.data?.data || response.data || [];
      setMedicalCategories(
        categories.map((category) => ({
          ...category,
          id: normalizeId(category.id),
          code: category.code || category.categoryCode || '',
          name: category.name || category.nameAr || category.nameEn || category.code || '—'
        }))
      );
    } catch (err) {
      console.error('Failed to fetch medical categories:', err);
      setMedicalCategories([]);
    } finally {
      setLoadingCategories(false);
    }
  };

  const fetchVisitDetails = async () => {
    if (!linkedVisitId) return;
    try {
      const response = await axiosClient.get(`/visits/${linkedVisitId}`);
      const data = response.data?.data || response.data;
      setVisitDetails(data);
      // Live bug fix (2026-07-20): initializePage awaits this and passes the
      // result straight into ensureActiveBatch's employerId param — it must
      // return the fetched visit, not rely on the (possibly stale, same-tick)
      // visitDetails state closure.
      return data;
    } catch (err) {
      console.error('Failed to fetch visit:', err);
    }
  };

  const fetchAvailableServices = async () => {
    setLoadingServices(true);
    try {
      const response = await axiosClient.get('/provider/my-contract/services', {
        params: { size: 2000 }
      });

      const data = response.data?.data || response.data;
      const items = data?.content || data?.items || data || [];

      if (items.length === 0) {
        setAvailableServices([]);
        return;
      }

      setAvailableServices(
        items.map((item) => {
          // PROVIDER-PORTAL-DATA-1: medicalServiceId is only a real catalog link — never
          // fall back to the pricing item's own id when it's absent (that id means
          // something different: provider_contract_pricing_items.id). pricingItemId is
          // the stable, always-present identity for contract-priced services.
          const medicalServiceId = normalizeId(item.medicalServiceId) || null;
          const pricingItemId = normalizeId(item.pricingItemId ?? item.id) || null;
          const requiresPreApproval =
            item.requiresPA || item.requiresPreAuth || item.requiresPreApproval || item.requires_pre_auth || false;
          return {
            id: pricingItemId ?? medicalServiceId, // stable identity for UI list/Autocomplete matching only
            medicalServiceId,
            pricingItemId,
            code: item.serviceCode,
            name: item.serviceName,
            categoryId: normalizeId(item.categoryId || item.serviceCategoryId || item.medicalCategoryId || item.effectiveCategory?.id),
            category: item.categoryName || item.effectiveCategory?.name || item.medicalCategory?.name || '',
            categoryCode: item.categoryCode || item.effectiveCategory?.code || item.medicalCategory?.code || '',
            requiresPA: requiresPreApproval,
            price: item.contractPrice,
            basePrice: item.basePrice,
            contractId: item.contractId,
            hasContract: item.hasContract !== false
          };
        })
      );
    } catch (err) {
      console.error('Failed to fetch services:', err);

      try {
        const response = await axiosClient.get('/provider/my-services');
        const services = response.data?.data || response.data || [];
        setAvailableServices(
          services.map((s) => {
            const medicalServiceId = normalizeId(s.medicalServiceId) || null;
            const pricingItemId = normalizeId(s.pricingItemId ?? s.id) || null;
            return {
              id: pricingItemId ?? medicalServiceId,
              medicalServiceId,
              pricingItemId,
              code: s.service_code || s.serviceCode || s.code,
              name: s.service_name || s.serviceName || s.name,
              categoryId: normalizeId(s.category_id || s.categoryId || s.serviceCategoryId),
              category: s.category_name || s.categoryName || s.category || '',
              categoryCode: s.category_code || s.categoryCode || '',
              requiresPA: s.requires_pre_auth ?? s.requiresPreAuth ?? s.requiresPA ?? false,
              hasContract: true
            };
          })
        );
      } catch (fallbackErr) {
        setAvailableServices([]);
      }
    } finally {
      setLoadingServices(false);
    }
  };

  // Filter services by category (NO LONGER EXCLUDE PA-required services)
  // ALL services are shown with a Badge indicator if they require pre-approval
  const filteredServices = useMemo(() => {
    return availableServices; // Show ALL services
  }, [availableServices]);

  const fetchMemberLimit = async () => {
    if (!linkedMemberId) return;
    try {
      const response = await axiosClient.get(`/members/${linkedMemberId}/remaining-limit`);
      setMemberLimit(response.data?.data || response.data);
    } catch (err) {
      console.error('Failed to fetch member limit:', err);
      setMemberLimit(null);
    }
  };

  // ═══════════════════════════════════════════════════════════════════════════
  // CONTRACT PRICE RESOLUTION
  // ═══════════════════════════════════════════════════════════════════════════
  const fetchContractPrice = useCallback(
    async (serviceCode, lineId) => {
      if (!userProviderId || !serviceCode) return;

      const cachedService = availableServices.find((s) => s.code === serviceCode);
      if (cachedService && cachedService.hasContract && cachedService.price !== undefined) {
        setClaimLines((prev) =>
          prev.map((line) =>
            line.id === lineId
              ? {
                  ...line,
                  unitPrice: cachedService.price,
                  hasContract: true,
                  loadingPrice: false,
                  priceError: null
                }
              : line
          )
        );
        return;
      }

      setClaimLines((prev) => prev.map((line) => (line.id === lineId ? { ...line, loadingPrice: true, priceError: null } : line)));

      try {
        const response = await axiosClient.get(`/provider/my-services/${serviceCode}/price`);
        const priceData = response.data?.data || response.data;

        if (priceData.hasContract && priceData.contractPrice != null) {
          setClaimLines((prev) =>
            prev.map((line) =>
              line.id === lineId
                ? {
                    ...line,
                    unitPrice: priceData.contractPrice,
                    hasContract: true,
                    loadingPrice: false
                  }
                : line
            )
          );
        } else {
          setClaimLines((prev) =>
            prev.map((line) =>
              line.id === lineId
                ? {
                    ...line,
                    unitPrice: 0,
                    hasContract: false,
                    loadingPrice: false,
                    priceError: LABELS.noContract
                  }
                : line
            )
          );
        }
      } catch (err) {
        setClaimLines((prev) =>
          prev.map((line) =>
            line.id === lineId
              ? {
                  ...line,
                  unitPrice: 0,
                  hasContract: false,
                  loadingPrice: false,
                  priceError: LABELS.noContract
                }
              : line
          )
        );
      }
    },
    [userProviderId, availableServices]
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // CLAIM LINE MANAGEMENT
  // ═══════════════════════════════════════════════════════════════════════════
  const addClaimLine = () => {
    setClaimLines((prev) => [
      ...prev,
      {
        id: lineIdCounter,
        medicalCategoryId: null,
        medicalCategoryName: '',
        medicalServiceId: null,
        pricingItemId: null,
        serviceName: '',
        serviceCode: '',
        quantity: 1,
        unitPrice: 0,
        hasContract: false,
        loadingPrice: false,
        priceError: null,
        requiresPA: false,
        filteredServices: []
      }
    ]);
    setLineIdCounter((prev) => prev + 1);
  };

  const removeClaimLine = (lineId) => {
    setClaimLines((prev) => prev.filter((line) => line.id !== lineId));
  };

  const updateClaimLine = (lineId, field, value) => {
    setClaimLines((prev) => prev.map((line) => (line.id === lineId ? { ...line, [field]: value } : line)));
  };

  const handleLineCategoryChange = async (lineId, category) => {
    if (!category) {
      setClaimLines((prev) =>
        prev.map((line) =>
          line.id === lineId
            ? {
                ...line,
                medicalCategoryId: null,
                medicalCategoryName: '',
                medicalServiceId: null,
                pricingItemId: null,
                serviceName: '',
                serviceCode: '',
                unitPrice: 0,
                hasContract: false,
                filteredServices: [],
                priceError: null
              }
            : line
        )
      );
      return;
    }

    const categoryServices = availableServices.filter((s) => doesServiceMatchCategory(s, category));

    setClaimLines((prev) =>
      prev.map((line) =>
        line.id === lineId
          ? {
              ...line,
              medicalCategoryId: normalizeId(category.id),
              medicalCategoryName: category.name,
              medicalServiceId: null,
              pricingItemId: null,
              serviceName: '',
              serviceCode: '',
              unitPrice: 0,
              hasContract: false,
              filteredServices: categoryServices,
              priceError: null
            }
          : line
      )
    );
  };

  const handleServiceSelect = async (lineId, service) => {
    if (!service) {
      updateClaimLine(lineId, 'medicalServiceId', null);
      updateClaimLine(lineId, 'pricingItemId', null);
      updateClaimLine(lineId, 'serviceName', '');
      updateClaimLine(lineId, 'serviceCode', '');
      updateClaimLine(lineId, 'unitPrice', 0);
      updateClaimLine(lineId, 'hasContract', false);
      updateClaimLine(lineId, 'requiresPA', false);
      return;
    }

    const currentLine = claimLines.find((l) => l.id === lineId);
    if (!currentLine?.medicalCategoryId) {
      return;
    }

    // CLAIM-REVIEW-FOLLOWUP-1: the previous check here only ever read a
    // static catalog flag (service.requiresPreApproval/requiresPreAuth/
    // requiresPA) that the real medical-service catalog never actually
    // populates — pre-authorization requirements live on the member's
    // BENEFIT POLICY RULE (per category/service, per employer), not on the
    // service itself, so that check never fired and the provider only found
    // out a PA was required from a generic error after submitting the whole
    // claim. This now asks the backend directly against the member's real
    // active policy (GET /members/{id}/service-coverage, already used
    // elsewhere for coverage-percent lookups) before the line is added.
    if (linkedMemberId) {
      try {
        const coverageResponse = await axiosClient.get(`/members/${linkedMemberId}/service-coverage`, {
          params: { serviceCode: service.code }
        });
        if (coverageResponse?.data?.data?.requiresPreApproval) {
          setError(
            `هذه الخدمة (${service.name}) تتطلب موافقة مسبقة على وثيقة هذا المنتفع ولا يمكن إضافتها في مطالبة عادية. يرجى إنشاء/استخدام موافقة مسبقة من صفحة الموافقات المسبقة أولاً.`
          );
          return;
        }
      } catch (coverageError) {
        // Best-effort check — a failed lookup (e.g. service coverage not
        // resolvable) must never block adding an otherwise valid line; the
        // backend's own save-time validation remains the final authority.
        console.warn('Service coverage/pre-approval check failed:', coverageError);
      }
    }

    // PROVIDER-PORTAL-DATA-1: a service must have at least one valid submit identity
    // (a real catalog link OR a contract pricing item) — never silently substitute the
    // UI list id in its place.
    if (!service.medicalServiceId && !service.pricingItemId) {
      setError('تعذر استخدام هذه الخدمة لأن ربطها بسعر العقد غير مكتمل. يرجى مراجعة مسؤول العقود أو اختيار خدمة أخرى.');
      return;
    }

    const isDuplicate = claimLines.some(
      (l) =>
        l.id !== lineId &&
        ((service.pricingItemId && l.pricingItemId === service.pricingItemId) ||
          (service.medicalServiceId && l.medicalServiceId === service.medicalServiceId))
    );
    if (isDuplicate) {
      setError('هذه الخدمة مضافة بالفعل في بند آخر');
      return;
    }

    const hasContractPrice = service.hasContract !== false && service.price !== undefined && service.price !== null;

    setClaimLines((prev) =>
      prev.map((line) =>
        line.id === lineId
          ? {
              ...line,
              medicalServiceId: service.medicalServiceId || null,
              pricingItemId: service.pricingItemId || null,
              serviceName: service.name,
              serviceCode: service.code,
              unitPrice: hasContractPrice ? service.price : 0,
              hasContract: hasContractPrice,
              loadingPrice: false,
              priceError: hasContractPrice ? null : LABELS.noContract,
              requiresPA: service.requiresPA || false
            }
          : line
      )
    );

    if (!hasContractPrice) {
      fetchContractPrice(service.code, lineId);
    }
  };

  // Calculate totals
  /**
   * ⚠️ UX-ONLY CALCULATION - NOT SENT TO BACKEND
   *
   * These calculations are for DISPLAY PURPOSES ONLY during claim creation.
   * The backend RECALCULATES all amounts from database pricing when processing claims.
   *
   * SAFETY NOTES:
   * - These values help providers preview expected amounts
   * - Backend validates against actual contract pricing (may differ)
   * - Real amounts come from backend after claim submission
   * - This is NOT settlement calculation (settlement uses backend-approved amounts)
   */
  const calculateLineTotal = (line) => (line.unitPrice || 0) * (line.quantity || 1);
  const totalClaimAmount = claimLines.reduce((sum, line) => sum + calculateLineTotal(line), 0);

  // Validation checks
  const linesWithoutCategory = claimLines.filter((line) => !line.medicalCategoryId);
  const hasCategoryViolation = linesWithoutCategory.length > 0;
  const isFormValid =
    claimLines.length > 0 && !hasCategoryViolation && claimLines.every((l) => (l.medicalServiceId || l.pricingItemId) && l.hasContract);

  const hasVisitAndDiagnosis = !!linkedVisitId && !!formData.diagnosisCode?.trim();
  const hasServicesReady = isFormValid;
  const hasAttachmentsReady = true; // Attachments are optional now
  const workflowSteps = ['بيانات المطالبة', 'الخدمات الطبية', 'المرفقات'];
  const workflowActiveStep = !hasVisitAndDiagnosis ? 0 : !hasServicesReady ? 1 : 2;

  // ═══════════════════════════════════════════════════════════════════════════
  // ATTACHMENT HANDLERS
  // ═══════════════════════════════════════════════════════════════════════════
  const handleFileSelect = (event) => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;

    const validFiles = [];
    const invalidMessages = [];

    files.forEach((file) => {
      const extension = (file.name.split('.').pop() || '').toLowerCase();

      if (!ALLOWED_FILE_EXTENSIONS.includes(extension)) {
        invalidMessages.push(`الملف ${file.name}: امتداد غير مدعوم`);
        return;
      }

      if (file.size > MAX_UPLOAD_SIZE_BYTES) {
        invalidMessages.push(`الملف ${file.name}: الحجم أكبر من ${MAX_UPLOAD_SIZE_MB}MB`);
        return;
      }

      validFiles.push(file);
    });

    if (invalidMessages.length > 0) {
      setError(`بعض الملفات مرفوضة:\n${invalidMessages.join('\n')}`);
    }

    if (validFiles.length === 0) {
      event.target.value = '';
      return;
    }

    const newFiles = validFiles.map((file) => ({
      file,
      type: 'MEDICAL_REPORT'
    }));
    setPendingFiles((prev) => [...prev, ...newFiles]);
    event.target.value = '';
  };

  const handleRemoveFile = (index) => {
    setPendingFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleFileTypeChange = (index, type) => {
    setPendingFiles((prev) => prev.map((f, i) => (i === index ? { ...f, type } : f)));
  };

  const fetchClaimAttachments = useCallback(async (claimId) => {
    if (!claimId) return;

    try {
      const response = await axiosClient.get(`/claims/${claimId}/attachments`);
      const payload = response.data?.data ?? response.data ?? [];
      const attachments = Array.isArray(payload) ? payload : [];

      setExistingAttachments(
        attachments.map((att) => ({
          id: att.id,
          fileName: att.fileName,
          fileType: att.fileType,
          attachmentType: att.attachmentType || att.description || 'OTHER',
          fileUrl: att.fileUrl,
          uploadedAt: att.createdAt || att.uploadedAt
        }))
      );
    } catch (err) {
      console.error('Failed to fetch claim attachments:', err);
      setExistingAttachments([]);
    }
  }, []);

  const handleDeleteExistingAttachment = async (attachmentId) => {
    if (!activeClaimId || !attachmentId) return;

    try {
      await axiosClient.delete(`/claims/${activeClaimId}/attachments/${attachmentId}`);
      setExistingAttachments((prev) => prev.filter((att) => att.id !== attachmentId));
    } catch (err) {
      console.error('Failed to delete attachment:', err);
      setError('تعذر حذف المرفق الحالي');
    }
  };

  const uploadAttachments = async (claimId) => {
    if (pendingFiles.length === 0) return;

    setUploading(true);
    let uploaded = 0;

    for (const { file, type } of pendingFiles) {
      try {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('attachmentType', type);

        await axiosClient.post(`/claims/${claimId}/attachments`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });
        uploaded++;
        setUploadProgress(Math.round((uploaded / pendingFiles.length) * 100));
      } catch (err) {
        console.error('Failed to upload file:', file.name, err);
      }
    }

    setUploading(false);
    setUploadProgress(0);
    setPendingFiles([]);
    await fetchClaimAttachments(claimId);
  };

  // ═══════════════════════════════════════════════════════════════════════════
  // FORM HANDLERS
  // ═══════════════════════════════════════════════════════════════════════════
  const handleFormChange = (field) => (event) => {
    setFormData((prev) => ({ ...prev, [field]: event.target.value }));
  };

  const validateDraftForm = () => {
    if (!linkedVisitId) {
      setError('لا يوجد رقم زيارة مرتبط');
      return false;
    }

    if (claimLines.length === 0) {
      setError('يجب إضافة خدمة واحدة على الأقل');
      return false;
    }

    const linesWithoutCategory = claimLines.filter((line) => !line.medicalCategoryId);
    if (linesWithoutCategory.length > 0) {
      setError('🚫 يجب اختيار التصنيف الطبي لجميع الخدمات');
      return false;
    }

    // PROVIDER-PORTAL-DATA-1: a valid line has EITHER a real catalog link (medicalServiceId)
    // OR a contract pricing item (pricingItemId) — medicalServiceId being null is the normal
    // case for contract-priced services with no catalog entry, not an invalid selection.
    const invalidLines = claimLines.filter((line) => (!line.medicalServiceId && !line.pricingItemId) || !line.hasContract);
    if (invalidLines.length > 0) {
      setError('بعض الخدمات غير صالحة أو غير موجودة في العقد');
      return false;
    }

    if (!formData.diagnosisCode || !formData.diagnosisCode.trim()) {
      setError(LABELS.diagnosisCodeRequired);
      return false;
    }

    return true;
  };

  const validateFinalForm = () => validateDraftForm();

  const handleSubmit = async (finalSubmit) => {
    setError(null);
    setAttemptedSubmit(true);
    const isValid = finalSubmit ? validateFinalForm() : validateDraftForm();
    if (!isValid) return;

    setSubmitting(true);
    setSubmitMode(finalSubmit ? 'final' : 'draft');
    try {
      const payload = {
        visitId: parseInt(linkedVisitId),
        memberId: parseInt(linkedMemberId),
        providerId: userProviderId,
        claimBatchId: activeBatchId,
        diagnosisCode: formData.diagnosisCode || null,
        diagnosisDescription: formData.diagnosisDescription || null,
        doctorName: formData.doctorName || null,
        serviceDate: linkedVisitDate || visitDetails?.visitDate || null,
        notes: formData.notes || null,
        // PROVIDER-PORTAL-DATA-1: send BOTH identities (medicalServiceId only when a real
        // catalog link exists, pricingItemId always when a contract pricing item was
        // selected) so the backend can resolve the authoritative contract price even for
        // services that were never linked to the medical_services catalog. unitPrice is
        // included as the already-resolved, displayed contract price — the backend still
        // independently re-resolves and validates it server-side (see ClaimMapper); this
        // is not the "manual price entry" the architecture forbids, it mirrors what's on
        // screen so a resolved-but-then-dropped price no longer produces a zero-amount
        // claim (the root cause of PROVIDER-PORTAL-DATA-1).
        lines: claimLines.map((line) => ({
          medicalServiceId: line.medicalServiceId || null,
          pricingItemId: line.pricingItemId || null,
          unitPrice: line.unitPrice ?? null,
          serviceCode: line.serviceCode || null,
          // Missing in the first DATA-1 pass: without this, the backend falls back to the
          // literal string "Unknown Service" whenever there's no medicalServiceId to resolve
          // a name from — which then can't be matched back into the reviewer's service
          // selector (its options come from a real catalog list), so the line displays as
          // unselected/disappearing even though the price/total are correct.
          serviceName: line.serviceName || null,
          serviceCategoryId: line.medicalCategoryId,
          serviceCategoryName: line.medicalCategoryName,
          quantity: line.quantity || 1
        }))
      };

      const response = activeClaimId
        ? await axiosClient.put(`/claims/${activeClaimId}/data`, payload)
        : await axiosClient.post('/claims', payload);
      const result = response.data?.data || response.data;
      const claimId = result.id || activeClaimId;
      setActiveClaimId(claimId);

      if (pendingFiles.length > 0 && claimId) {
        await uploadAttachments(claimId);
      }

      if (finalSubmit) {
        await axiosClient.post(`/claims/${claimId}/submit`);
      }

      localStorage.removeItem(localDraftStorageKey);
      setAutosaveStatus('idle');
      setAttemptedSubmit(false);

      setSuccess({
        message: finalSubmit ? 'تم إرسال المطالبة للمراجعة الطبية' : 'تم حفظ المطالبة كمسودة بنجاح',
        claimId: claimId,
        referenceNumber: result.claimNumber || result.referenceNumber,
        attachmentsCount: pendingFiles.length + existingAttachments.length
      });
    } catch (err) {
      console.error('Submit error:', err);
      const errorMsg = resolveApiErrorMessage(err.response?.data, 'فشل في تقديم المطالبة');

      setError(errorMsg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleBack = useCallback(() => {
    navigate('/provider/visits');
  }, [navigate]);

  return {
    // theme
    isDark,
    tableHeaderBg,
    tableHeaderColor,

    // visit/member context
    fromVisitLog,
    linkedVisitId,
    linkedMemberId,
    draftClaimId,
    linkedMemberName,
    linkedMemberCivilId,
    linkedMemberCardNumber,
    linkedEmployerName,
    linkedMemberPhone,
    linkedVisitDate,
    linkedVisitTime,
    linkedVisitType,
    linkedProviderName,
    accessBlocked,
    userProviderId,
    userProviderName,
    visitDetails,
    memberLimit,

    // services/categories/lines
    availableServices,
    loadingServices,
    medicalCategories,
    loadingCategories,
    claimLines,
    filteredServices,
    normalizeId,
    doesServiceMatchCategory,
    addClaimLine,
    removeClaimLine,
    updateClaimLine,
    handleLineCategoryChange,
    handleServiceSelect,
    calculateLineTotal,
    totalClaimAmount,
    hasCategoryViolation,
    isFormValid,
    hasVisitAndDiagnosis,
    hasServicesReady,
    hasAttachmentsReady,
    workflowSteps,
    workflowActiveStep,

    // diagnosis/form
    formData,
    setFormData,
    handleFormChange,

    // custom service dialog
    customServiceDialogOpen,
    customServiceData,
    customServiceError,
    addingCustomService,
    handleOpenCustomServiceDialog,
    handleCloseCustomServiceDialog,
    handleCustomServiceDataChange,
    handleSubmitCustomService,

    // attachments
    pendingFiles,
    existingAttachments,
    uploading,
    uploadProgress,
    handleFileSelect,
    handleRemoveFile,
    handleFileTypeChange,
    handleDeleteExistingAttachment,

    // conversation (legacy localStorage — unchanged, replaced by the messaging engine in a later phase)
    providerChatMessages,
    providerChatInput,
    setProviderChatInput,
    handleSendProviderChatMessage,

    // draft/autosave
    activeClaimId,
    draftLoaded,
    autosaveStatus,
    autosaveAt,

    // submit/page-level
    loading,
    submitting,
    submitMode,
    attemptedSubmit,
    error,
    setError,
    success,
    handleSubmit,
    handleBack
  };
}
