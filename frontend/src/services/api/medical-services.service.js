import axiosClient from 'utils/axios';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

/**
 * Medical Services API Service
 * Provides CRUD operations for Medical Services module
 * Backend: MedicalServiceController.java - /api/medical-services
 */
const BASE_URL = '/medical-services';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Get paginated medical services list
 * @param {Object} params - Query parameters
 * @returns {Promise<Object>} Paginated response
 */
export const getMedicalServices = async (params = {}) => {
  const response = await axiosClient.get(BASE_URL, { params });
  return normalizePaginatedResponse(response);
};

/**
 * Get medical service by ID
 * @param {number} id - Service ID
 * @returns {Promise<Object>} Service details
 */
export const getMedicalServiceById = async (id) => {
  const response = await axiosClient.get(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Create new medical service
 * @param {Object} payload - Service data
 * @returns {Promise<Object>} Created service
 */
export const createMedicalService = async (payload) => {
  const response = await axiosClient.post(BASE_URL, payload);
  return unwrap(response);
};

/**
 * Update medical service
 * @param {number} id - Service ID
 * @param {Object} payload - Updated service data
 * @returns {Promise<Object>} Updated service
 */
export const updateMedicalService = async (id, payload) => {
  const response = await axiosClient.put(`${BASE_URL}/${id}`, payload);
  return unwrap(response);
};

/**
 * Delete medical service
 * @param {number} id - Service ID
 * @returns {Promise<void>}
 */
export const deleteMedicalService = async (id) => {
  const response = await axiosClient.delete(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Get all services (for dropdowns)
 * @returns {Promise<Array>} All services
 */
export const getAllMedicalServices = async () => {
  const response = await axiosClient.get(`${BASE_URL}/all`);
  return unwrap(response);
};

/**
 * Download Excel import template
 * @returns {Promise<Blob>} Excel file blob
 */
export const downloadMedicalServicesTemplate = async () => {
  const response = await axiosClient.get(`${BASE_URL}/import/template`, {
    responseType: 'blob'
  });
  return response.data;
};

/**
 * Upload Excel file to import medical services
 * @param {File} file - Excel file
 * @returns {Promise<Object>} Import result
 */
export const uploadMedicalServicesExcel = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await axiosClient.post(`${BASE_URL}/import`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });

  return unwrap(response);
};

/**
 * Get service statistics (counts)
 * @returns {Promise<Object>} Stats { total, active, inactive }
 */
export const getMedicalServicesStats = async () => {
  const response = await axiosClient.get(`${BASE_URL}/stats`);
  return unwrap(response);
};

/**
 * Deactivate all medical services (set active = false)
 * @returns {Promise<number>} Number of services deactivated
 */
export const deactivateAllMedicalServices = async () => {
  const response = await axiosClient.put(`${BASE_URL}/bulk/deactivate`);
  return unwrap(response);
};

/**
 * Activate all medical services (set active = true)
 * @returns {Promise<number>} Number of services activated
 */
export const activateAllMedicalServices = async () => {
  const response = await axiosClient.put(`${BASE_URL}/bulk/activate`);
  return unwrap(response);
};

/**
 * Permanently delete all medical services
 * ⚠️ WARNING: This is irreversible!
 * @returns {Promise<number>} Number of services permanently deleted
 */
export const deleteAllMedicalServices = async () => {
  const response = await axiosClient.delete(`${BASE_URL}/bulk/all?confirm=true`);
  return unwrap(response);
};

/**
 * Quick update category for a medical service (inline edit)
 * @param {number} id - Service ID
 * @param {number} categoryId - New category ID
 * @returns {Promise<Object>} Updated service
 */
export const updateServiceCategory = async (id, categoryId) => {
  const response = await axiosClient.patch(`${BASE_URL}/${id}/category`, { categoryId });
  return unwrap(response);
};

/**
 * Bulk update category for multiple medical services
 * @param {Array<number>} serviceIds - Array of service IDs
 * @param {number} categoryId - New category ID
 * @returns {Promise<Object>} Update result { updated, failed }
 */
export const bulkUpdateServiceCategory = async (serviceIds, categoryId) => {
  const response = await axiosClient.patch(`${BASE_URL}/bulk/category`, { serviceIds, categoryId });
  return unwrap(response);
};

/**
 * Search medical services with filters
 * @param {Object} params - Search parameters
 * @param {string} params.searchTerm - Search term (name/code)
 * @param {number} params.categoryId - Category ID filter
 * @param {boolean} params.requiresPA - Requires PA filter
 * @param {number} params.minPrice - Minimum price
 * @param {number} params.maxPrice - Maximum price
 * @param {number} params.page - Page number (0-based)
 * @param {number} params.size - Page size
 * @returns {Promise<Object>} Paginated search results
 */
export const searchMedicalServices = async (params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/search`, { params });
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// LOOKUP API (For MedicalServiceSelector Component)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Unified lookup for medical service selection.
 *
 * ARCHITECTURAL LAW: MedicalService MUST always be represented as:
 *   CODE + NAME + CATEGORY
 *
 * Features:
 * - Search by: code, name, name, categoryNameAr, categoryNameEn
 * - Optional filter by categoryId
 * - Returns full context for display
 *
 * @param {Object} params - Lookup parameters
 * @param {string} params.q - Search term (optional)
 * @param {number} params.categoryId - Filter by category (optional)
 * @returns {Promise<Array>} List of services with full category context
 */
export const lookupMedicalServices = async (params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/lookup`, { params });
  return unwrap(response);
};
