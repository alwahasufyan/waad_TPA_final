import axiosClient from 'utils/axios';

const BASE_URL = '/system/maintenance';

const unwrap = (response) => response?.data?.data ?? response?.data;

const maintenanceService = {
  status: async () => {
    const response = await axiosClient.get(`${BASE_URL}/status`);
    return unwrap(response);
  },

  set: async ({ enabled, reason }) => {
    const response = await axiosClient.post(BASE_URL, { enabled, reason });
    return unwrap(response);
  }
};

export default maintenanceService;
