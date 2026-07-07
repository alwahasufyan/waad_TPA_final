import axiosClient from 'utils/axios';

/**
 * Medical Catalog API — read-only unified catalog view.
 * Backend: /api/v1/medical-catalog
 */

/**
 * Fetch full catalog as hierarchical category-service tree.
 * Each category node has `.services[]`, each service may have `.specialtyId/.specialtyCode/.specialtyNameAr`.
 * @returns {Promise<Array>} categories, each with `.services[]`
 */
export const getCatalogTree = async () => {
  const res = await axiosClient.get('/medical-catalog/tree');
  return res.data?.data ?? [];
};

/**
 * Search catalog across code, nameAr, nameEn and aliases.
 * @param {string} q  search query
 * @returns {Promise<Array>} flat list of up to 50 matching services
 */
export const searchCatalog = async (q) => {
  const res = await axiosClient.get('/medical-catalog/search', { params: { q } });
  return res.data?.data ?? [];
};

/**
 * Fetch all active medical specialties for the dropdown filter.
 * Backend: /api/v1/medical-specialties
 * @returns {Promise<Array>} list of { id, code, nameAr, nameEn }
 */
export const getSpecialties = async () => {
  const res = await axiosClient.get('/medical-specialties');
  return res.data?.data ?? [];
};
