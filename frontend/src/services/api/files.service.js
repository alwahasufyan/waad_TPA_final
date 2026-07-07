import api from '../../utils/axios';

/**
 * Files Service
 *
 * Handles file upload, download, and management
 */

/**
 * Upload a file
 *
 * @param {File} file - File to upload
 * @param {string} folder - Target folder (e.g., 'claims', 'preauth', 'visits')
 * @param {string} description - Optional description
 * @param {function} onProgress - Progress callback
 * @returns {Promise} Upload result
 */
export const uploadFile = async (file, folder, description = null, onProgress = null) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('folder', folder);
  if (description) {
    formData.append('description', description);
  }

  const config = {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  };

  if (onProgress) {
    config.onUploadProgress = (progressEvent) => {
      const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
      onProgress(percentCompleted);
    };
  }

  const response = await api.post('/files/upload', formData, config);
  return response.data;
};

/**
 * Download a file
 *
 * @param {string} folder - Folder name
 * @param {string} filename - File name
 * @returns {Promise<Blob>} File content
 */
export const downloadFile = async (folder, filename) => {
  const response = await api.get(`/files/${folder}/${filename}/download`, {
    responseType: 'blob'
  });
  return response.data;
};

/**
 * Delete a file
 *
 * @param {string} folder - Folder name
 * @param {string} filename - File name
 * @returns {Promise} Deletion result
 */
export const deleteFile = async (folder, filename) => {
  const response = await api.delete(`/files/${folder}/${filename}`);
  return response.data;
};

/**
 * Get presigned URL for a file
 *
 * @param {string} folder - Folder name
 * @param {string} filename - File name
 * @param {number} expiryMinutes - URL validity duration (default: 60)
 * @returns {Promise<string>} Presigned URL
 */
export const getFileUrl = async (folder, filename, expiryMinutes = 60) => {
  const response = await api.get(`/files/${folder}/${filename}/url`, {
    params: { expiryMinutes }
  });
  return response.data;
};

/**
 * Check if file exists
 *
 * @param {string} folder - Folder name
 * @param {string} filename - File name
 * @returns {Promise<boolean>} True if exists
 */
export const fileExists = async (folder, filename) => {
  const response = await api.get(`/files/${folder}/${filename}/exists`);
  return response.data;
};

/**
 * Upload claim attachment
 *
 * @param {number} claimId - Claim ID
 * @param {File} file - File to upload
 * @param {string} attachmentType - Type of attachment
 * @returns {Promise} Upload result
 */
export const uploadClaimAttachment = async (claimId, file, attachmentType, onProgress = null) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('attachmentType', attachmentType);

  const config = {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  };

  if (onProgress) {
    config.onUploadProgress = (progressEvent) => {
      const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
      onProgress(percentCompleted);
    };
  }

  const response = await api.post(`/claims/${claimId}/attachments`, formData, config);
  return response.data;
};

/**
 * Get claim attachments
 *
 * @param {number} claimId - Claim ID
 * @returns {Promise<Array>} List of attachments
 */
export const getClaimAttachments = async (claimId) => {
  console.log(`[FilesService] GET /claims/${claimId}/attachments`);
  const response = await api.get(`/claims/${claimId}/attachments`);
  console.log(`[FilesService] ✅ Response:`, response.data);
  return response.data;
};

/**
 * Download claim attachment
 *
 * @param {number} claimId - Claim ID
 * @param {number} attachmentId - Attachment ID
 * @returns {Promise<Blob>} File content
 */
export const downloadClaimAttachment = async (claimId, attachmentId) => {
  console.log(`[FilesService] GET /claims/${claimId}/attachments/${attachmentId} (Blob)`);
  try {
    const response = await api.get(`/claims/${claimId}/attachments/${attachmentId}`, {
      responseType: 'blob'
    });
    console.log(`[FilesService] ✅ Blob received. Size: ${response.data.size}, Type: ${response.data.type}`);
    return response.data;
  } catch (error) {
    console.error(`[FilesService] ❌ Download failed:`, {
      claimId,
      attachmentId,
      status: error.response?.status,
      message: error.message,
      url: error.config?.url
    });
    throw error;
  }
};

