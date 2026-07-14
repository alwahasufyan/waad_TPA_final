import axiosClient from 'utils/axios';

const BASE_URL = '/system/danger-zone';

const unwrap = (response) => response?.data?.data ?? response?.data;

const dangerZoneService = {
  status: async () => {
    const response = await axiosClient.get(`${BASE_URL}/status`);
    return unwrap(response);
  },

  sendOtp: async (operation) => {
    const response = await axiosClient.post(`${BASE_URL}/otp/send`, { operation });
    return unwrap(response);
  },

  restore: async (backupId, { password, confirmationPhrase, otpCode }) => {
    const response = await axiosClient.post(`${BASE_URL}/restore/${backupId}`, { password, confirmationPhrase, otpCode });
    return unwrap(response);
  },

  reset: async ({ password, confirmationPhrase, otpCode, resetMonitoringLogs, resetErrorLogs, resetBackupMetadata }) => {
    const response = await axiosClient.post(`${BASE_URL}/reset`, {
      password,
      confirmationPhrase,
      otpCode,
      resetMonitoringLogs,
      resetErrorLogs,
      resetBackupMetadata
    });
    return unwrap(response);
  }
};

export default dangerZoneService;
