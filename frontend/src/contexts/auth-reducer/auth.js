// action - state management
import { REGISTER, LOGIN, LOGOUT } from './actions';

// initial state
const initialState = {
  isLoggedIn: false,
  isInitialized: false,
  user: null,
  roles: [],
  permissions: []
};

// ==============================|| AUTH REDUCER ||============================== //

const auth = (state = initialState, action) => {
  switch (action.type) {
    case REGISTER: {
      const { user, roles = [], permissions = [] } = action.payload;
      return {
        ...state,
        user,
        roles,
        permissions
      };
    }
    case LOGIN: {
      const { user, roles = [], permissions = [] } = action.payload;
      return {
        ...state,
        isLoggedIn: true,
        isInitialized: true,
        user,
        roles,
        permissions
      };
    }
    case LOGOUT: {
      return {
        ...state,
        isInitialized: true,
        isLoggedIn: false,
        user: null,
        roles: [],
        permissions: []
      };
    }
    default: {
      return { ...state };
    }
  }
};

export default auth;
