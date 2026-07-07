import { createBrowserRouter, Navigate } from 'react-router-dom';

// project imports
import MainRoutes from './MainRoutes';
import LoginRoutes from './LoginRoutes';
import RoleBasedRedirect from 'components/RoleBasedRedirect';

// ==============================|| ROUTING RENDER - PHASE 5.5 ENHANCED ||============================== //

/**
 * PHASE 5.5: Role-Based Landing Pages
 *
 * Root "/" now uses RoleBasedRedirect:
 * - Unauthenticated → /login
 * - Authenticated → role-specific landing page
 *
 * Order:
 * 1. Root with smart redirect
 * 2. LoginRoutes (public routes)
 * 3. MainRoutes (protected routes)
 * 4. 404 catch-all
 */
const router = createBrowserRouter(
  [
    // Root "/" with smart role-based redirect
    {
      path: '/',
      element: <RoleBasedRedirect />
    },

    // STEP 2: LoginRoutes FIRST (public routes)
    LoginRoutes,

    // STEP 3: MainRoutes (protected routes with PermissionGuard)
    MainRoutes,

    // STEP 4: Catch-all 404 (must be last)
    {
      path: '*',
      element: <Navigate to="/404" replace />
    }
  ],
  {
    basename: import.meta.env.VITE_APP_BASE_NAME,
    future: {
      v7_startTransition: true,
      v7_relativeSplatPath: true
    }
  }
);

export default router;
