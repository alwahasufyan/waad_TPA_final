/**
 * Role-Based Landing Page Routes
 * Phase 5.5: Critical Stabilization
 *
 * Maps each role to its primary landing page to eliminate post-login navigation confusion
 */

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
    ACCOUNTANT: '/settlement/batches',
    // The reviewer starts from the operational inbox, where claims can be
    // reviewed immediately. The batches page is an administration view and
    // was causing an unnecessary detour (and, for some sessions, an error).
    MEDICAL_REVIEWER: '/dashboard',
    PROVIDER: '/provider',
    PROVIDER_STAFF: '/provider',
    EMPLOYER_ADMIN: '/member-portal/family',
    DATA_ENTRY: '/claims/batches',
    ACCOUNT_MANAGER: '/claims/batches',
  };

  return roleRoutes[normalizedRole] || '/dashboard';
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
