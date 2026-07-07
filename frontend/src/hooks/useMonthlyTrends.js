import { useState, useEffect, useCallback } from 'react';
import { getMonthlyTrends } from 'services/api/dashboard.service';
import { useEmployerFilter } from 'contexts/EmployerFilterContext';

/**
 * Hook for fetching monthly trends
 *
 * Uses dedicated dashboard endpoint: GET /api/dashboard/monthly-trends
 * Returns monthly aggregated data for charts.
 * Supports employer filter.
 *
 * @param {number} months - Number of months to retrieve (default: 12)
 * @returns {Object} { trends, loading, error, refresh }
 */
export const useMonthlyTrends = (months = 12) => {
  const { selectedEmployerId } = useEmployerFilter();
  const [trends, setTrends] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchTrends = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getMonthlyTrends(months, selectedEmployerId);
      setTrends(Array.isArray(data) ? data : []);
    } catch (err) {
      const errorMessage = err.response?.data?.message || err.message || 'فشل تحميل الاتجاهات الشهرية';
      setError(errorMessage);
      console.error('Error fetching monthly trends:', err);
    } finally {
      setLoading(false);
    }
  }, [months, selectedEmployerId]);

  useEffect(() => {
    fetchTrends();
  }, [fetchTrends]);

  return {
    trends,
    loading,
    error,
    refresh: fetchTrends
  };
};
