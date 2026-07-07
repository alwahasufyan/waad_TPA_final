import { useContext } from 'react';
import { AuthContext } from './AuthContext';

// AUDIT FIX (TASK B): Use session-based AuthContext instead of JWTContext
const useAuth = () => useContext(AuthContext);

export default useAuth;
