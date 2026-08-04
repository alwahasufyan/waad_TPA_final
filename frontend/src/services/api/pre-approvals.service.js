import axiosClient from 'utils/axios';
import { createErrorHandler } from 'utils/api-error-handler';
import { validateAmount } from 'utils/api-validators';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

// ==============================|| PRE-APPROVALS SERVICE ||============================== //

// NOTE: Backend API v1 uses /v1/pre-authorizations endpoint
// Frontend routes use /pre-approvals for UI consistency
const BASE_URL = '/pre-authorizations';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Error handler for pre-approvals service
 * Provides user-friendly Arabic error messages
 */
const handlePreApprovalErrors = createErrorHandler('الموافقة المسبقة', {
  404: 'الموافقة المسبقة غير موجودة',
  409: 'يوجد تعارض في بيانات الموافقة المسبقة',
  422: 'البيانات المُدخلة للموافقة المسبقة غير صحيحة'
});

export const preApprovalsService = {
  /**
   * Get all pre-approvals with pagination
   * @param {Object} params - Optional pagination params {page, size, sortBy, sortDirection}
   * @returns {Promise<Object>} Paginated pre-approvals
   */
  getAll: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.page !== undefined) queryParams.append('page', params.page - 1); // Backend is 0-indexed
      if (params.size) queryParams.append('size', params.size);
      if (params.sortBy) queryParams.append('sortBy', params.sortBy);
      if (params.sortDir) queryParams.append('sortDirection', params.sortDir);

      const url = queryParams.toString() ? `${BASE_URL}?${queryParams.toString()}` : BASE_URL;
      const response = await axiosClient.get(url);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Get pre-approval by ID
   * @param {number} id - Pre-approval ID
   * @returns {Promise<Object>} Pre-approval details
   */
  getById: async (id) => {
    try {
      if (!id) throw new Error('معرف الموافقة المسبقة مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/${id}`);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Create new pre-approval (standard flow)
   * Pre-authorization MUST be created from an existing Visit (Visit-Centric Architecture)
   * @param {Object} data - Pre-approval data with visitId, memberId, providerId, serviceCode, etc.
   * @returns {Promise<Object>} Created pre-approval
   */
  create: async (data) => {
    try {
      if (!data) throw new Error('بيانات الموافقة المسبقة مطلوبة');
      if (!data.visitId) throw new Error('معرف الزيارة مطلوب - يجب إنشاء الموافقة المسبقة من زيارة');
      if (data.requestedAmount !== undefined) {
        validateAmount(data.requestedAmount, 'المبلغ المطلوب');
      }
      // Use standard endpoint (Visit-Centric Architecture)
      const response = await axiosClient.post(BASE_URL, data);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Create pre-approval with full details (alias for create - same endpoint)
   * @param {Object} data - Full pre-approval data with providerId, serviceCode, etc.
   * @returns {Promise<Object>} Created pre-approval
   */
  createFull: async (data) => {
    try {
      if (!data) throw new Error('بيانات الموافقة المسبقة مطلوبة');
      if (!data.visitId) throw new Error('معرف الزيارة مطلوب');
      if (data.requestedAmount !== undefined) {
        validateAmount(data.requestedAmount, 'المبلغ المطلوب');
      }
      const response = await axiosClient.post(BASE_URL, data);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Update pre-authorization data (PENDING/NEEDS_CORRECTION)
   * @param {number} id - Pre-approval ID
   * @param {Object} data - Updated pre-approval data
   * @returns {Promise<Object>} Updated pre-approval
   */
  updateData: async (id, data) => {
    try {
      if (!id) throw new Error('معرف الموافقة المسبقة مطلوب');
      if (!data) throw new Error('بيانات التحديث مطلوبة');
      if (data.requestedAmount !== undefined) {
        validateAmount(data.requestedAmount, 'المبلغ المطلوب');
      }
      const response = await axiosClient.put(`${BASE_URL}/${id}/data`, data);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Reviewer update for pre-authorization review actions
   * @param {number} id - Pre-approval ID
   * @param {Object} data - Review payload
   * @returns {Promise<Object>} Updated pre-approval
   */
  updateReview: async (id, data) => {
    try {
      if (!id) throw new Error('معرف الموافقة المسبقة مطلوب');
      if (!data) throw new Error('بيانات المراجعة مطلوبة');
      const response = await axiosClient.put(`${BASE_URL}/${id}/review`, data);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  // Backward-compatible alias (internally aligned to /data)
  update: async (id, data) => preApprovalsService.updateData(id, data),

  /**
   * Delete pre-approval
   * @param {number} id - Pre-approval ID
   * @returns {Promise<void>}
   */
  remove: async (id) => {
    try {
      if (!id) throw new Error('معرف الموافقة المسبقة مطلوب');
      const response = await axiosClient.delete(`${BASE_URL}/${id}`);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Get pre-approvals by status (paginated)
   * WAAD-PREAUTH-REVIEWER-HISTORY-1: used by the reviewer inbox's "processed"
   * history view (APPROVED/REJECTED) so previously-actioned requests remain
   * referenceable instead of only ever appearing in the pending queue.
   * @param {string} status - Status (PENDING, APPROVED, REJECTED, ...)
   * @param {Object} params - Optional pagination params {page, size, sortBy, sortDir}
   * @returns {Promise<Object>} Paginated pre-approvals {items, total, page, size}
   */
  getByStatus: async (status, params = {}) => {
    try {
      if (!status) throw new Error('حالة الموافقة مطلوبة');
      const queryParams = new URLSearchParams();
      if (params.page !== undefined) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);
      if (params.sortBy) queryParams.append('sortBy', params.sortBy);
      if (params.sortDir) queryParams.append('sortDirection', params.sortDir);

      const url = queryParams.toString()
        ? `${BASE_URL}/status/${status}?${queryParams.toString()}`
        : `${BASE_URL}/status/${status}`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Get pending pre-approvals (Operations Queue)
   * Uses /inbox/pending endpoint (FIFO order by default)
  * @param {Object} params - Optional pagination params {page, size, sortBy, sortDir, status, priority, search, fromDate, toDate, providerId}
   * @returns {Promise<Object>} Paginated pending pre-approvals
   */
  getPending: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      // Pass page directly (component sends page+1, backend handles conversion)
      if (params.page) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);
      if (params.sortBy) queryParams.append('sortBy', params.sortBy);
      if (params.sortDir) queryParams.append('sortDir', params.sortDir);
      // Filters
      if (params.status) queryParams.append('status', params.status);
      if (params.priority) queryParams.append('priority', params.priority);
      if (params.search) queryParams.append('search', params.search);
      if (params.fromDate) queryParams.append('fromDate', params.fromDate);
      if (params.toDate) queryParams.append('toDate', params.toDate);
      if (params.providerId) queryParams.append('providerId', params.providerId);

      const url = queryParams.toString() ? `${BASE_URL}/inbox/pending?${queryParams.toString()}` : `${BASE_URL}/inbox/pending`;
      const response = await axiosClient.get(url);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Approve pre-approval
   * @param {number} id - Pre-approval ID
   * @param {Object} data - Approval data
   * @returns {Promise<Object>} Approved pre-approval
   */
  approve: async (id, data) => {
    try {
      if (!id) throw new Error('معرف الموافقة مطلوب');
      // Only send approvalNotes - backend calculates all financial values (approvedAmount, copayAmount, etc.)
      const payload = { approvalNotes: data?.approvalNotes || null };
      // Async approval - returns immediately with APPROVAL_IN_PROGRESS status
      const response = await axiosClient.post(`${BASE_URL}/${id}/approve`, payload);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Reject pre-approval
   * @param {number} id - Pre-approval ID
   * @param {Object} data - Rejection data
   * @returns {Promise<Object>} Rejected pre-approval
   */
  reject: async (id, data) => {
    try {
      if (!id) throw new Error('معرف الموافقة مطلوب');
      if (!data?.rejectionReason) {
        throw new Error('سبب الرفض مطلوب');
      }
      const response = await axiosClient.post(`${BASE_URL}/${id}/reject`, data);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * WAAD-PREAUTH-MULTI-LINE-1 (Phase 3): record a reviewer's decision for
   * ONE service line of a pre-authorization. Does not change the header
   * status — call finalizeReview() once every line has a decision.
   * @param {number} preAuthId - Pre-authorization ID
   * @param {number} lineId - Line ID
   * @param {Object} data - { decision: 'APPROVED'|'REJECTED'|'CLARIFICATION_REQUIRED', reason, approvedAmount }
   * @returns {Promise<Object>} Updated pre-approval (with lines)
   */
  submitLineDecision: async (preAuthId, lineId, data) => {
    try {
      if (!preAuthId || !lineId) throw new Error('معرف الموافقة والخدمة مطلوبان');
      if (!data?.decision) throw new Error('القرار مطلوب');
      const response = await axiosClient.post(`${BASE_URL}/${preAuthId}/lines/${lineId}/decision`, data);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * WAAD-PREAUTH-MULTI-LINE-1 (Phase 3): compute the pre-authorization's
   * final outcome (APPROVED/REJECTED/PARTIALLY_APPROVED) from its lines'
   * decisions. Requires every line to have a decision first.
   * @param {number} preAuthId - Pre-authorization ID
   * @returns {Promise<Object>} Finalized pre-approval
   */
  finalizeReview: async (preAuthId) => {
    try {
      if (!preAuthId) throw new Error('معرف الموافقة مطلوب');
      const response = await axiosClient.post(`${BASE_URL}/${preAuthId}/finalize`);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Acknowledge pre-approval (Provider viewed approval) - PHASE 5
   * Lifecycle: APPROVED → ACKNOWLEDGED
   * @param {number} id - Pre-approval ID
   * @returns {Promise<Object>} Acknowledged pre-approval
   */
  acknowledge: async (id) => {
    try {
      if (!id) throw new Error('معرف الموافقة مطلوب');
      const response = await axiosClient.post(`${BASE_URL}/${id}/acknowledge`);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Start review of a submitted pre-approval (PENDING/NEEDS_CORRECTION → UNDER_REVIEW)
   * @param {number} id - Pre-approval ID
   * @returns {Promise<Object>} Updated pre-approval
   */
  startReview: async (id) => {
    try {
      if (!id) throw new Error('معرف الموافقة مطلوب');
      const response = await axiosClient.post(`${BASE_URL}/${id}/start-review`);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  // ======================= INBOX OPERATIONS =======================

  /**
   * Get pre-approvals inbox by status (PHASE 5)
   * @param {string} status - Status filter ('approved', 'acknowledged', 'pending')
   * @param {number} page - Page number (1-indexed)
   * @param {number} size - Page size
   * @returns {Promise<Object>} Paginated pre-approvals {items, total, page, size}
   */
  getInbox: async (status, page = 1, size = 10) => {
    try {
      if (!status) throw new Error('حالة الموافقة مطلوبة');

      const queryParams = new URLSearchParams();
      queryParams.append('page', page);
      queryParams.append('size', size);
      queryParams.append('status', status.toUpperCase());

      const response = await axiosClient.get(`${BASE_URL}/inbox/pending?${queryParams.toString()}`);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Request correction from the provider (PENDING/UNDER_REVIEW → NEEDS_CORRECTION)
   * PREAUTH-REVIEW-WORKFLOW-1
   * @param {number} id - Pre-approval ID
   * @param {string} notes - Explanation of what needs to be corrected (required, max 1000 chars)
   * @returns {Promise<Object>} Updated pre-approval
   */
  requestInfo: async (id, notes) => {
    try {
      if (!id) throw new Error('معرف الموافقة مطلوب');
      if (!notes || !notes.trim()) throw new Error('يجب توضيح المعلومات المطلوبة');
      const response = await axiosClient.post(`${BASE_URL}/${id}/request-info`, { notes: notes.trim() });
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Get pre-approvals by provider, across all statuses — used for the
   * provider portal's "my submissions" inbox (PREAUTH-REVIEW-WORKFLOW-1).
   * @param {number} providerId - Provider ID (the caller's own — see
   *   pages/provider/PreAuthInbox.jsx for how it's resolved from the
   *   authenticated user)
   * @param {Object} params - Optional pagination params {page, size, sortBy, sortDirection}
   * @returns {Promise<Object>} Paginated pre-approvals
   */
  getByProvider: async (providerId, params = {}) => {
    try {
      if (!providerId) throw new Error('معرف مقدم الخدمة مطلوب');
      const queryParams = new URLSearchParams();
      if (params.page !== undefined) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);
      if (params.sortBy) queryParams.append('sortBy', params.sortBy);
      if (params.sortDirection) queryParams.append('sortDirection', params.sortDirection);

      const url = queryParams.toString()
        ? `${BASE_URL}/provider/${providerId}?${queryParams.toString()}`
        : `${BASE_URL}/provider/${providerId}`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Get pre-approvals by member
   * @param {number} memberId - Member ID
   * @returns {Promise<Array>} List of pre-approvals for member
   */
  getByMember: async (memberId) => {
    try {
      if (!memberId) throw new Error('معرف العضو مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/member/${memberId}`);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Check if member has valid pre-approval for service
   * @param {number} memberId - Member ID
   * @param {string} serviceCode - Service code
   * @returns {Promise<Object>} Validity check result {valid, preApproval, remainingAmount}
   */
  checkValidity: async (memberId, serviceCode) => {
    try {
      if (!memberId) throw new Error('معرف العضو مطلوب');
      if (!serviceCode) throw new Error('رمز الخدمة مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/check-validity?memberId=${memberId}&serviceCode=${serviceCode}`);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Get pre-approval attachments
   * @param {number} id - Pre-approval ID
   * @returns {Promise<Array>} List of attachments
   */
  getAttachments: async (id) => {
    try {
      if (!id) throw new Error('معرف الموافقة المسبقة مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/${id}/attachments`);
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Upload attachment to pre-approval
   * @param {number} id - Pre-approval ID
   * @param {FormData} formData - Form data with file and attachmentType
   * @returns {Promise<Object>} Uploaded attachment metadata
   */
  uploadAttachment: async (id, formData) => {
    try {
      if (!id) throw new Error('معرف الموافقة المسبقة مطلوب');
      if (!formData) throw new Error('بيانات الملف مطلوبة');
      const response = await axiosClient.post(`${BASE_URL}/${id}/attachments`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });
      return unwrap(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Download pre-approval attachment
   * @param {number} preApprovalId - Pre-approval ID
   * @param {number} attachmentId - Attachment ID
   * @returns {Promise<Blob>} File blob
   */
  downloadAttachment: async (preApprovalId, attachmentId) => {
    try {
      if (!preApprovalId) throw new Error('معرف الموافقة المسبقة مطلوب');
      if (!attachmentId) throw new Error('معرف المرفق مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/${preApprovalId}/attachments/${attachmentId}`, {
        responseType: 'blob'
      });
      return response.data;
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  },

  /**
   * Search pre-approvals
   * @param {Object} params - { q, page, size, sortBy, sortDir }
   * @returns {Promise<Object>} Paginated results
   */
  search: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.q) queryParams.append('q', params.q);
      if (params.page !== undefined) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);
      if (params.sortBy) queryParams.append('sortBy', params.sortBy);
      if (params.sortDir) queryParams.append('sortDirection', params.sortDir);

      const response = await axiosClient.get(`${BASE_URL}/search?${queryParams.toString()}`);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handlePreApprovalErrors(error);
    }
  }
};

export default preApprovalsService;
