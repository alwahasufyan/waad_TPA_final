/**
 * Per-user permission overrides — WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI
 *
 * Wraps /api/v1/admin/rbac/users/{userId}/overrides. Real backend data only
 * — the business rules (cannot target SUPER_ADMIN, WAAD_ADMIN cannot touch
 * critical-security permissions, reason required) are all enforced
 * server-side in EffectivePermissionService.
 */

import axiosServices from '../../utils/axios';

const baseUrl = (userId) => `/admin/rbac/users/${userId}/overrides`;

export const userOverridesService = {
  /**
   * List a user's overrides (active + historical).
   * GET /api/v1/admin/rbac/users/{userId}/overrides
   */
  getOverrides: async (userId) => {
    const response = await axiosServices.get(baseUrl(userId));
    return response?.data?.data || response?.data || [];
  },

  /**
   * Create an override (GRANT or REVOKE) for one user.
   * POST /api/v1/admin/rbac/users/{userId}/overrides
   */
  createOverride: async (userId, { permissionCode, effect, reason, expiresAt }) => {
    const response = await axiosServices.post(baseUrl(userId), { permissionCode, effect, reason, expiresAt });
    return response?.data;
  },

  /**
   * Deactivate an existing override — reverts the user to their role's
   * normal permission for that code.
   * DELETE /api/v1/admin/rbac/users/{userId}/overrides/{overrideId}
   */
  deactivateOverride: async (userId, overrideId, reason) => {
    const response = await axiosServices.delete(`${baseUrl(userId)}/${overrideId}`, { params: reason ? { reason } : {} });
    return response?.data;
  }
};

export default userOverridesService;
