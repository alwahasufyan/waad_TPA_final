import { useState, useEffect, useCallback } from 'react';
import { getMembersGrowth } from 'services/api/dashboard.service';

/**
 * Hook to fetch members monthly growth trends
 * Uses dedicated backend endpoint with server-side aggregations
 *
 * @param {number} months - Number of months to retrieve (default: 12)
 * @returns {Object} { growth, loading, error, refresh }
 */
export const useMembersGrowth = (months = 12) => {
  const [growth, setGrowth] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchGrowth = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getMembersGrowth(months);
      setGrowth(data || []);
    } catch (err) {
      console.error('Error fetching members growth:', err);
      setError(err.message || 'Failed to fetch members growth');
      setGrowth([]);
    } finally {
      setLoading(false);
    }
  }, [months]);

  useEffect(() => {
    fetchGrowth();
  }, [fetchGrowth]);

  const refresh = useCallback(() => {
    fetchGrowth();
  }, [fetchGrowth]);

  return {
    growth,
    loading,
    error,
    refresh
  };
};

export default useMembersGrowth;
