import { useState, useEffect, useCallback, useMemo } from 'react';
import axiosClient from 'utils/axios';

/**
 * Claim Status Constants
 * Must match backend ClaimStatus enum exactly
 */
export const CLAIM_STATUS = {
  DRAFT: 'DRAFT',
  SUBMITTED: 'SUBMITTED',
  UNDER_REVIEW: 'UNDER_REVIEW',
  APPROVAL_IN_PROGRESS: 'APPROVAL_IN_PROGRESS',
  APPROVED: 'APPROVED',
  BATCHED: 'BATCHED',
  REJECTED: 'REJECTED',
  NEEDS_CORRECTION: 'NEEDS_CORRECTION',
  SETTLED: 'SETTLED'
};

/**
 * Claim status display order (matches state machine)
 */
export const CLAIM_STATUS_ORDER = [
  CLAIM_STATUS.DRAFT,
  CLAIM_STATUS.SUBMITTED,
  CLAIM_STATUS.UNDER_REVIEW,
  CLAIM_STATUS.APPROVAL_IN_PROGRESS,
  CLAIM_STATUS.APPROVED,
  CLAIM_STATUS.BATCHED,
  CLAIM_STATUS.REJECTED,
  CLAIM_STATUS.NEEDS_CORRECTION,
  CLAIM_STATUS.SETTLED
];

/**
 * Arabic labels for claim statuses
 */
export const CLAIM_STATUS_LABELS = {
  [CLAIM_STATUS.DRAFT]: 'مسودة',
  [CLAIM_STATUS.SUBMITTED]: 'مقدمة',
  [CLAIM_STATUS.UNDER_REVIEW]: 'قيد المراجعة',
  [CLAIM_STATUS.APPROVAL_IN_PROGRESS]: 'جاري معالجة الموافقة',
  [CLAIM_STATUS.APPROVED]: 'موافق عليها',
  [CLAIM_STATUS.BATCHED]: 'ضمن دفعة تسوية',
  [CLAIM_STATUS.REJECTED]: 'مرفوضة',
  [CLAIM_STATUS.NEEDS_CORRECTION]: 'تحتاج تصحيح',
  [CLAIM_STATUS.SETTLED]: 'تمت التسوية'
};

/**
 * Colors for claim statuses
 */
export const CLAIM_STATUS_COLORS = {
  [CLAIM_STATUS.DRAFT]: 'default',
  [CLAIM_STATUS.SUBMITTED]: 'info',
  [CLAIM_STATUS.UNDER_REVIEW]: 'warning',
  [CLAIM_STATUS.APPROVAL_IN_PROGRESS]: 'warning',
  [CLAIM_STATUS.APPROVED]: 'success',
  [CLAIM_STATUS.BATCHED]: 'secondary',
  [CLAIM_STATUS.REJECTED]: 'error',
  [CLAIM_STATUS.NEEDS_CORRECTION]: 'warning',
  [CLAIM_STATUS.SETTLED]: 'secondary'
};

/**
 * Helper to unwrap API response
 */
const unwrap = (response) => response.data?.data ?? response.data;

/**
 * useEmployerDashboardKPIs Hook
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * FINANCIAL INTEGRITY RULE
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ALL KPIs are fetched from backend aggregation endpoint.
 * NO client-side calculations of financial amounts.
 *
 * Backend Endpoint: GET /api/dashboard/summary?employerId={id}
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * @param {number|null} employerId - Employer ID for filtering (null = all for admin)
 * @returns {Object} KPI data, loading states, error, and refresh function
 */
