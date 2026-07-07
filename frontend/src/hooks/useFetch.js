import { useState, useEffect, useCallback } from 'react';

/**
 * Custom hook for data fetching with loading, error, and retry logic
 * @param {Function} fetchFn - Async function that fetches data
 * @param {Array} deps - Dependencies array for useEffect
 * @param {Object} options - { initialData, skip }
 * @returns {Object} - { data, loading, error, retry, refetch }
 */
export default function useFetch(fetchFn, deps = [], options = {}) {
  const { initialData = null, skip = false } = options;

  const [data, setData] = useState(initialData);
  const [loading, setLoading] = useState(!skip);
  const [error, setError] = useState(null);
  const [retryCount, setRetryCount] = useState(0);

  const fetchData = useCallback(async () => {
    if (skip) return;

    try {
      setLoading(true);
      setError(null);
      const result = await fetchFn();

      if (result?.success) {
        setData(result.data);
      } else {
        setError(result?.error || 'An error occurred');
      }
    } catch (err) {
      setError(err.message || 'Network error');
    } finally {
      setLoading(false);
    }
  }, [fetchFn, skip, ...deps]);

  useEffect(() => {
    fetchData();
  }, [fetchData, retryCount]);

  const retry = useCallback(() => {
    setRetryCount((prev) => prev + 1);
  }, []);

  const refetch = useCallback(() => {
    fetchData();
  }, [fetchData]);

  return {
    data,
    loading,
    error,
    retry,
    refetch
  };
}
