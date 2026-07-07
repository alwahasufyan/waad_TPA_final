// material-ui
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

// project imports
import AuthWrapper from 'sections/auth/AuthWrapper';

// ================================|| FIREBASE - CODE VERIFICATION ||================================ //

export default function CodeVerification() {
  const navigate = useNavigate();
  useEffect(() => {
    navigate('/auth/login', { replace: true });
  }, [navigate]);
  return null;
}
