import { useState, useEffect, useCallback } from 'react';
import preApprovalsService from 'services/api/pre-approvals.service';

/**
 * Hook for managing paginated pre-approvals list
 * @param {Object} initialParams - Initial query parameters
 * @returns {Object} - {data, loading, error, params, setParams, refresh}
 */
export const usePreApprovalsList = (initialParams = { page: 1, size: 10 }) => {
  const [data, setData] = useState({ items: [], total: 0, page: 1, size: 10 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [params, setParams] = useState(initialParams);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const result = await preApprovalsService.getAll(params);
      // Defensive: ensure result has expected shape
      setData({
        items: Array.isArray(result?.items) ? result.items : Array.isArray(result) ? result : [],
        total: result?.total ?? result?.totalElements ?? 0,
        page: result?.page ?? params.page,
        size: result?.size ?? params.size
      });
    } catch (err) {
      setError(err.response?.data?.message || 'فشل تحميل الموافقات المسبقة');
      console.error('Error fetching pre-approvals:', err);
      // Set safe default on error
      setData({ items: [], total: 0, page: params.page, size: params.size });
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

/**
 * Hook for managing single pre-approval details
 * @param {number} id - Pre-approval ID
 * @returns {Object} - {preApproval, loading, error, refresh}
 */
export const usePreApprovalDetails = (id) => {
  const [preApproval, setPreApproval] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchData = useCallback(async () => {
    if (!id) return;
    try {
      setLoading(true);
      setError(null);
      const result = await preApprovalsService.getById(id);
      setPreApproval(result);
    } catch (err) {
      setError(err.response?.data?.message || 'فشل تحميل تفاصيل الموافقة المسبقة');
      console.error('Error fetching pre-approval:', err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return {
    preApproval,
    loading,
    error,
    refresh: fetchData
  };
};

/**
 * Hook for creating pre-approval
 * @returns {Object} - {create, creating, error}
 */
export const useCreatePreApproval = () => {
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);

  const create = async (data) => {
    try {
      setCreating(true);
      setError(null);
      const result = await preApprovalsService.create(data);
      return result;
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'فشل إنشاء الموافقة المسبقة';
      setError(errorMsg);
      throw new Error(errorMsg);
    } finally {
      setCreating(false);
    }
  };

  return { create, creating, error };
};

/**
 * Hook for updating pre-approval
 * @returns {Object} - {update, updating, error}
 */
export const useUpdatePreApproval = () => {
  const [updating, setUpdating] = useState(false);
  const [error, setError] = useState(null);

  const update = async (id, data) => {
    try {
      setUpdating(true);
      setError(null);
      const result = await preApprovalsService.update(id, data);
      return result;
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'فشل تحديث الموافقة المسبقة';
      setError(errorMsg);
      throw new Error(errorMsg);
    } finally {
      setUpdating(false);
    }
  };

  return { update, updating, error };
};

/**
 * Hook for deleting pre-approval
 * @returns {Object} - {remove, deleting, error}
 */
export const useDeletePreApproval = () => {
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState(null);

  const remove = async (id) => {
    try {
      setDeleting(true);
      setError(null);
      await preApprovalsService.remove(id);
      return true;
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'فشل حذف الموافقة المسبقة';
      setError(errorMsg);
      throw new Error(errorMsg);
    } finally {
      setDeleting(false);
    }
  };

  return { remove, deleting, error };
};
