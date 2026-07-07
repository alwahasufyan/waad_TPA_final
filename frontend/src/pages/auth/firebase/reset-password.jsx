// material-ui
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

// project imports
import AuthWrapper from 'sections/auth/AuthWrapper';

// ================================|| FIREBASE - RESET PASSWORD ||================================ //

export default function ResetPassword() {
  const navigate = useNavigate();
  useEffect(() => {
    navigate('/auth/login', { replace: true });
  }, [navigate]);
  return null;
}
