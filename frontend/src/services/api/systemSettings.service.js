/**
 * System Settings API Service
 * Manages system-wide configurable settings
 *
 * API Base: /api/v1/admin/system-settings
 */

import axiosClient from 'utils/axios';

const BASE_URL = '/admin/system-settings';

/**
 * System Settings Service
 */
export const systemSettingsService = {
  /**
   * Get all editable system settings
   * @returns {Promise<Array>} List of settings
   */
  getAll: async () => {
    const response = await axiosClient.get(BASE_URL);
    return response.data;
  },

  /**
   * Get settings by category
   * @param {string} category - Category name (CLAIMS, MEMBERS, SECURITY, etc.)
   * @returns {Promise<Array>} List of settings in category
   */
  getByCategory: async (category) => {
    const response = await axiosClient.get(`${BASE_URL}/category/${category}`);
    return response.data;
  },

  /**
   * Get current claim SLA days
   * @returns {Promise<Object>} { slaDays, description }
   */
  getClaimSlaDays: async () => {
    const response = await axiosClient.get(`${BASE_URL}/claim-sla-days`);
    return response.data;
  },

  /**
   * Update claim SLA days
   * @param {number} slaDays - New SLA days (1-30)
   * @returns {Promise<Object>} { oldValue, newValue, message, updatedBy }
   */
  updateClaimSlaDays: async (slaDays) => {
    const response = await axiosClient.put(`${BASE_URL}/claim-sla-days`, { slaDays });
    return response.data;
  },

  /**
   * Reset claim SLA days to default
   * @returns {Promise<Object>} { oldValue, newValue, message, updatedBy }
   */
  resetClaimSlaDays: async () => {
    const response = await axiosClient.post(`${BASE_URL}/claim-sla-days/reset`);
    return response.data;
  },

  /**
   * Get SLA compliance report
   * @returns {Promise<Object>} Compliance report
   */
  getSlaComplianceReport: async () => {
    const response = await axiosClient.get(`${BASE_URL}/sla-compliance-report`);
    return response.data;
  },

  /**
   * Update a specific setting by key
   * @param {string} key - Setting key
   * @param {string} value - New value
   * @returns {Promise<Object>} Updated setting
   */
  updateSetting: async (key, value) => {
    const response = await axiosClient.put(`${BASE_URL}/${key}`, { value });
    return response.data;
  }
};

export default systemSettingsService;