/**
 * Delete claim attachment
 *
 * @param {number} claimId - Claim ID
 * @param {number} attachmentId - Attachment ID
 * @returns {Promise} Deletion result
 */
export const deleteClaimAttachment = async (claimId, attachmentId) => {
  const response = await api.delete(`/claims/${claimId}/attachments/${attachmentId}`);
  return response.data;
};

/**
 * Upload PreAuth attachment
 * Endpoint: POST /api/v1/pre-authorizations/{preAuthId}/attachments
 */
export const uploadPreAuthAttachment = async (preAuthId, file, attachmentType, onProgress = null) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('attachmentType', attachmentType);

  const config = {
    headers: { 'Content-Type': 'multipart/form-data' }
  };

  if (onProgress) {
    config.onUploadProgress = (progressEvent) => {
      const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
      onProgress(percentCompleted);
    };
  }

  const response = await api.post(`/pre-authorizations/${preAuthId}/attachments`, formData, config);
  return response.data;
};

/**
 * Get PreAuth attachments
 * Endpoint: GET /api/v1/pre-authorizations/{preAuthId}/attachments
 */
export const getPreAuthAttachments = async (preAuthId) => {
  const response = await api.get(`/pre-authorizations/${preAuthId}/attachments`);
  return response.data;
};

/**
 * Download PreAuth attachment
 * Endpoint: GET /api/v1/pre-authorizations/{preAuthId}/attachments/{attachmentId}
 */
export const downloadPreAuthAttachment = async (preAuthId, attachmentId) => {
  const response = await api.get(`/pre-authorizations/${preAuthId}/attachments/${attachmentId}`, {
    responseType: 'blob'
  });
  return response.data;
};

/**
 * Delete PreAuth attachment
 * Endpoint: DELETE /api/v1/pre-authorizations/{preAuthId}/attachments/{attachmentId}
 */
export const deletePreAuthAttachment = async (preAuthId, attachmentId) => {
  const response = await api.delete(`/pre-authorizations/${preAuthId}/attachments/${attachmentId}`);
  return response.data;
};

/**
 * Upload Visit attachment
 */
export const uploadVisitAttachment = async (visitId, file, attachmentType, description = null, onProgress = null) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('attachmentType', attachmentType);
  if (description) {
    formData.append('description', description);
  }

  const config = {
    headers: { 'Content-Type': 'multipart/form-data' }
  };

  if (onProgress) {
    config.onUploadProgress = (progressEvent) => {
      const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
      onProgress(percentCompleted);
    };
  }

  const response = await api.post(`/visits/${visitId}/attachments`, formData, config);
  return response.data;
};

/**
 * Get Visit attachments
 */
export const getVisitAttachments = async (visitId) => {
  const response = await api.get(`/visits/${visitId}/attachments`);
  return response.data;
};

/**
 * Download Visit attachment
 */
export const downloadVisitAttachment = async (visitId, attachmentId) => {
  const response = await api.get(`/visits/${visitId}/attachments/${attachmentId}`, {
    responseType: 'blob'
  });
  return response.data;
};

/**
 * Delete Visit attachment
 */
export const deleteVisitAttachment = async (visitId, attachmentId) => {
  const response = await api.delete(`/visits/${visitId}/attachments/${attachmentId}`);
  return response.data;
};

export default {
  uploadFile,
  downloadFile,
  deleteFile,
  getFileUrl,
  fileExists,
  uploadClaimAttachment,
  getClaimAttachments,
  downloadClaimAttachment,
  deleteClaimAttachment,
  uploadPreAuthAttachment,
  getPreAuthAttachments,
  downloadPreAuthAttachment,
  deletePreAuthAttachment,
  uploadVisitAttachment,
  getVisitAttachments,
  downloadVisitAttachment,
  deleteVisitAttachment
};
