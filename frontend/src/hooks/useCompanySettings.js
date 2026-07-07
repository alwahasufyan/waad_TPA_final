import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { companySettingsService } from 'services/api/companySettings.service';
import { useSnackbar } from 'notistack';

/**
 * ============================================================================
 * Company Settings React Hooks
 * ============================================================================
 *
 * Custom hooks for managing employer settings and UI visibility controls.
 *
 * Hooks:
 * - useEmployerSettings: Fetch employer settings
 * - useUpdateEmployerSettings: Update employer permissions
 * - useEmployerUiVisibility: Fetch UI visibility settings
 * - useUpdateEmployerUiVisibility: Update UI visibility
 *
 * Features:
 * - Automatic cache invalidation
 * - Loading and error states
 * - Success/error notifications
 * - Granular control over employer permissions
 *
 * @created 2026-01-02
 */

// ============================================================================
// EMPLOYER SETTINGS HOOKS
// ============================================================================

/**
 * Hook to fetch employer settings
 * @param {number} employerId - Employer ID
 * @returns {Object} Query result with employer settings
 */
export const useEmployerSettings = (employerId) => {
  return useQuery({
    queryKey: ['employerSettings', employerId],
    queryFn: async () => {
      const response = await companySettingsService.getByEmployerId(employerId);
      return response;
    },
    enabled: !!employerId,
    staleTime: 5 * 60 * 1000 // 5 minutes
  });
};

/**
 * Hook to update employer settings
 * @returns {Object} Mutation result
 */
export const useUpdateEmployerSettings = () => {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  return useMutation({
    mutationFn: async ({ employerId, data }) => {
      return await companySettingsService.updateSettings(employerId, data);
    },
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries(['employerSettings', variables.employerId]);
      enqueueSnackbar('تم تحديث إعدادات الشريك بنجاح', { variant: 'success' });
      return data;
    },
    onError: (error) => {
      console.error('Error updating employer settings:', error);
      enqueueSnackbar(error.response?.data?.message || 'حدث خطأ أثناء تحديث الإعدادات', {
        variant: 'error'
      });
    }
  });
};

// ============================================================================
// UI VISIBILITY HOOKS
// ============================================================================

/**
 * Hook to fetch employer UI visibility settings
 * @param {number} employerId - Employer ID
 * @returns {Object} Query result with UI visibility configuration
 */
export const useEmployerUiVisibility = (employerId) => {
  return useQuery({
    queryKey: ['employerUiVisibility', employerId],
    queryFn: async () => {
      const response = await companySettingsService.getUiVisibility(employerId);
      return response;
    },
    enabled: !!employerId,
    staleTime: 5 * 60 * 1000
  });
};

/**
 * Hook to update employer UI visibility settings
 * @returns {Object} Mutation result
 */
export const useUpdateEmployerUiVisibility = () => {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  return useMutation({
    mutationFn: async ({ employerId, data }) => {
      return await companySettingsService.updateUiVisibility(employerId, data);
    },
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries(['employerUiVisibility', variables.employerId]);
      queryClient.invalidateQueries(['employerSettings', variables.employerId]);
      enqueueSnackbar('تم تحديث إعدادات واجهة المستخدم بنجاح', { variant: 'success' });
      return data;
    },
    onError: (error) => {
      console.error('Error updating UI visibility:', error);
      enqueueSnackbar(error.response?.data?.message || 'حدث خطأ أثناء تحديث إعدادات الواجهة', {
        variant: 'error'
      });
    }
  });
};
