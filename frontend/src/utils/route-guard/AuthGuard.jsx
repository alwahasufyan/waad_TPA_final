import PropTypes from 'prop-types';
import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Box, CircularProgress } from '@mui/material';

// project imports
import useAuth from 'hooks/useAuth';
import { AUTH_STATUS } from 'contexts/AuthContext';

// ==============================|| AUTH GUARD - PROTECTED ROUTES ||============================== //

export default function AuthGuard({ children }) {
  const { authStatus } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    // CRITICAL: Only redirect when we KNOW user is unauthenticated
    // Do NOT redirect during INITIALIZING (prevents infinite loops)
    if (authStatus === AUTH_STATUS.UNAUTHENTICATED) {
      navigate('/login', {
        state: {
          from: location.pathname
        },
        replace: true
      });
    }
  }, [authStatus, navigate, location]);

  // Show loading during initialization
  if (authStatus === AUTH_STATUS.INITIALIZING) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh'
        }}
      >
        <CircularProgress size={60} />
      </Box>
    );
  }

  // Authenticated - render children
  return children;
}

AuthGuard.propTypes = {
  children: PropTypes.node
};
