/**
 * AuthContext - Session-based authentication.
 *
 * Security note:
 * Browser cookies are shared by all tabs on the same origin. The backend session
 * is therefore intentionally single-user per browser profile. This context
 * prevents a dangerous silent role switch when a different user logs in from
 * another tab by forcing the old tab back to login.
 */

import PropTypes from 'prop-types';
import { createContext, useEffect, useState, useContext } from 'react';

import authService from 'services/api/auth.service';
import { useRBACStore } from 'api/rbac';
import { openSnackbar } from 'api/snackbar';
import { clearToken } from 'utils/token-storage';

export const AUTH_STATUS = {
  INITIALIZING: 'INITIALIZING',
  AUTHENTICATED: 'AUTHENTICATED',
  UNAUTHENTICATED: 'UNAUTHENTICATED'
};

const AUTH_CHANNEL = 'tba-auth-channel';
const TAB_AUTH_USER_KEY = 'waad:auth:tabUserId';
const TAB_BLOCKED_USER_KEY = 'waad:auth:blockedUserId';

const getUserIdentity = (userData) => {
  if (!userData) return null;
  if (userData.id !== undefined && userData.id !== null) return String(userData.id);
  if (userData.username) return String(userData.username);
  return null;
};

const getSessionValue = (key) => {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
};

