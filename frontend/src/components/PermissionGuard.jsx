import useAuth from 'hooks/useAuth';
import { Navigate } from 'react-router-dom';
import { ROLE_RESOURCE_ACCESS } from 'config/roleAccessMap';

/**
 * Get user's primary role from user object
 */
const getUserRole = (user) => {
  if (!user) return null;

  const normalizeRole = (value) => {
    if (typeof value === 'string') return value.trim().toUpperCase().replace(/\s+/g, '_');
    if (value && typeof value === 'object') {
      return normalizeRole(value.name || value.role || (Array.isArray(value.roles) ? value.roles[0] : null));
    }
    return null;
  };

  if (user.role) return normalizeRole(user.role);
  if (Array.isArray(user.roles) && user.roles.length > 0) {
    return normalizeRole(user.roles[0]);
  }
  return null;
};

const isSuperAdminUser = (user) => getUserRole(user) === 'SUPER_ADMIN';

// Unknown/unmapped roles resolve to an empty resource list here, which means
// hasResourceAccess() below correctly fails closed for them rather than
// silently granting access — RBAC-ROUTE-GUARD-HARDENING-2 requirement #5.
const hasResourceAccess = (role, resource) => {
  const allowedResources = ROLE_RESOURCE_ACCESS[role] || [];
  if (allowedResources.includes('*')) return true;
  return allowedResources.includes(resource);
};

/**
 * RoleGuard — RBAC-ROUTE-GUARD-HARDENING-2
 *
 * Real route/component-level enforcement, reusing the same ROLE_RESOURCE_ACCESS
 * map that already drives menu visibility (config/roleAccessMap.js), so "what
 * can I see in the menu" and "what can I open by URL" are the same answer.
 *
 * Usage:
 *
 * 1. Resource-based route protection (preferred — matches menu visibility):
 *    <RoleGuard resource="claims" isRouteGuard>
 *      <ClaimsPage />
 *    </RoleGuard>
 *
 * 2. Explicit role-list route protection (for routes with no natural menu resource):
 *    <RoleGuard allowedRoles={['SUPER_ADMIN', 'ACCOUNTANT']} isRouteGuard>
 *      <SettlementPage />
 *    </RoleGuard>
 *
 * 3. Deliberately role-agnostic route (any authenticated user, e.g. own profile):
 *    <RoleGuard authOnly isRouteGuard>
 *      <ProfilePage />
 *    </RoleGuard>
 *
 * 4. Component-level protection (hide, don't redirect, if unauthorized):
 *    <RoleGuard resource="users">
 *      <DeleteUserButton />
 *    </RoleGuard>
 *
 * A guard with none of `resource` / `allowedRoles` / `authOnly` is an
 * RBAC_UNCLASSIFIED_ROUTE: it keeps today's pre-hardening behavior (any
 * authenticated user passes) rather than silently becoming either more or
 * less permissive, but logs a dev-only warning so unclassified routes stay
 * visible instead of being forgotten. See docs/rbac/RBAC-ROUTE-GUARD-HARDENING-2-REPORT.md.
 */
const RoleGuard = ({
  allowedRoles,
  resource,
  action = 'view', // eslint-disable-line no-unused-vars -- accepted for forward compat; ROLE_RESOURCE_ACCESS is resource-only today, see report §1
  authOnly = false,
  isRouteGuard = false,
  children,
  fallback = null
}) => {
  const { user, authStatus } = useAuth();

  if (authStatus === 'INITIALIZING') return null;

  if (!user) {
    return isRouteGuard ? <Navigate to="/login" replace /> : fallback;
  }

  // SUPER_ADMIN bypasses all role/resource checks — unchanged from before.
  if (isSuperAdminUser(user)) {
    return children;
  }

  const userRole = getUserRole(user);
  const hasAllowedRoles = Array.isArray(allowedRoles) && allowedRoles.length > 0;
  const hasResource = Boolean(resource);

  if (hasAllowedRoles || hasResource) {
    const roleOk = !hasAllowedRoles || allowedRoles.includes(userRole);
    const resourceOk = !hasResource || hasResourceAccess(userRole, resource);

    if (roleOk && resourceOk) {
      return children;
    }

    // Denied: redirect to a route every authenticated user can always reach
    // (/403 carries no guard of its own — confirmed in
    // RBAC-ROUTE-GUARD-HARDENING-1-REPORT.md §4), instead of the previous
    // getDefaultRouteForRole(), which was found to map some roles (e.g.
    // ACCOUNTANT → /settlement/batches, EMPLOYER_ADMIN → /member-portal/family)
    // to routes that don't exist in MainRoutes.jsx — i.e. "redirect to a page
    // they also cannot access," exactly what this ticket's UX requirement
    // says to avoid.
    return isRouteGuard ? <Navigate to="/403" replace /> : fallback;
  }

  if (authOnly) {
    return children;
  }

  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.warn(
      '[RBAC_UNCLASSIFIED_ROUTE] A PermissionGuard/RoleGuard instance has no resource, allowedRoles, or authOnly — ' +
        'it currently allows any authenticated user through unchanged from pre-hardening behavior. ' +
        'Classify it (add resource/allowedRoles) or mark it authOnly if that is intentional. ' +
        'See docs/rbac/RBAC-ROUTE-GUARD-HARDENING-2-REPORT.md §6.'
    );
  }

  return children;
};

/**
 * Hook: check if current user has access to a resource and/or one of the
 * allowed roles. SUPER_ADMIN always returns true. Mirrors RoleGuard's logic
 * for use outside of JSX guarding (e.g. conditionally rendering a button).
 */
export const useHasRole = (allowedRoles, resource) => {
  const { user } = useAuth();
  if (!user) return false;
  if (isSuperAdminUser(user)) return true;

  const userRole = getUserRole(user);
  const hasAllowedRoles = Array.isArray(allowedRoles) && allowedRoles.length > 0;
  const hasResource = Boolean(resource);

  if (!hasAllowedRoles && !hasResource) return true;

  const roleOk = !hasAllowedRoles || allowedRoles.includes(userRole);
  const resourceOk = !hasResource || hasResourceAccess(userRole, resource);
  return roleOk && resourceOk;
};

// Default export = RoleGuard
// Also export as PermissionGuard for backward compatibility
export default RoleGuard;
