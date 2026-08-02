/**
 * Role-Based Landing Page Routes
 * Phase 5.5: Critical Stabilization
 *
 * Maps each role to its primary landing page to eliminate post-login navigation confusion
 */

import { getAccessiblePages } from './accessiblePages';

/**
 * Get the default landing page route for a given role
 * @param {string} role - User role (SUPER_ADMIN, ACCOUNTANT, MEDICAL_REVIEWER, PROVIDER_STAFF, EMPLOYER_ADMIN)
 * @returns {string} - Route path for the role's primary landing page
 */
const normalizeRole = (input) => {
  if (!input) return '';

  if (typeof input === 'string') {
    return input.trim().toUpperCase().replace(/\s+/g, '_');
  }

  if (typeof input === 'object') {
    if (typeof input.name === 'string') {
      return input.name.trim().toUpperCase().replace(/\s+/g, '_');
    }

    if (typeof input.role === 'string') {
      return input.role.trim().toUpperCase().replace(/\s+/g, '_');
    }

    if (Array.isArray(input.roles) && input.roles.length > 0) {
      return normalizeRole(input.roles[0]);
    }
  }

  return '';
};

export const getDefaultRouteForRole = (role) => {
  // Provider sessions are bound to a provider entity. Use that binding as
  // the strongest landing signal so legacy payloads that omit `role` or use
  // a different role field can never fall through to the admin dashboard.
  if (role && typeof role === 'object' && role.providerId) return '/provider';

  const normalizedRole = normalizeRole(role);

  const roleRoutes = {
    SUPER_ADMIN: '/claims/batches',
    // WAAD-RBAC-PHASE-3A: DashboardController now accepts WAAD_ADMIN, so it
    // lands like SUPER_ADMIN's operational peers — on the dashboard.
    WAAD_ADMIN: '/dashboard',
    ACCOUNTANT: '/settlement/batches',
    // The reviewer starts from the operational inbox, where claims can be
    // reviewed immediately. The batches page is an administration view and
    // was causing an unnecessary detour (and, for some sessions, an error).
    MEDICAL_REVIEWER: '/dashboard',
    PROVIDER: '/provider',
    PROVIDER_STAFF: '/provider',
    // WAAD-RBAC-EMPLOYER-LANDING-ROUTE-FIX-1: '/member-portal/family' does not
    // exist anywhere in MainRoutes.jsx — every EMPLOYER_ADMIN was landing on a
    // dead route on login, falling through to the app's catch-all and, once
    // any per-user permission override was involved, presenting as a scary
    // 403 page. '/dashboard' is a real route and EMPLOYER_ADMIN has
    // dashboard.read by default (V101 role_permissions seed).
    EMPLOYER_ADMIN: '/dashboard',
    DATA_ENTRY: '/claims/batches',
    ACCOUNT_MANAGER: '/claims/batches'
  };

  return roleRoutes[normalizedRole] || '/dashboard';
};

/**
 * WAAD-RBAC-PER-USER-LANDING-PAGE-1: route landed on when a user has zero
 * currently-accessible pages, or when a page-level guard denies access and
 * there is nowhere sensible to redirect instead. Reuses pages/errors/NoAccess.jsx,
 * repurposed to show only a calm "no pages available" message — never a
 * scary error screen — per that ticket's requirement 8.
 */
export const ZERO_ACCESS_ROUTE = '/403';

/**
 * WAAD-RBAC-PER-USER-LANDING-PAGE-1: the single source of truth for "where
 * should this user land" — supersedes calling getDefaultRouteForRole()
 * directly everywhere. Priority order:
 *   1. Their admin-set default landing page, IF it's still accessible under
 *      their CURRENT effective permissions (a later permission change can
 *      invalidate a previously-valid choice — always re-checked, never
 *      trusted blindly).
 *   2. The static role-based bootstrap default, IF accessible.
 *   3. The first page their effective permissions grant access to.
 *   4. ZERO_ACCESS_ROUTE, if literally nothing is accessible.
 *
 * SUPER_ADMIN bypasses all of this (always uses the static role default —
 * they have every permission by definition, so it's always accessible).
 *
 * @param {object} user - full user object from session (needs .role/.roles/.providerId/.permissions/.defaultLandingPage)
 * @returns {string} route to navigate to
 */
export const resolveLandingRoute = (user) => {
  if (!user) return '/login';

  const roleInput = user?.providerId ? user : user?.role || (Array.isArray(user?.roles) ? user.roles[0] : null);
  const normalizedRole = normalizeRole(roleInput);

  if (normalizedRole === 'SUPER_ADMIN') {
    return getDefaultRouteForRole(roleInput);
  }

  const accessiblePages = getAccessiblePages(user.permissions, false);

  if (accessiblePages.length === 0) {
    return ZERO_ACCESS_ROUTE;
  }

  if (user.defaultLandingPage && accessiblePages.some((p) => p.url === user.defaultLandingPage)) {
    return user.defaultLandingPage;
  }

  const roleDefault = getDefaultRouteForRole(roleInput);
  if (accessiblePages.some((p) => p.url === roleDefault)) {
    return roleDefault;
  }

  return accessiblePages[0].url;
};

/**
 * Check if a user should be redirected from their current path
 * @param {string} currentPath - Current route path
 * @param {string} role - User role
 * @returns {boolean} - True if redirect is needed
 */
export const shouldRedirectToLanding = (currentPath, role) => {
  // Redirect from root or login to role-specific landing
  if (currentPath === '/' || currentPath === '/login') {
    return true;
  }
  return false;
};
