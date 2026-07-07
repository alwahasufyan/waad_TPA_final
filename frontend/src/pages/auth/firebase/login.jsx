import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import AuthWrapper from 'sections/auth/AuthWrapper';

// ================================|| FIREBASE - LOGIN ||================================ //

export default function Login() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  useEffect(() => {
    // Redirect deprecated external auth pages to JWT login
    navigate('/auth/login', { replace: true });
  }, [navigate, searchParams]);
  return null;
}
