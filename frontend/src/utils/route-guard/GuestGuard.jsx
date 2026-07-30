import PropTypes from 'prop-types';
import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

// project imports
import useAuth from 'hooks/useAuth';
import { AUTH_STATUS } from 'contexts/AuthContext';
import { getDefaultRouteForRole } from 'utils/roleRoutes';

// ==============================|| GUEST GUARD - PUBLIC ROUTES ||============================== //

export default function GuestGuard({ children }) {
  const { authStatus, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    // CRITICAL: Only redirect when we KNOW user is authenticated
    // Do NOT redirect during INITIALIZING
    if (authStatus === AUTH_STATUS.AUTHENTICATED) {
      // Session responses expose the role in `roles[]`; pass the complete
      // user object so both current and legacy response shapes are handled.
      const landingRoute = getDefaultRouteForRole(user);
      // Login is the role landing boundary. Do not reuse a stale protected
      // route (especially /403) from a previous session.
      navigate(landingRoute, {
        state: {
          from: ''
        },
        replace: true
      });
    }
  }, [authStatus, navigate, location, user?.role]);

  // Always render children (login form) during INITIALIZING and UNAUTHENTICATED
  return children;
}

GuestGuard.propTypes = {
  children: PropTypes.node
};
