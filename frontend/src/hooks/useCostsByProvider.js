import { useState, useEffect, useCallback } from 'react';
import { getCostsByProvider } from 'services/api/dashboard.service';

/**
 * Hook for fetching costs by provider
 *
 * Uses dedicated dashboard endpoint: GET /api/dashboard/cost-by-provider
 * Returns top N providers by total cost.
 *
 * @param {number} limit - Maximum number of providers to return (default: 10)
 * @returns {Object} { costs, loading, error, refresh }
 */
export const useCostsByProvider = (limit = 10) => {
  const [costs, setCosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchCosts = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getCostsByProvider(limit);
      setCosts(Array.isArray(data) ? data : []);
    } catch (err) {
      const errorMessage = err.response?.data?.message || err.message || 'فشل تحميل التكاليف حسب مقدم الخدمة';
      setError(errorMessage);
      console.error('Error fetching costs by provider:', err);
    } finally {
      setLoading(false);
    }
  }, [limit]);

  useEffect(() => {
    fetchCosts();
  }, [fetchCosts]);

  return {
    costs,
    loading,
    error,
    refresh: fetchCosts
  };
};
