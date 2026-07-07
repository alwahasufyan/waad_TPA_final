/**
 * Organization Service
 * Unified service for accessing different organization types
 * (Employers, Insurance Companies, etc.)
 */

import { getEmployers } from './api/employers.service';
import companyService from './api/company.service';

/**
 * Get organizations by type
 * @param {string} type - Organization type ('EMPLOYER', 'INSURANCE', etc.)
 * @returns {Promise<{data: Array}>} Organizations of the specified type
 */
const getByType = async (type) => {
  try {
    switch (type.toUpperCase()) {
      case 'EMPLOYER':
        const employers = await getEmployers();
        return { data: employers || [] };

      case 'INSURANCE':
        // For now, return empty array as insurance orgs are not yet implemented
        // In the future, this would call an insurance organization service
        const companies = await companyService.getAll();
        // Filter companies that could be insurance companies
        return {
          data: (companies.data || companies || []).filter((c) => c.type === 'INSURANCE' || !c.type)
        };

      default:
        console.warn(`Unknown organization type: ${type}`);
        return { data: [] };
    }
  } catch (error) {
    console.error(`Error fetching organizations of type ${type}:`, error);
    return { data: [] };
  }
};

/**
 * Get all organizations
 * @returns {Promise<{data: Array}>} All organizations
 */
const getAll = async () => {
  try {
    const [employers] = await Promise.all([getEmployers().catch(() => [])]);

    return {
      data: [...(employers || []).map((e) => ({ ...e, organizationType: 'EMPLOYER' }))]
    };
  } catch (error) {
    console.error('Error fetching all organizations:', error);
    return { data: [] };
  }
};

const organizationService = {
  getByType,
  getAll
};

export default organizationService;
