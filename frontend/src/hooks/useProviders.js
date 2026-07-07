import { useState, useEffect, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { providersService } from 'services/api';

export const useProvidersList = (initialParams = { page: 0, size: 10 }) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [params, setParams] = useState(initialParams);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await providersService.getAll(params);
      setData(result);
    } catch (err) {
      setError(err.message || 'فشل تحميل المزودين');
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return {
    data,
    loading,
    error,
    params,
    setParams,
    refresh: fetchData
  };
};

export const useProviderDetails = (id) => {
  const [provider, setProvider] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchProvider = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const result = await providersService.getById(id);
      setProvider(result);
    } catch (err) {
      setError(err.message || 'فشل تحميل تفاصيل المزود');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchProvider();
  }, [fetchProvider]);

  return {
    provider,
    loading,
    error,
    refresh: fetchProvider
  };
};

export const useCreateProvider = () => {
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);
  const queryClient = useQueryClient();

  const create = async (data) => {
    setCreating(true);
    setError(null);
    try {
      const result = await providersService.create(data);
      // Invalidate providers cache to refresh list
      console.log('[useCreateProvider] Provider created, invalidating cache');
      queryClient.invalidateQueries({ queryKey: ['providers'] });
      return { success: true, data: result };
    } catch (err) {
      const errorMsg = err.message || 'فشل إنشاء المزود';
      setError(errorMsg);
      return { success: false, error: errorMsg };
    } finally {
      setCreating(false);
    }
  };

  return { create, creating, error };
};

export const useUpdateProvider = () => {
  const [updating, setUpdating] = useState(false);
  const [error, setError] = useState(null);
  const queryClient = useQueryClient();

  const update = async (id, data) => {
    setUpdating(true);
    setError(null);
    try {
      const result = await providersService.update(id, data);
      queryClient.invalidateQueries({ queryKey: ['providers'] });
      return { success: true, data: result };
    } catch (err) {
      const errorMsg = err.message || 'فشل تحديث المزود';
      setError(errorMsg);
      return { success: false, error: errorMsg };
    } finally {
      setUpdating(false);
    }
  };

  return { update, updating, error };
};

export const useDeleteProvider = () => {
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState(null);
  const queryClient = useQueryClient();

  const remove = async (id) => {
    setDeleting(true);
    setError(null);
    try {
      await providersService.remove(id);
      queryClient.invalidateQueries({ queryKey: ['providers'] });
      return { success: true };
    } catch (err) {
      const errorMsg = err.message || 'فشل حذف المزود';
      setError(errorMsg);
      return { success: false, error: errorMsg };
    } finally {
      setDeleting(false);
    }
  };

  return { remove, deleting, error };
};

/**
 * Hook for fetching all providers (for dropdowns)
 * @returns {Object} { data, loading, error, refresh }
 */
export const useAllProviders = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await providersService.getAll();
      setData(Array.isArray(response?.data) ? response.data : Array.isArray(response) ? response : []);
    } catch (err) {
      console.error('[useProviders] Failed to load all providers:', err);
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

/**
 * Hook for fetching provider selector options (for dropdowns/autocomplete)
 * Endpoint: GET /api/providers/selector
 * @returns {Object} { data, loading, error, refresh }
 */
export const useProviderSelector = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await providersService.getSelector();
      setData(Array.isArray(response) ? response : []);
    } catch (err) {
      console.error('[useProviders] Failed to load provider selectors:', err);
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
