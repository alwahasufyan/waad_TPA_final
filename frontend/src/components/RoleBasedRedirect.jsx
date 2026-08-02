import { Navigate } from 'react-router-dom';
import useAuth from 'hooks/useAuth';
import { resolveLandingRoute } from 'utils/roleRoutes';

/**
 * RoleBasedRedirect Component
 * Phase 5.5: Critical Stabilization
 *
 * Smart root "/" handler that redirects based on authentication status:
 * - Unauthenticated users → /login
 * - Authenticated users → role-specific landing page
 */
export default function RoleBasedRedirect() {
  const { authStatus, user } = useAuth();

  // AuthContext is session-based and deliberately does not expose the old
  // isLoggedIn flag. Wait for the session check before choosing a landing
  // route; otherwise the root route can briefly choose an unauthorized
  // dashboard and end on 403 after a successful provider login.
  if (authStatus === 'INITIALIZING') return null;

  // If not logged in, redirect to login
  if (authStatus !== 'AUTHENTICATED' || !user) {
    return <Navigate to="/login" replace />;
  }

  // If logged in, redirect to their resolved landing page (per-user default,
  // falling back to role-based default, falling back to first accessible page)
  const landingRoute = resolveLandingRoute(user);
  return <Navigate to={landingRoute} replace />;
}