const setSessionValue = (key, value) => {
  try {
    if (value === null || value === undefined) {
      sessionStorage.removeItem(key);
    } else {
      sessionStorage.setItem(key, String(value));
    }
  } catch {
    // Ignore storage failures and keep runtime state authoritative.
  }
};

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [authStatus, setAuthStatus] = useState(AUTH_STATUS.INITIALIZING);
  const [lastActivity, setLastActivity] = useState(Date.now());
  const TIMEOUT_MS = 30 * 60 * 1000;

  const clearLocalAuthState = () => {
    setSessionValue(TAB_AUTH_USER_KEY, null);
    setUser(null);
    setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
    useRBACStore.getState().clear();
    clearToken();
  };

  const rememberAuthenticatedUser = (userData) => {
    const identity = getUserIdentity(userData);
    setSessionValue(TAB_AUTH_USER_KEY, identity);
    setSessionValue(TAB_BLOCKED_USER_KEY, null);
  };

  const blockForeignSession = (foreignIdentity) => {
    setSessionValue(TAB_BLOCKED_USER_KEY, foreignIdentity);
    clearLocalAuthState();
  };

  const broadcastAuthEvent = (payload) => {
    try {
      const channel = new BroadcastChannel(AUTH_CHANNEL);
      channel.postMessage(payload);
      channel.close();
    } catch {
      // BroadcastChannel may be unavailable in older browsers.
    }
  };

  useEffect(() => {
    if (authStatus !== AUTH_STATUS.AUTHENTICATED) return undefined;

    let lastUpdate = Date.now();
    const handleActivity = () => {
      const now = Date.now();
      if (now - lastUpdate > 5000) {
        setLastActivity(now);
        lastUpdate = now;
      }
    };

    const events = ['mousedown', 'keydown', 'scroll', 'touchstart'];
    events.forEach((event) => window.addEventListener(event, handleActivity));

    return () => {
      events.forEach((event) => window.removeEventListener(event, handleActivity));
    };
  }, [authStatus]);

  useEffect(() => {
    if (authStatus !== AUTH_STATUS.AUTHENTICATED) return undefined;

    const intervalId = setInterval(() => {
      if (Date.now() - lastActivity > TIMEOUT_MS) {
        openSnackbar({
          message: 'انتهت الجلسة بسبب عدم النشاط',
          alert: { color: 'warning' }
        });
        logout();
      }
    }, 60000);

    return () => clearInterval(intervalId);
  }, [authStatus, lastActivity]);

  useEffect(() => {
    const handleUnauthorized = () => {
      if (authStatus === AUTH_STATUS.AUTHENTICATED) {
        openSnackbar({
          message: 'انتهت الجلسة، يرجى تسجيل الدخول مرة أخرى',
          alert: { color: 'error' }
        });

        clearLocalAuthState();
        setTimeout(() => {
          window.location.href = '/login';
        }, 1000);
      }
    };

    window.addEventListener('auth:session-expired', handleUnauthorized);
    return () => window.removeEventListener('auth:session-expired', handleUnauthorized);
  }, [authStatus]);

  useEffect(() => {
    let channel = null;
    try {
      channel = new BroadcastChannel(AUTH_CHANNEL);
    } catch {
      return undefined;
    }

    channel.onmessage = (event) => {
      if (event.data?.type === 'LOGOUT') {
        setSessionValue(TAB_BLOCKED_USER_KEY, null);
        clearLocalAuthState();
        window.location.href = '/login';
        return;
      }

      if (event.data?.type === 'LOGIN') {
        const incomingUserId = event.data?.userId ? String(event.data.userId) : null;
        const currentUserId = getSessionValue(TAB_AUTH_USER_KEY);

        if (incomingUserId && currentUserId && incomingUserId !== currentUserId) {
          blockForeignSession(incomingUserId);
          openSnackbar({
            message: 'تم تسجيل دخول مستخدم مختلف في تبويب آخر، يرجى تسجيل الدخول من جديد',
            alert: { color: 'warning' }
          });
          window.location.href = '/login';
        }
      }
    };

    return () => {
      channel.close();
    };
  }, []);

  useEffect(() => {
    const init = async () => {
      try {
        const response = await authService.me();

        if (response.status === 'success' && response.data) {
          const responseIdentity = getUserIdentity(response.data);
          const blockedIdentity = getSessionValue(TAB_BLOCKED_USER_KEY);
          const rememberedIdentity = getSessionValue(TAB_AUTH_USER_KEY);

          if (blockedIdentity && blockedIdentity === responseIdentity) {
            clearLocalAuthState();
            return;
          }

          if (rememberedIdentity && responseIdentity && rememberedIdentity !== responseIdentity) {
            blockForeignSession(responseIdentity);
            return;
          }

          rememberAuthenticatedUser(response.data);
          setUser(response.data);
          setAuthStatus(AUTH_STATUS.AUTHENTICATED);
          useRBACStore.getState().initialize(response.data);
        } else {
          clearLocalAuthState();
        }
      } catch {
        clearLocalAuthState();
      }
    };

    init();
  }, []);

  const login = async (credentials) => {
    const response = await authService.login(credentials);

    if (response.status === 'success' && response.data) {
      rememberAuthenticatedUser(response.data);
      setUser(response.data);
      setAuthStatus(AUTH_STATUS.AUTHENTICATED);
      useRBACStore.getState().initialize(response.data);
      broadcastAuthEvent({ type: 'LOGIN', userId: getUserIdentity(response.data) });
      return response.data;
    }

    throw new Error('Login failed');
  };

  const logout = async () => {
    try {
      await authService.logout();
    } catch (error) {
      console.warn('Logout API failed (likely already expired)', error);
    }

    setSessionValue(TAB_BLOCKED_USER_KEY, null);
    clearLocalAuthState();
    sessionStorage.clear();
    broadcastAuthEvent({ type: 'LOGOUT' });
    window.location.href = '/login';
  };

  const refreshUser = async () => {
    try {
      const response = await authService.me();

      if (response.status === 'success' && response.data) {
        const responseIdentity = getUserIdentity(response.data);
        const blockedIdentity = getSessionValue(TAB_BLOCKED_USER_KEY);
        const rememberedIdentity = getSessionValue(TAB_AUTH_USER_KEY);

        if (blockedIdentity && blockedIdentity === responseIdentity) {
          clearLocalAuthState();
          return;
        }

        if (rememberedIdentity && responseIdentity && rememberedIdentity !== responseIdentity) {
          blockForeignSession(responseIdentity);
          return;
        }

        rememberAuthenticatedUser(response.data);
        setUser(response.data);
        setAuthStatus(AUTH_STATUS.AUTHENTICATED);
        useRBACStore.getState().initialize(response.data);
      } else {
        clearLocalAuthState();
      }
    } catch {
      clearLocalAuthState();
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        authStatus,
        login,
        logout,
        refreshUser
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

AuthProvider.propTypes = {
  children: PropTypes.node
};

export default AuthContext;
export { AuthContext };

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
