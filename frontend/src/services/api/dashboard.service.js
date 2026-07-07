import axiosClient from 'utils/axios';

/**
 * ============================================================================
 * Dashboard API Service
 * ============================================================================
 *
 * CONTRACT: DASHBOARD_API_CONTRACT.md
 *
 * Provides dedicated endpoints for dashboard statistics and analytics.
 * All calculations are done server-side using JPQL aggregations.
 *
 * DTOs per Contract:
 * - DashboardSummaryDto: totalMembers, activeMembers, totalClaims, openClaims,
 *                        approvedClaims, totalProviders, activeProviders,
 *                        totalContracts, activeContracts, totalMedicalCost, monthlyGrowth
 * - MonthlyTrendDto: month ("YYYY-MM"), count, amount?
 * - CostByProviderDto: providerId, providerName, totalCost, claimCount
 * - ServiceDistributionDto: serviceType, serviceName, count, percentage
 * - RecentActivityDto: id, type, title, description, entityName, timestamp
 *
 * Endpoints per Contract:
 * - GET /api/dashboard/summary              - ملخص KPIs
 * - GET /api/dashboard/monthly-trends       - الاتجاهات الشهرية
 * - GET /api/dashboard/members-growth       - نمو الأعضاء
 * - GET /api/dashboard/cost-by-provider     - التكاليف حسب المزود
 * - GET /api/dashboard/service-distribution - توزيع الخدمات
 * - GET /api/dashboard/recent-activities    - الأنشطة الأخيرة
 *
 * @updated 2026-01-13 - Aligned with DASHBOARD_API_CONTRACT.md
 */

const BASE_URL = '/dashboard';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Get dashboard summary statistics (KPIs)
 * CONTRACT: GET /api/dashboard/summary
 *
 * @param {number|null} employerId - Optional employer ID for filtering
 * @returns {Promise<DashboardSummaryDto>}
 */
export const getDashboardSummary = async (employerId = null) => {
  try {
    const params = {};
    if (employerId) {
      params.employerId = employerId;
    }

    const response = await axiosClient.get(`${BASE_URL}/summary`, { params });
    return unwrap(response);
  } catch (error) {
    console.error('Error fetching dashboard summary:', error);
    throw error;
  }
};

/**
 * Get monthly trends for claims
 * CONTRACT: GET /api/dashboard/monthly-trends
 *
 * @param {number} months - Number of months to retrieve (default: 12)
 * @param {number|null} employerId - Optional employer ID for filtering
 * @returns {Promise<MonthlyTrendDto[]>}
 */
export const getMonthlyTrends = async (months = 12, employerId = null) => {
  try {
    const params = { months };
    if (employerId) {
      params.employerId = employerId;
    }

    const response = await axiosClient.get(`${BASE_URL}/monthly-trends`, { params });
    return unwrap(response);
  } catch (error) {
    console.error('Error fetching monthly trends:', error);
    throw error;
  }
};

/**
 * Get members monthly growth
 * CONTRACT: GET /api/dashboard/members-growth
 *
 * @param {number} months - Number of months to retrieve (default: 12)
 * @returns {Promise<MonthlyTrendDto[]>}
 */
export const getMembersGrowth = async (months = 12) => {
  try {
    const response = await axiosClient.get(`${BASE_URL}/members-growth`, {
      params: { months }
    });
    return unwrap(response);
  } catch (error) {
    console.error('Error fetching members growth:', error);
    throw error;
  }
};

/**
 * Get costs aggregated by provider
 * CONTRACT: GET /api/dashboard/cost-by-provider
 *
 * @param {number} limit - Maximum number of providers to return (default: 10)
 * @returns {Promise<CostByProviderDto[]>}
 */
export const getCostsByProvider = async (limit = 10) => {
  try {
    const response = await axiosClient.get(`${BASE_URL}/cost-by-provider`, {
      params: { limit }
    });
    return unwrap(response);
  } catch (error) {
    console.error('Error fetching costs by provider:', error);
    throw error;
  }
};

/**
 * Get service distribution
 * CONTRACT: GET /api/dashboard/service-distribution
 *
 * @returns {Promise<ServiceDistributionDto[]>}
 */
export const getServiceDistribution = async () => {
  try {
    const response = await axiosClient.get(`${BASE_URL}/service-distribution`);
    return unwrap(response);
  } catch (error) {
    console.error('Error fetching service distribution:', error);
    throw error;
  }
};

/**
 * Get recent activities
 * CONTRACT: GET /api/dashboard/recent-activities
 *
 * @param {number} limit - Maximum number of activities to return (default: 10)
 * @returns {Promise<RecentActivityDto[]>}
 */
export const getRecentActivities = async (limit = 10) => {
  try {
    const response = await axiosClient.get(`${BASE_URL}/recent-activities`, {
      params: { limit }
    });
    return unwrap(response);
  } catch (error) {
    console.error('Error fetching recent activities:', error);
    throw error;
  }
};

/**
 * Dashboard Service Export
 */
const dashboardService = {
  getDashboardSummary,
  getMonthlyTrends,
  getMembersGrowth,
  getCostsByProvider,
  getServiceDistribution,
  getRecentActivities
};

export default dashboardService;
