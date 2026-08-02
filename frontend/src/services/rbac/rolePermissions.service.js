/**
 * RBAC Role Permissions Service
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1
 *
 * Wraps the permission catalog / role-permission-matrix endpoints added in
 * this ticket (backend: RolePermissionAdminController,
 * /api/v1/admin/rbac/**). Real data only — no mocked catalog/roles.
 */

import axiosServices from '../../utils/axios';

const BASE_URL = '/admin/rbac';

export const rolePermissionsService = {
  /**
   * Get the permission catalog grouped by group_name.
   * GET /api/v1/admin/rbac/permissions/grouped
   */
  getGroupedPermissions: async () => {
    const response = await axiosServices.get(`${BASE_URL}/permissions/grouped`);
    return response?.data?.data || response?.data || [];
  },

  /**
   * Get role summary cards (display names, permission counts, user counts,
   * editability).
   * GET /api/v1/admin/rbac/roles
   */
  getRoles: async () => {
    const response = await axiosServices.get(`${BASE_URL}/roles`);
    return response?.data?.data || response?.data || [];
  },

  /**
   * Get a role's current permission codes.
   * GET /api/v1/admin/rbac/roles/{role}/permissions
   */
  getRolePermissions: async (role) => {
    const response = await axiosServices.get(`${BASE_URL}/roles/${role}/permissions`);
    const data = response?.data?.data || response?.data || [];
    return Array.isArray(data) ? data : Array.from(data);
  },

  /**
   * Replace a role's complete permission set.
   * PUT /api/v1/admin/rbac/roles/{role}/permissions
   *
   * SUPER_ADMIN's permission set is fixed and rejected by the backend (400/403).
   * WAAD_ADMIN cannot toggle critical-security permissions for any role (403).
   */
  updateRolePermissions: async (role, permissionCodes) => {
    const response = await axiosServices.put(`${BASE_URL}/roles/${role}/permissions`, { permissionCodes });
    return response?.data;
  },

  /**
   * Paginated, filterable RBAC audit trail (role-permission changes,
   * per-user overrides, login/logout) — WAAD-RBAC-USERS-ROLES-PERMISSIONS-COMPLETION-1.
   * GET /api/v1/admin/rbac/audit-log
   */
  getAuditLog: async ({ action, userId, from, to, page = 0, size = 20 } = {}) => {
    const params = { page, size };
    if (action) params.action = action;
    if (userId) params.userId = userId;
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await axiosServices.get(`${BASE_URL}/audit-log`, { params });
    return response?.data?.data || response?.data;
  }
};

export default rolePermissionsService;
