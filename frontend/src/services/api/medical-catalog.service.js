import axiosClient from 'utils/axios';

/**
 * Medical Catalog Hierarchy Service
 * Provides the 3-level taxonomy tree: Category → Specialty → Service
 * Backend: MedicalCatalogController (hierarchy endpoints)
 */
const BASE_URL = '/medical-catalog';

const unwrap = (response) => response.data?.data ?? response.data;

/** Get full catalog hierarchy tree */
export const getCatalogHierarchy = async () => {
  const response = await axiosClient.get(`${BASE_URL}/hierarchy`);
  return unwrap(response);
};

/** Get single category node with specialties and services */
export const getCategoryHierarchyNode = async (categoryId) => {
  const response = await axiosClient.get(`${BASE_URL}/hierarchy/${categoryId}`);
  return unwrap(response);
};

/** Get flat catalog tree (existing endpoint) */
export const getCatalogTree = async () => {
  const response = await axiosClient.get(`${BASE_URL}/tree`);
  return unwrap(response);
};

/** Search medical services in catalog */
export const searchCatalog = async (q) => {
  const response = await axiosClient.get(`${BASE_URL}/search`, { params: { q } });
  return unwrap(response);
};