export const useEmployerDashboardKPIs = (employerId) => {
  // State: Backend summary data
  const [summaryData, setSummaryData] = useState({
    totalMembers: 0,
    activeMembers: 0,
    totalClaims: 0,
    openClaims: 0,
    approvedClaims: 0,
    totalMedicalCost: 0,
    monthlyGrowth: 0
  });

  // State: Claims by status (from separate endpoint for detailed breakdown)
  const [claimsByStatus, setClaimsByStatus] = useState({});

  // State: Loading and error
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  /**
   * Fetch dashboard summary from backend (Single Source of Truth)
   * Endpoint: GET /api/dashboard/summary
   */
  const fetchDashboardSummary = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      // Build query params
      const params = employerId ? { employerId } : {};

      // Fetch from backend aggregation endpoint
      const response = await axiosClient.get('/dashboard/summary', { params });
      const data = unwrap(response);

      if (!data) {
        throw new Error('Invalid dashboard summary response');
      }

      // Set summary data directly from backend (NO calculations!)
      setSummaryData({
        totalMembers: data.totalMembers ?? 0,
        activeMembers: data.activeMembers ?? 0,
        totalClaims: data.totalClaims ?? 0,
        openClaims: data.openClaims ?? 0,
        approvedClaims: data.approvedClaims ?? 0,
        totalMedicalCost: parseFloat(data.totalMedicalCost) || 0,
        monthlyGrowth: parseFloat(data.monthlyGrowth) || 0
      });

      // Fetch claims status breakdown for detailed view
      await fetchClaimsStatusBreakdown();
    } catch (err) {
      console.error('❌ Failed to fetch dashboard summary:', err);
      setError(err.message || 'فشل في تحميل بيانات لوحة التحكم');

      // Reset to safe defaults
      setSummaryData({
        totalMembers: 0,
        activeMembers: 0,
        totalClaims: 0,
        openClaims: 0,
        approvedClaims: 0,
        totalMedicalCost: 0,
        monthlyGrowth: 0
      });
    } finally {
      setLoading(false);
    }
  }, [employerId]);

  /**
   * Fetch claims status breakdown from backend
   * Endpoint: GET /api/reports/financial-summary (provides statusSummaries)
   */
  const fetchClaimsStatusBreakdown = useCallback(async () => {
    try {
      const params = employerId ? { employerOrgId: employerId } : {};
      const response = await axiosClient.get('/claims/financial-summary', { params });
      const data = unwrap(response);

      // Build status counts from backend response
      const statusCounts = {};
      CLAIM_STATUS_ORDER.forEach((status) => {
        statusCounts[status] = 0;
      });

      // Map statusSummaries from backend
      if (data?.statusSummaries && Array.isArray(data.statusSummaries)) {
        data.statusSummaries.forEach((item) => {
          if (item.status && statusCounts.hasOwnProperty(item.status)) {
            statusCounts[item.status] = item.count ?? 0;
          }
        });
      }

      setClaimsByStatus(statusCounts);
    } catch (err) {
      console.error('⚠️ Failed to fetch claims status breakdown:', err);
      // Keep default empty counts - non-critical error
    }
  }, [employerId]);

  /**
   * Initial fetch and refetch on employerId change
   */
  useEffect(() => {
    fetchDashboardSummary();
  }, [fetchDashboardSummary]);

  /**
   * Computed: Claims by status array (for display)
   */
  const claimsByStatusArray = useMemo(() => {
    return CLAIM_STATUS_ORDER.map((status) => ({
      status,
      label: CLAIM_STATUS_LABELS[status],
      count: claimsByStatus[status] ?? 0,
      color: CLAIM_STATUS_COLORS[status]
    }));
  }, [claimsByStatus]);

  return {
    // Members KPIs (from backend)
    totalMembers: summaryData.totalMembers,
    activeMembers: summaryData.activeMembers,
    membersLoading: loading,
    membersError: error,

    // Visits KPIs (not available in current endpoint - use 0)
    totalVisits: 0,
    visitsLoading: loading,
    visitsError: null,

    // Claims KPIs (from backend)
    totalClaims: summaryData.totalClaims,
    claimsByStatus,
    claimsByStatusArray,
    approvedAmount: summaryData.totalMedicalCost, // Backend calls it totalMedicalCost
    rejectedAmount: 0, // Not exposed by current endpoint - would need enhancement
    claimsLoading: loading,
    claimsError: error,

    // Overall
    isLoading: loading,
    hasError: !!error,
    refresh: fetchDashboardSummary
  };
};

export default useEmployerDashboardKPIs;
