import { useState, useEffect } from 'react';
import { getEmployers, getEmployerById } from 'services/api/employers.service';

/**
 * Custom hook to fetch all employers list
 * @returns {Object} { data, loading, error, refetch }
 */
export const useEmployersList = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchEmployers = async () => {
    try {
      setLoading(true);
      setError(null);
      const result = await getEmployers();
      setData(result || []);
    } catch (err) {
      console.error('Failed to fetch employers:', err);
      setError(err);
      setData([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEmployers();
  }, []);

  const refetch = () => {
    fetchEmployers();
  };

  return { data, loading, error, refetch };
};

/**
 * Custom hook to fetch single employer by ID
 * @param {number} id - Employer ID
 * @returns {Object} { data, loading, error, refetch }
 */
export const useEmployerDetails = (id) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchEmployer = async () => {
    if (!id) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const result = await getEmployerById(id);
      setData(result);
    } catch (err) {
      console.error('Failed to fetch employer:', err);
      setError(err);
      setData(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEmployer();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const refetch = () => {
    fetchEmployer();
  };

  return { data, loading, error, refetch };
};
