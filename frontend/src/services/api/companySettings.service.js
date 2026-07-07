import axiosClient from 'utils/axios';

/**
 * ============================================================================
 * Company Settings API Service
 * ============================================================================
 *
 * Service for managing employer-specific settings and UI visibility controls.
 *
 * Features:
 * - Employer permission settings (view claims, visits, edit members, etc.)
 * - UI visibility configuration per employer
 * - Granular control over frontend sections display
 *
 * Backend Contract:
 * - GET  /company-settings/employer/:employerId
 * - PUT  /company-settings/employer/:employerId
 * - GET  /company-settings/employer/:employerId/ui
 * - PUT  /company-settings/employer/:employerId/ui
 *
 * CompanySettingsDto Structure:
 * {
 *   id: 1,
 *   companyId: 1,
 *   employerId: 1,
 *   canViewClaims: false,
 *   canViewVisits: false,
 *   canEditMembers: true,
 *   canDownloadAttachments: true,
 *   employerName: "شركة الأسمنت",
 *   companyName: "TBA",
 *   uiVisibility: { ... }
 * }
 *
 * UiVisibilityDto Structure:
 * {
 *   members: {
 *     showFamilyTab: true,
 *     showDocumentsTab: true,
 *     showBenefitsTab: true
 *   },
 *   claims: {
 *     showFilesSection: true,
 *     showPaymentsSection: true,
 *     showDiagnosisSection: true
 *   },
 *   visits: {
 *     showAttachmentsSection: true,
 *     showServiceDetailsSection: true
 *   },
 *   dashboard: {
 *     showMembersKpi: true,
 *     showClaimsKpi: true,
 *     showVisitsKpi: true
 *   }
 * }
 *
 * @created 2026-01-02
 */

const BASE_URL = '/company-settings';

// ============================================================================
// EMPLOYER SETTINGS OPERATIONS
// ============================================================================

/**
 * Get employer settings by employer ID
 * @param {number} employerId - Employer ID
 * @returns {Promise<Object>} Employer settings with UI visibility
 */
const getByEmployerId = async (employerId) => {
  try {
    const response = await axiosClient.get(`${BASE_URL}/employer/${employerId}`);
    return response.data;
  } catch (error) {
    console.error(`Error fetching settings for employer ${employerId}:`, error);
    throw error;
  }
};

/**
 * Update employer settings
 * @param {number} employerId - Employer ID
 * @param {Object} data - Updated settings
 * @returns {Promise<Object>} Updated settings
 */
const updateSettings = async (employerId, data) => {
  try {
    const response = await axiosClient.put(`${BASE_URL}/employer/${employerId}`, data);
    return response.data;
  } catch (error) {
    console.error(`Error updating settings for employer ${employerId}:`, error);
    throw error;
  }
};

// ============================================================================
// UI VISIBILITY OPERATIONS
// ============================================================================

/**
 * Get UI visibility settings for employer
 * @param {number} employerId - Employer ID
 * @returns {Promise<Object>} UI visibility configuration
 */
const getUiVisibility = async (employerId) => {
  try {
    const response = await axiosClient.get(`${BASE_URL}/employer/${employerId}/ui`);
    return response.data;
  } catch (error) {
    console.error(`Error fetching UI visibility for employer ${employerId}:`, error);
    throw error;
  }
};

/**
 * Update UI visibility settings for employer
 * @param {number} employerId - Employer ID
 * @param {Object} data - Updated UI visibility configuration
 * @returns {Promise<Object>} Updated UI visibility
 */
const updateUiVisibility = async (employerId, data) => {
  try {
    const response = await axiosClient.put(`${BASE_URL}/employer/${employerId}/ui`, data);
    return response.data;
  } catch (error) {
    console.error(`Error updating UI visibility for employer ${employerId}:`, error);
    throw error;
  }
};

// ============================================================================
// EXPORTS
// ============================================================================

export const companySettingsService = {
  getByEmployerId,
  updateSettings,
  getUiVisibility,
  updateUiVisibility
};

export default companySettingsService;
