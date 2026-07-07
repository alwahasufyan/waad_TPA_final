import { use } from 'react';

// auth provider
// Phase C Migration: Switched from JWT to Session-based AuthContext
import AuthContext from 'contexts/AuthContext';

// ==============================|| AUTH HOOKS ||============================== //

export default function useAuth() {
  const context = use(AuthContext);

  if (!context) throw new Error('context must be use inside provider');

  return context;
}
