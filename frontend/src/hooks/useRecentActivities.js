import { useState, useEffect, useCallback } from 'react';
import { getRecentActivities } from 'services/api/dashboard.service';

/**
 * Hook for fetching recent activities
 *
 * Uses dedicated dashboard endpoint: GET /api/dashboard/recent-activities
 * Returns recent system activities for timeline display.
 *
 * @param {number} limit - Maximum number of activities to return (default: 10)
 * @returns {Object} { activities, loading, error, refresh }
 */
export const useRecentActivities = (limit = 10) => {
  const [activities, setActivities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchActivities = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getRecentActivities(limit);
      setActivities(Array.isArray(data) ? data : []);
    } catch (err) {
      const errorMessage = err.response?.data?.message || err.message || 'فشل تحميل الأنشطة الأخيرة';
      setError(errorMessage);
      console.error('Error fetching recent activities:', err);
    } finally {
      setLoading(false);
    }
  }, [limit]);

  useEffect(() => {
    fetchActivities();
  }, [fetchActivities]);

  return {
    activities,
    loading,
    error,
    refresh: fetchActivities
  };
};
