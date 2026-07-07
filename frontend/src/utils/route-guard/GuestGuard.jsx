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
      navigate(location?.state?.from ? location?.state?.from : getDefaultRouteForRole(user?.role), {
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
