/**
 * WAAD-RBAC-REPORT-PERMISSIONS-GRANULARITY-1
 *
 * Report domain access check used by the Reports Center pages
 * (pages/reports/index.jsx, pages/reports/ReportsDomainPage.jsx) so that
 * disabling a domain's `reports.*` permission in the Roles & Permissions
 * matrix actually hides that domain, not just the direct report route.
 *
 * Mirrors PermissionGuard/RoleGuard's own precedence: a definite
 * true/false from the session's effective-permissions list wins; an absent
 * signal (no `user.permissions`, e.g. a pre-rollout cached session) falls
 * back to the coarser role/resource check in roleAccessMap.js.
 */

import useAuth from 'hooks/useAuth';
import { ROLE_RESOURCE_ACCESS } from 'config/roleAccessMap';
import { getReportDomains } from './reportEngine';

const getUserRole = (user) => user?.role || (Array.isArray(user?.roles) && user.roles[0]) || null;

export const useDomainAccess = () => {
  const { user } = useAuth();
  const role = getUserRole(user);
  const isSuperAdmin = role === 'SUPER_ADMIN';
  const allowedResources = ROLE_RESOURCE_ACCESS[role] || [];
  const effectivePermissions = Array.isArray(user?.permissions) ? user.permissions : [];
  const hasPermissionSignal = effectivePermissions.length > 0;

  return (domain) => {
    if (!domain) return false;
    if (isSuperAdmin) return true;
    if (allowedResources.includes('*')) return true;
    // permission is authoritative when the session has a definite signal —
    // it can widen access beyond the static resource ceiling, not just
    // narrow it (see the matching fix in menu-items/components.jsx).
    if (domain.permission && hasPermissionSignal) return effectivePermissions.includes(domain.permission);
    return allowedResources.includes(domain.resource);
  };
};

export const useVisibleReportDomains = () => {
  const hasDomainAccess = useDomainAccess();
  return getReportDomains().filter(hasDomainAccess);
};
