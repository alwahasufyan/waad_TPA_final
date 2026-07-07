import axiosClient from 'utils/axios';

/**
 * PreAuthorization Audit Trail Service
 */
const preAuthAuditService = {
  /**
   * Get audit history for a specific PreAuth
   */
  getAuditHistory: async (preAuthId, page = 0, size = 20) => {
    const response = await axiosClient.get(`/pre-authorizations/${preAuthId}/history`, {
      params: { page, size }
    });
    return response.data;
  },

  /**
   * Get full audit history (non-paginated)
   */
  getFullAuditHistory: async (preAuthId) => {
    const response = await axiosClient.get(`/pre-authorizations/${preAuthId}/history/full`);
    return response.data;
  },

  /**
   * Get audits by user
   */
  getAuditsByUser: async (username, page = 0, size = 20) => {
    const response = await axiosClient.get(`/pre-authorizations/audits/user/${username}`, {
      params: { page, size }
    });
    return response.data;
  },

  /**
   * Get audits by action type
   */
  getAuditsByAction: async (action, page = 0, size = 20) => {
    const response = await axiosClient.get(`/pre-authorizations/audits/action/${action}`, {
      params: { page, size }
    });
    return response.data;
  },

  /**
   * Get recent audits (last N days)
   */
  getRecentAudits: async (days = 7, page = 0, size = 20) => {
    const response = await axiosClient.get('/pre-authorizations/audits/recent', {
      params: { days, page, size }
    });
    return response.data;
  },

  /**
   * Search audit trail
   */
  searchAudits: async (query, page = 0, size = 20) => {
    const response = await axiosClient.get('/pre-authorizations/audits/search', {
      params: { query, page, size }
    });
    return response.data;
  },

  /**
   * Get audit statistics
   */
  getStatistics: async () => {
    const response = await axiosClient.get('/pre-authorizations/audits/statistics');
    return response.data;
  }
};

export default preAuthAuditService;
