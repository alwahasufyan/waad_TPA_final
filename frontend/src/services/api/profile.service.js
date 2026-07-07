/**
 * Profile API Service
 * Self-service profile operations for authenticated users
 *
 * Endpoints:
 * - POST /api/profile/change-password - Change user password
 *
 * Last Updated: 2024-12-21
 */

import axiosClient from 'utils/axios';

/**
 * Change password for the currently authenticated user
 *
 * @param {Object} payload - Password change payload
 * @param {string} payload.currentPassword - Current password for verification
 * @param {string} payload.newPassword - New password (min 8 chars)
 * @param {string} payload.confirmPassword - Must match newPassword
 * @returns {Promise<Object>} API response with success message
 * @throws {Error} If current password is wrong or validation fails
 */
export const changePassword = async (payload) => {
  const response = await axiosClient.post('/profile/change-password', payload);
  return response;
};

// Export as service object for consistent pattern
export const profileService = {
  changePassword
};

export default profileService;
