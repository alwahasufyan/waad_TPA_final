import { useState, useCallback } from 'react';

/**
 * useFileUpload Hook
 *
 * Custom hook for handling file uploads with progress tracking
 *
 * @param {Object} options - Configuration options
 * @param {Function} options.uploadFn - Upload function to use
 * @param {Function} options.onSuccess - Success callback
 * @param {Function} options.onError - Error callback
 * @returns {Object} Upload state and methods
 */
export const useFileUpload = ({ uploadFn, onSuccess, onError } = {}) => {
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState(null);
  const [uploadedFile, setUploadedFile] = useState(null);

  const upload = useCallback(
    async (file, ...args) => {
      setUploading(true);
      setProgress(0);
      setError(null);
      setUploadedFile(null);

      try {
        // Progress callback
        const onProgress = (percent) => {
          setProgress(percent);
        };

        // Call the upload function with progress callback
        const result = await uploadFn(file, ...args, onProgress);

        setUploadedFile(result);
        setProgress(100);

        if (onSuccess) {
          onSuccess(result);
        }

        return result;
      } catch (err) {
        const errorMessage = err.response?.data?.message || err.message || 'Upload failed';
        setError(errorMessage);

        if (onError) {
          onError(err);
        }

        throw err;
      } finally {
        setUploading(false);
      }
    },
    [uploadFn, onSuccess, onError]
  );

  const reset = useCallback(() => {
    setUploading(false);
    setProgress(0);
    setError(null);
    setUploadedFile(null);
  }, []);

  return {
    upload,
    uploading,
    progress,
    error,
    uploadedFile,
    reset
  };
};

/**
 * useMultiFileUpload Hook
 *
 * Custom hook for handling multiple file uploads
 */
export const useMultiFileUpload = ({ uploadFn, onSuccess, onError } = {}) => {
  const [uploads, setUploads] = useState([]);
  const [uploading, setUploading] = useState(false);

  const uploadFiles = useCallback(
    async (files, ...args) => {
      setUploading(true);
      const fileArray = Array.from(files);

      const uploadPromises = fileArray.map(async (file, index) => {
        const uploadState = {
          file,
          progress: 0,
          error: null,
          result: null,
          completed: false
        };

        setUploads((prev) => {
          const newUploads = [...prev];
          newUploads[index] = uploadState;
          return newUploads;
        });

        try {
          const onProgress = (percent) => {
            setUploads((prev) => {
              const newUploads = [...prev];
              newUploads[index] = { ...newUploads[index], progress: percent };
              return newUploads;
            });
          };

          const result = await uploadFn(file, ...args, onProgress);

          setUploads((prev) => {
            const newUploads = [...prev];
            newUploads[index] = {
              ...newUploads[index],
              result,
              completed: true,
              progress: 100
            };
            return newUploads;
          });

          if (onSuccess) {
            onSuccess(result, index);
          }

          return result;
        } catch (err) {
          const errorMessage = err.response?.data?.message || err.message || 'Upload failed';

          setUploads((prev) => {
            const newUploads = [...prev];
            newUploads[index] = {
              ...newUploads[index],
              error: errorMessage,
              completed: true
            };
            return newUploads;
          });

          if (onError) {
            onError(err, index);
          }

          throw err;
        }
      });

      try {
        const results = await Promise.all(uploadPromises);
        return results;
      } finally {
        setUploading(false);
      }
    },
    [uploadFn, onSuccess, onError]
  );

  const reset = useCallback(() => {
    setUploads([]);
    setUploading(false);
  }, []);

  return {
    uploadFiles,
    uploads,
    uploading,
    reset
  };
};

export default useFileUpload;
