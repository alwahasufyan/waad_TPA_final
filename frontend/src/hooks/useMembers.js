import { useEffect, useState, useCallback } from 'react';
import { getAllMembers, getMember } from 'services/api/unified-members.service';

/**
 * Hook for fetching paginated members list
 * Uses unified-members.service.js (backend: /api/unified-members)
 *
 * @param {Object} initialParams - Initial query parameters
 * @returns {Object} { data, loading, error, params, setParams, refresh }
 */
export const useMembersList = (initialParams = {}) => {
  const [params, setParams] = useState({
    page: 0, // Backend uses 0-based pagination
    size: 20,
    sortBy: 'createdAt',
    sortDir: 'desc',
    search: '',
    ...initialParams
  });

  const [data, setData] = useState({
    items: [],
    total: 0,
    page: 0,
    size: 20
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      // Backend returns Spring Page: { content, totalElements, number, size, ... }
      // FIX: Controller expects 'sort' and 'direction' as separate parameters
      const response = await getAllMembers({
        page: params.page,
        size: params.size,
        sort: params.sortBy || 'id',
        direction: (params.sortDir || 'desc').toUpperCase(),
        search: params.search || undefined,
        employerId: params.employerId || undefined
      });

      // Map Spring Page response to frontend format
      setData({
        items: response?.content || [],
        total: response?.totalElements || 0,
        page: response?.number || 0,
        size: response?.size || params.size
      });
    } catch (err) {
      console.error('[useMembers] Failed to load members list:', err);
      setError(err);
      setData({ items: [], total: 0, page: params.page, size: params.size });
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = useCallback(() => {
    load();
  }, [load]);

  return {
    data,
    loading,
    error,
    params,
    setParams,
    refresh
  };
};

/**
 * Hook for fetching single member details
 * Uses unified-members.service.js (backend: /api/unified-members/{id})
 *
 * @param {number} id - Member ID
 * @returns {Object} { data, loading, error, refresh }
 */
export const useMemberDetails = (id) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    if (!id) return;

    try {
      setLoading(true);
      setError(null);
      const response = await getMember(id);

      // Transform MemberViewDto to form-friendly format
      const memberData = {
        ...response,
        // Ensure dates are in correct format for date pickers
        birthDate: response.birthDate || null,
        joinDate: response.joinDate || null,
        startDate: response.startDate || null,
        endDate: response.endDate || null,
        // Ensure enums are uppercase strings
        gender: response.gender || '',
        maritalStatus: response.maritalStatus || '',
        status: response.status || '',
        cardStatus: response.cardStatus || '',
        // Ensure dependents array exists
        dependents: response.dependents || []
      };

      setData(memberData);
    } catch (err) {
      console.error('[useMembers] Failed to load member details:', err);
      setError(err);
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = useCallback(() => {
    load();
  }, [load]);

  return {
    data,
    loading,
    error,
    refresh
  };
};

/**
 * Hook for fetching all members (for dropdowns)
 * Uses unified-members.service.js
 *
 * @returns {Object} { data, loading, error, refresh }
 */
export const useAllMembers = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await getAllMembers({ page: 0, size: 1000 });
      setData(Array.isArray(response?.content) ? response.content : []);
    } catch (err) {
      console.error('[useMembers] Failed to load all members:', err);
      setError(err);
      setData([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = useCallback(() => {
    load();
  }, [load]);

  return {
    data,
    loading,
    error,
    refresh
  };
};
