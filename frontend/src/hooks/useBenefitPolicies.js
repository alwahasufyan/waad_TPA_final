import { useState, useEffect, useCallback } from 'react';
import {
  getBenefitPolicies,
  getBenefitPolicyById,
  getBenefitPoliciesSelector,
  getBenefitPoliciesSelectorByEmployer
} from 'services/api/benefit-policies.service';

/**
 * Hook for fetching benefit policies list (paginated)
 * @param {Object} params - Query parameters (page, size, sortBy, sortDir, employerId)
 * @returns {Object} { data, loading, error, refresh }
 */
export const useBenefitPoliciesList = (params = {}) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await getBenefitPolicies(params);
      setData(response);
    } catch (err) {
      console.error('[useBenefitPolicies] Failed to load benefit policies:', err);
      setError(err);
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = () => {
    load();
  };

  return {
    data,
    loading,
    error,
    refresh
  };
};

/**
 * Hook for fetching single benefit policy by ID
 * @param {number} id - Policy ID
 * @returns {Object} { data, loading, error, refresh }
 */
export const useBenefitPolicyDetails = (id) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    if (!id) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await getBenefitPolicyById(id);
      setData(response);
    } catch (err) {
      console.error('[useBenefitPolicies] Failed to load benefit policy:', err);
      setError(err);
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = () => {
    load();
  };

  return {
    data,
    loading,
    error,
    refresh
  };
};

/**
 * Hook for fetching benefit policy selector options (for dropdowns)
 * Endpoint: GET /api/benefit-policies/selector
 * @returns {Object} { data, loading, error, refresh }
 */
export const useBenefitPolicySelector = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await getBenefitPoliciesSelector();
      setData(Array.isArray(response) ? response : []);
    } catch (err) {
      console.error('[useBenefitPolicies] Failed to load benefit policy selectors:', err);
      setError(err);
      setData([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = () => {
    load();
  };

  return {
    data,
    loading,
    error,
    refresh
  };
};

/**
 * Hook for fetching benefit policy selector options for a specific employer
 * Endpoint: GET /api/benefit-policies/selector/employer/{employerId}
 * @param {number} employerId - Employer organization ID
 * @returns {Object} { data, loading, error, refresh }
 */
export const useBenefitPolicySelectorByEmployer = (employerId) => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    if (!employerId) {
      setData([]);
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await getBenefitPoliciesSelectorByEmployer(employerId);
      setData(Array.isArray(response) ? response : []);
    } catch (err) {
      console.error('[useBenefitPolicies] Failed to load benefit policy selectors for employer:', err);
      setError(err);
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [employerId]);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = () => {
    load();
  };

  return {
    data,
    loading,
    error,
    refresh
  };
};
