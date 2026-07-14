import axiosClient from 'utils/axios';

const BASE_URL = '/system/backups';

export const systemBackupsService = {
  getStatus: async () => {
    const response = await axiosClient.get(`${BASE_URL}/status`);
    return response.data?.data;
  },

  list: async () => {
    const response = await axiosClient.get(BASE_URL);
    return response.data?.data || [];
  },

  getSettings: async () => {
    const response = await axiosClient.get(`${BASE_URL}/settings`);
    return response.data?.data;
  },

  updateSettings: async (settings) => {
    const response = await axiosClient.put(`${BASE_URL}/settings`, settings);
    return response.data?.data;
  },

  create: async ({ type, note }) => {
    const response = await axiosClient.post(BASE_URL, { type, note });
    return response.data?.data;
  },

  validate: async (id) => {
    const response = await axiosClient.post(`${BASE_URL}/${id}/validate`);
    return response.data?.data;
  },

  verifyRestore: async (id) => {
    const response = await axiosClient.post(`${BASE_URL}/${id}/verify-restore`);
    return response.data?.data;
  },

  rehearse: async (id) => {
    const response = await axiosClient.post(`${BASE_URL}/${id}/rehearse`);
    return response.data?.data;
  },

  purge: async (dryRun = true) => {
    const response = await axiosClient.post(`${BASE_URL}/purge`, null, { params: { dryRun } });
    return response.data?.data;
  },

  downloadUrl: (id) => `${axiosClient.defaults.baseURL}${BASE_URL}/${id}/download`
};

export default systemBackupsService;
