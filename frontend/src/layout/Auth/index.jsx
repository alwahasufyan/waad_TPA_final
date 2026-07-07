import { Outlet, Navigate } from 'react-router-dom';
import useAuth from 'hooks/useAuth';
import { getDefaultRouteForRole } from 'utils/roleRoutes';

// ==============================|| LAYOUT - AUTH - SIMPLIFIED ||============================== //

export default function AuthLayout() {
  const { user } = useAuth();

  // If already logged in, redirect to dashboard
  if (user) {
    return <Navigate to={getDefaultRouteForRole(user?.role)} replace />;
  }

  // Otherwise show login page
  return <Outlet />;
}
