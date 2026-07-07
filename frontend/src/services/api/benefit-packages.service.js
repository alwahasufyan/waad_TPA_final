import axiosClient from 'utils/axios';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

/**
 * Benefit Packages API Service
 * Provides CRUD operations for Benefit Packages module
 */
const BASE_URL = '/benefit-packages';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Get paginated benefit packages list
 * @param {Object} params - Query parameters
 * @returns {Promise<Object>} Paginated response
 */
export const getBenefitPackages = async (params = {}) => {
  const response = await axiosClient.get(BASE_URL, { params });
  return normalizePaginatedResponse(response);
};

/**
 * Get benefit package by ID
 * @param {number} id - Package ID
 * @returns {Promise<Object>} Package details
 */
export const getBenefitPackageById = async (id) => {
  const response = await axiosClient.get(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Create new benefit package
 * @param {Object} payload - Package data
 * @returns {Promise<Object>} Created package
 */
export const createBenefitPackage = async (payload) => {
  const response = await axiosClient.post(BASE_URL, payload);
  return unwrap(response);
};

/**
 * Update benefit package
 * @param {number} id - Package ID
 * @param {Object} payload - Updated package data
 * @returns {Promise<Object>} Updated package
 */
export const updateBenefitPackage = async (id, payload) => {
  const response = await axiosClient.put(`${BASE_URL}/${id}`, payload);
  return unwrap(response);
};

/**
 * Delete benefit package
 * @param {number} id - Package ID
 * @returns {Promise<void>}
 */
export const deleteBenefitPackage = async (id) => {
  const response = await axiosClient.delete(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Get all benefit packages (for dropdowns)
 * @returns {Promise<Array>} All packages
 */
export const getAllBenefitPackages = async () => {
  const response = await axiosClient.get(`${BASE_URL}/all`);
  return unwrap(response);
};
