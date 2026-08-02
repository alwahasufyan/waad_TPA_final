import { Outlet, Navigate } from 'react-router-dom';
import useAuth from 'hooks/useAuth';
import { resolveLandingRoute } from 'utils/roleRoutes';

// ==============================|| LAYOUT - AUTH - SIMPLIFIED ||============================== //

export default function AuthLayout() {
  const { user } = useAuth();

  // If already logged in, redirect to their resolved landing page
  if (user) {
    return <Navigate to={resolveLandingRoute(user)} replace />;
  }

  // Otherwise show login page
  return <Outlet />;
}
