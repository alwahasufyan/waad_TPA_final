import axiosClient from 'utils/axios';

/**
 * Medical Classification Engine API Service (MC-1)
 * Backend: PriceListImportController.java — import & staging layer only.
 * Review decisions and publishing arrive in MC-2/MC-3.
 */
const BASE_URL = '/classification/imports';

const unwrap = (response) => response.data?.data || response.data;

export const classificationService = {
  /**
   * Upload a provider price list (multipart). Idempotent by file hash:
   * duplicate uploads for the same provider are rejected by the backend.
   */
  uploadImport: async ({ providerId, contractId, hint, file }) => {
    const formData = new FormData();
    formData.append('providerId', providerId);
    if (contractId) formData.append('contractId', contractId);
    if (hint) formData.append('hint', hint);
    formData.append('file', file);
    const response = await axiosClient.post(BASE_URL, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    return unwrap(response);
  },

  /** Paginated imports list (newest first). */
  getImports: async ({ providerId, page = 0, size = 25 } = {}) => {
    const params = { page, size };
    if (providerId) params.providerId = providerId;
    const response = await axiosClient.get(BASE_URL, { params });
    return unwrap(response);
  },

  /** One import with full provenance. */
  getImport: async (id) => {
    const response = await axiosClient.get(`${BASE_URL}/${id}`);
    return unwrap(response);
  },

  /** Staged lines of an import (optional reviewStatus filter). */
  getImportLines: async (id, { reviewStatus, page = 0, size = 50 } = {}) => {
    const params = { page, size };
    if (reviewStatus) params.reviewStatus = reviewStatus;
    const response = await axiosClient.get(`${BASE_URL}/${id}/lines`, { params });
    return unwrap(response);
  },

  cancelImport: async (id) => {
    const response = await axiosClient.post(`${BASE_URL}/${id}/cancel`);
    return unwrap(response);
  },

  /** Engine availability probe — returns { ok, problem }. */
  engineHealth: async () => {
    const response = await axiosClient.get(`${BASE_URL}/engine/health`);
    const body = response.data;
    return { ok: body?.status === 'success', problem: body?.message || null };
  },

  // ── MC-2: Medical Classification Workspace (review) ──────────────────────

  /** Workspace header: progress, queue breakdown, Approve-Remaining gate, knowledge counter. */
  getReviewSummary: async (importId) => {
    const response = await axiosClient.get(`${BASE_URL}/${importId}/review/summary`);
    return unwrap(response);
  },

  /** One critical-queue tab: UNKNOWN | LOW_CONFIDENCE | DUPLICATE | GUARD. */
  getReviewQueue: async (importId, queue, { page = 0, size = 50 } = {}) => {
    const response = await axiosClient.get(`${BASE_URL}/${importId}/review/queue/${queue}`, {
      params: { page, size }
    });
    return unwrap(response);
  },

  /** Decide one line: { action: 'APPROVE'|'REJECT', categoryId?, serviceId?, price?, note? }. */
  decideLine: async (importId, lineId, decision) => {
    const response = await axiosClient.post(`${BASE_URL}/${importId}/review/lines/${lineId}/decide`, decision);
    return unwrap(response);
  },

  /** Same decision applied to several lines: { lineIds: [...], action, categoryId?, note? }. */
  decideBulk: async (importId, decision) => {
    const response = await axiosClient.post(`${BASE_URL}/${importId}/review/lines/decide-bulk`, decision);
    return unwrap(response);
  },

  /** A5: explicit audited approval of the hidden high-confidence majority. */
  approveRemaining: async (importId) => {
    const response = await axiosClient.post(`${BASE_URL}/${importId}/review/approve-remaining`);
    return unwrap(response);
  },

  /**
   * MC-4A: finish review — Approve Remaining (A5) + auto-create the version +
   * run financial validation, in one action. Returns { bulkApproved, versionId }.
   */
  finishReview: async (importId, contractId) => {
    const params = contractId ? { contractId } : {};
    const response = await axiosClient.post(`${BASE_URL}/${importId}/review/finish`, null, { params });
    return unwrap(response);
  },
  // ── MC-4C: Exception Edits ──────────────────────────────────────────────────
  createPatchDraft: async (contractId) => {
    const response = await axiosClient.post(`/classification/versions/exception/draft/${contractId}`);
    return unwrap(response);
  },
  recordExceptionPriceChange: async (versionId, pricingItemId, newPrice, reason) => {
    const params = { pricingItemId, newPrice, reason };
    const response = await axiosClient.post(`/classification/versions/${versionId}/exception/record`, null, { params });
    return unwrap(response);
  },
  addExceptionService: async (versionId, medicalServiceId, price, reason) => {
    const response = await axiosClient.post(`/classification/versions/${versionId}/exception/add`, { medicalServiceId, price, reason });
    return unwrap(response);
  },
  deactivateExceptionService: async (versionId, pricingItemId, reason) => {
    const response = await axiosClient.post(`/classification/versions/${versionId}/exception/deactivate`, null, { params: { pricingItemId, reason } });
    return unwrap(response);
  },
  publishPatch: async (versionId) => {
    const response = await axiosClient.post(`/classification/versions/${versionId}/exception/publish`);
    return unwrap(response);
  },
  createRollbackDraft: async (versionId) => {
    const response = await axiosClient.post(`/classification/versions/${versionId}/rollback`);
    return unwrap(response);
  },
  getPriceChangeAudit: async (versionId) => {
    const response = await axiosClient.get(`/classification/versions/${versionId}/price-change-audit`);
    return unwrap(response);
  },
  // ── MC-3: Price List Versions (financial artifact) ───────────────────────

  /** Create a DRAFT version from a REVIEW_COMPLETE import (A10 validation runs immediately). */
  createVersionFromImport: async (importId, contractId) => {
    const params = contractId ? { contractId } : {};
    const response = await axiosClient.post(`/classification/versions/from-import/${importId}`, null, { params });
    return unwrap(response);
  },

  getVersions: async (contractId) => {
    const response = await axiosClient.get('/classification/versions', { params: { contractId } });
    return unwrap(response);
  },

  /** A11: the approval artifact — comparison report + gate state. */
  getVersionComparison: async (versionId) => {
    const response = await axiosClient.get(`/classification/versions/${versionId}/comparison`);
    return unwrap(response);
  },

  getVersionFindings: async (versionId) => {
    const response = await axiosClient.get(`/classification/versions/${versionId}/findings`);
    return unwrap(response);
  },

  revalidateVersion: async (versionId) => {
    const response = await axiosClient.post(`/classification/versions/${versionId}/validate`);
    return unwrap(response);
  },

  resolveFinding: async (versionId, findingId, note) => {
    const response = await axiosClient.post(`/classification/versions/${versionId}/findings/${findingId}/resolve`, { note });
    return unwrap(response);
  },

  waiveFinding: async (versionId, findingId, note) => {
    const response = await axiosClient.post(`/classification/versions/${versionId}/findings/${findingId}/waive`, { note });
    return unwrap(response);
  },

  fixLinePrice: async (versionId, lineId, price, note) => {
    const response = await axiosClient.patch(`/classification/versions/${versionId}/lines/${lineId}/price`, { price, note });
    return unwrap(response);
  },

  approveVersion: async (versionId) => {
    const response = await axiosClient.post(`/classification/versions/${versionId}/approve`);
    return unwrap(response);
  },

  publishVersion: async (versionId) => {
    const response = await axiosClient.post(`/classification/versions/${versionId}/publish`);
    return unwrap(response);
  },

  archiveVersion: async (versionId) => {
    const response = await axiosClient.post(`/classification/versions/${versionId}/archive`);
    return unwrap(response);
  },

  /** MC-4B: contract قائمة الأسعار tab — active version card + brief history + draft. */
  getContractPriceListSummary: async (contractId) => {
    const response = await axiosClient.get(`/classification/versions/contract/${contractId}/summary`);
    return unwrap(response);
  },

  getCategoryPicker: async (importId) => {
    const response = await axiosClient.get(`${BASE_URL}/${importId}/review/pickers/categories`);
    return unwrap(response);
  },

  searchCatalogServices: async (importId, q) => {
    const response = await axiosClient.get(`${BASE_URL}/${importId}/review/pickers/services`, { params: { q } });
    return unwrap(response);
  }
};

export default classificationService;
