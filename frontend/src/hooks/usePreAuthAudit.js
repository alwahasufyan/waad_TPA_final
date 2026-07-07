import { useState, useEffect, useCallback } from 'react';
import preAuthAuditService from 'services/api/preauth-audit.service';

/**
 * Hook for PreAuthorization Audit Trail
 * @param {Object} options - { preAuthId, action, user, days }
 */
export const usePreAuthAudit = (options = {}) => {
  const { preAuthId, action, user, days = 7 } = options;

  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [hasMore, setHasMore] = useState(true);

  const fetchAuditData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      let response;

      // Determine which API to call based on options
      if (preAuthId) {
        response = await preAuthAuditService.getAuditHistory(preAuthId, page, size);
      } else if (action) {
        response = await preAuthAuditService.getAuditsByAction(action, page, size);
      } else if (user) {
        response = await preAuthAuditService.getAuditsByUser(user, page, size);
      } else {
        response = await preAuthAuditService.getRecentAudits(days, page, size);
      }

      // Extract data from ApiResponse wrapper: { status, message, data: { content, totalPages, ... } }
      const pageData = response?.data || response;
      const content = pageData?.content || [];
      const totalPages = pageData?.totalPages || 0;

      if (page === 0) {
        setData(content);
      } else {
        setData((prev) => [...prev, ...content]);
      }

      setHasMore(page < totalPages - 1);
    } catch (err) {
      setError(err.response?.data?.message || 'فشل تحميل سجل التدقيق');
      console.error('Error fetching audit trail:', err);
    } finally {
      setLoading(false);
    }
  }, [preAuthId, action, user, days, page, size]);

  useEffect(() => {
    fetchAuditData();
  }, [fetchAuditData]);

  const loadMore = () => {
    if (!loading && hasMore) {
      setPage((prev) => prev + 1);
    }
  };

  const refresh = () => {
    setPage(0);
    setData([]);
    setHasMore(true);
  };

  return {
    data,
    loading,
    error,
    hasMore,
    loadMore,
    refresh
  };
};

/**
 * Hook for searching audit trail
 */
export const usePreAuthAuditSearch = (initialQuery = '') => {
  const [query, setQuery] = useState(initialQuery);
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const search = useCallback(async (searchQuery) => {
    if (!searchQuery || searchQuery.trim().length < 2) {
      setData([]);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await preAuthAuditService.searchAudits(searchQuery, 0, 50);
      // Extract data from ApiResponse wrapper
      const pageData = response?.data || response;
      setData(pageData?.content || []);
    } catch (err) {
      setError(err.response?.data?.message || 'فشل البحث في سجل التدقيق');
      console.error('Error searching audit trail:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (query && query.trim().length >= 2) {
      const debounce = setTimeout(() => {
        search(query);
      }, 500);
      return () => clearTimeout(debounce);
    } else {
      setData([]);
    }
  }, [query, search]);

  return {
    query,
    setQuery,
    data,
    loading,
    error,
    search
  };
};

/**
 * Hook for audit statistics
 */
export const usePreAuthAuditStats = () => {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchStats = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await preAuthAuditService.getStatistics();
      // Extract data from ApiResponse wrapper
      setStats(response?.data || response);
    } catch (err) {
      setError(err.response?.data?.message || 'فشل تحميل إحصائيات التدقيق');
      console.error('Error fetching audit statistics:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  return {
    stats,
    loading,
    error,
    refresh: fetchStats
  };
};
