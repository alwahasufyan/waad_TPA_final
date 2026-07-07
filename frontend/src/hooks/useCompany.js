import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { companyService } from 'services/api/company.service';
import { useSnackbar } from 'notistack';

/**
 * ============================================================================
 * Company React Hooks - Single Company Context
 * ============================================================================
 *
 * Custom hooks for managing company data with React Query.
 *
 * Architecture Philosophy:
 * - System operates in single-company mode
 * - useSystemCompany is the PREFERRED hook (no 404 errors)
 * - Company context is implicit and system-wide
 * - Call once at app level and cache globally
 *
 * Hooks:
 * - useSystemCompany: Get system default company (NEW - preferred)
 * - useCompanies: Fetch all companies
 * - useCompany: Fetch single company by ID
 * - useCompanyByCode: Fetch single company by code (DEPRECATED)
 * - useCreateCompany: Create new company
 * - useUpdateCompany: Update existing company
 * - useDeleteCompany: Delete company
 *
 * Features:
 * - Automatic cache invalidation
 * - Loading and error states
 * - Success/error notifications
 * - Optimistic updates
 *
 * @created 2026-01-02
 * @updated 2026-01-02 - Added system company hook
 */

// ============================================================================
// SYSTEM COMPANY (SINGLE-COMPANY CONTEXT)
// ============================================================================

/**
 * Hook to fetch the system's default company.
 *
 * This is the PREFERRED hook for single-company mode.
 * - Always returns 200 OK (never 404)
 * - No need to pass company code
 * - Cache for entire app lifetime
 * - Call once at app level (e.g., in App.jsx or Layout)
 *
 * @returns {Object} Query result with system company data
 *
 * @example
 * const { data: company, isLoading } = useSystemCompany();
 * if (company) {
 *   console.log('System company:', company.data.name);
 * }
 */
export const useSystemCompany = () => {
  return useQuery({
    queryKey: ['system', 'company'],
    queryFn: async () => {
      const response = await companyService.getSystemCompany();
      return response;
    },
    staleTime: Infinity, // Never refetch - company doesn't change
    cacheTime: Infinity, // Keep in cache forever
    retry: 3 // Retry on failure (important for startup)
  });
};

// ============================================================================
// QUERY HOOKS (Read Operations)
// ============================================================================

/**
 * Hook to fetch all companies
 * @returns {Object} Query result with companies data
 */
export const useCompanies = () => {
  return useQuery({
    queryKey: ['companies'],
    queryFn: async () => {
      const response = await companyService.getAll();
      return response;
    },
    staleTime: 5 * 60 * 1000 // 5 minutes
  });
};

/**
 * Hook to fetch single company by ID
 * @param {number} id - Company ID
 * @returns {Object} Query result with company data
 */
export const useCompany = (id) => {
  return useQuery({
    queryKey: ['company', id],
    queryFn: async () => {
      const response = await companyService.getById(id);
      return response;
    },
    enabled: !!id,
    staleTime: 5 * 60 * 1000
  });
};

/**
 * Hook to fetch single company by code
 * @deprecated Use useSystemCompany() instead for single-company mode
 * @param {string} code - Company code
 * @returns {Object} Query result with company data
 */
export const useCompanyByCode = (code) => {
  return useQuery({
    queryKey: ['company', 'code', code],
    queryFn: async () => {
      const response = await companyService.getByCode(code);
      return response;
    },
    enabled: !!code,
    staleTime: 5 * 60 * 1000
  });
};

// ============================================================================
// MUTATION HOOKS (Write Operations)
// ============================================================================

/**
 * Hook to create new company
 * @returns {Object} Mutation result
 */
export const useCreateCompany = () => {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  return useMutation({
    mutationFn: async (data) => {
      return await companyService.create(data);
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries(['companies']);
      enqueueSnackbar('تم إنشاء الشركة بنجاح', { variant: 'success' });
      return data;
    },
    onError: (error) => {
      console.error('Error creating company:', error);
      enqueueSnackbar(error.response?.data?.message || 'حدث خطأ أثناء إنشاء الشركة', {
        variant: 'error'
      });
    }
  });
};

/**
 * Hook to update existing company
 * @returns {Object} Mutation result
 */
export const useUpdateCompany = () => {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  return useMutation({
    mutationFn: async ({ id, data }) => {
      return await companyService.update(id, data);
    },
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries(['companies']);
      queryClient.invalidateQueries(['company', variables.id]);
      enqueueSnackbar('تم تحديث الشركة بنجاح', { variant: 'success' });
      return data;
    },
    onError: (error) => {
      console.error('Error updating company:', error);
      enqueueSnackbar(error.response?.data?.message || 'حدث خطأ أثناء تحديث الشركة', {
        variant: 'error'
      });
    }
  });
};

/**
 * Hook to delete company
 * @returns {Object} Mutation result
 */
export const useDeleteCompany = () => {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  return useMutation({
    mutationFn: async (id) => {
      return await companyService.delete(id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries(['companies']);
      enqueueSnackbar('تم حذف الشركة بنجاح', { variant: 'success' });
    },
    onError: (error) => {
      console.error('Error deleting company:', error);
      enqueueSnackbar(error.response?.data?.message || 'حدث خطأ أثناء حذف الشركة', {
        variant: 'error'
      });
    }
  });
};
