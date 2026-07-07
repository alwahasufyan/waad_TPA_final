import { useState, useEffect, useCallback } from 'react';
import { getServiceDistribution } from 'services/api/dashboard.service';

/**
 * Hook for fetching service distribution
 *
 * Uses dedicated dashboard endpoint: GET /api/dashboard/service-distribution
 * Returns aggregated data grouped by service type.
 *
 * @returns {Object} { distribution, loading, error, refresh }
 */
export const useServiceDistribution = () => {
  const [distribution, setDistribution] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchDistribution = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getServiceDistribution();
      setDistribution(Array.isArray(data) ? data : []);
    } catch (err) {
      const errorMessage = err.response?.data?.message || err.message || 'فشل تحميل توزيع الخدمات';
      setError(errorMessage);
      console.error('Error fetching service distribution:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDistribution();
  }, [fetchDistribution]);

  return {
    distribution,
    loading,
    error,
    refresh: fetchDistribution
  };
};
