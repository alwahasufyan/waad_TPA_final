import axiosClient from 'utils/axios';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

/**
 * ============================================================================
 * Medical Packages API Service
 * ============================================================================
 *
 * CONTRACT: MEDICAL_PACKAGE_API_CONTRACT.md
 *
 * DTOs per Contract:
 * - MedicalPackage (read): id, code, name, name, description, services[],
 *                          totalCoverageLimit, active, createdAt, updatedAt, servicesCount
 * - MedicalPackageDTO (create/update): code, name, name, description?,
 *                                       serviceIds[]?, totalCoverageLimit?, active?
 * - MedicalPackageSelectorDto: id, code, name, name
 *
 * Endpoints per Contract:
 * - GET  /api/medical-packages              - قائمة (paginated)
 * - GET  /api/medical-packages/selector     - للقوائم المنسدلة
 * - GET  /api/medical-packages/{id}         - جلب بالمعرف
 * - GET  /api/medical-packages/code/{code}  - جلب بالكود
 * - GET  /api/medical-packages/active       - الباقات النشطة فقط
 * - POST /api/medical-packages              - إنشاء
 * - PUT  /api/medical-packages/{id}         - تحديث
 * - DELETE /api/medical-packages/{id}       - حذف
 *
 * Pagination Parameters:
 * - page (1-based), size, search, sortBy, sortDir
 *
 * @updated 2026-01-13 - Aligned with MEDICAL_PACKAGE_API_CONTRACT.md
 */
const BASE_URL = '/medical-packages';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Get paginated medical packages list
 * CONTRACT: GET /api/medical-packages
 * @param {Object} params - Query parameters (page, size, search, sortBy, sortDir)
 * @returns {Promise<Object>} Paginated response { items, total, page, size }
 */
export const getMedicalPackages = async (params = {}) => {
  const response = await axiosClient.get(BASE_URL, { params });
  return normalizePaginatedResponse(response);
};

/**
 * Get medical package by ID
 * CONTRACT: GET /api/medical-packages/{id}
 * @param {number} id - Package ID
 * @returns {Promise<Object>} MedicalPackage
 */
export const getMedicalPackageById = async (id) => {
  const response = await axiosClient.get(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Get medical package by code
 * CONTRACT: GET /api/medical-packages/code/{code}
 * @param {string} code - Package code
 * @returns {Promise<Object>} MedicalPackage
 */
export const getMedicalPackageByCode = async (code) => {
  const response = await axiosClient.get(`${BASE_URL}/code/${code}`);
  return unwrap(response);
};

/**
 * Create new medical package
 * CONTRACT: POST /api/medical-packages
 * @param {Object} payload - MedicalPackageDTO (code, name, name, description?, serviceIds[]?, totalCoverageLimit?, active?)
 * @returns {Promise<Object>} Created MedicalPackage
 */
export const createMedicalPackage = async (payload) => {
  const response = await axiosClient.post(BASE_URL, payload);
  return unwrap(response);
};

/**
 * Update medical package
 * CONTRACT: PUT /api/medical-packages/{id}
 * @param {number} id - Package ID
 * @param {Object} payload - MedicalPackageDTO
 * @returns {Promise<Object>} Updated MedicalPackage
 */
export const updateMedicalPackage = async (id, payload) => {
  const response = await axiosClient.put(`${BASE_URL}/${id}`, payload);
  return unwrap(response);
};

/**
 * Delete medical package
 * CONTRACT: DELETE /api/medical-packages/{id}
 * @param {number} id - Package ID
 * @returns {Promise<void>}
 */
export const deleteMedicalPackage = async (id) => {
  const response = await axiosClient.delete(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Get packages for dropdown selector
 * CONTRACT: GET /api/medical-packages/selector
 * @returns {Promise<Array>} MedicalPackageSelectorDto[]
 */
export const getMedicalPackagesSelector = async () => {
  const response = await axiosClient.get(`${BASE_URL}/selector`);
  return unwrap(response);
};

/**
 * Get active packages only
 * CONTRACT: GET /api/medical-packages/active
 * @returns {Promise<Array>} MedicalPackage[] (active only)
 */
export const getActiveMedicalPackages = async () => {
  const response = await axiosClient.get(`${BASE_URL}/active`);
  return unwrap(response);
};

/**
 * Legacy: Get all packages (alias for selector)
 * @deprecated Use getMedicalPackagesSelector instead
 */
export const getAllMedicalPackages = getMedicalPackagesSelector;

// Default export for compatibility
const medicalPackagesService = {
  getMedicalPackages,
  getMedicalPackageById,
  getMedicalPackageByCode,
  createMedicalPackage,
  updateMedicalPackage,
  deleteMedicalPackage,
  getMedicalPackagesSelector,
  getActiveMedicalPackages,
  getAllMedicalPackages
};

export default medicalPackagesService;
