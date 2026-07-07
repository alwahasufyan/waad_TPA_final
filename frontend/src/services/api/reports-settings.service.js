import axiosClient from 'utils/axios';

/**
 * Report Settings API Service
 * Handles PDF/Report specific configurations
 */
const BASE_URL = '/api/v1/pdf/settings';

const getActiveSettings = async () => {
  try {
    const response = await axiosClient.get(`${BASE_URL}/active`);
    return response.data;
  } catch (error) {
    console.error('Error fetching active report settings:', error);
    throw error;
  }
};

const updateSettings = async (id, data) => {
  try {
    const response = await axiosClient.put(`${BASE_URL}/${id}`, data);
    return response.data;
  } catch (error) {
    console.error(`Error updating report settings ${id}:`, error);
    throw error;
  }
};

const uploadLogo = async (id, file) => {
  try {
    const formData = new FormData();
    formData.append('file', file);
    const response = await axiosClient.post(`${BASE_URL}/${id}/logo`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
    return response.data;
  } catch (error) {
    console.error(`Error uploading logo for settings ${id}:`, error);
    throw error;
  }
};

export const reportSettingsService = {
  getActiveSettings,
  updateSettings,
  uploadLogo
};

export default reportSettingsService;
