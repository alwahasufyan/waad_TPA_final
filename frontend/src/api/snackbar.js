import { create } from 'zustand';

// ==============================|| SNACKBAR API - STATE MANAGEMENT ||============================== //

/**
 * Zustand store for snackbar/notification state
 * Provides global snackbar control
 */
export const useSnackbarStore = create((set) => ({
  open: false,
  message: 'Note archived',
  anchorOrigin: { vertical: 'bottom', horizontal: 'right' },
  variant: 'default',
  alert: { color: 'primary', variant: 'filled' },
  transition: 'Fade',
  close: true,
  actionButton: false,
  maxStack: 3,
  dense: false,
  iconVariant: 'usedefault'
}));

/**
 * Hook to get snackbar state
 */
export const useGetSnackbar = () => {
  return useSnackbarStore();
};

/**
 * Open snackbar with custom options
 * @param {Object} options - Snackbar configuration
 */
export const openSnackbar = (options) => {
  useSnackbarStore.setState({
    open: true,
    ...options
  });
};

/**
 * Close snackbar
 */
export const closeSnackbar = () => {
  useSnackbarStore.setState({ open: false });
};

export default useSnackbarStore;
