import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import AuthWrapper from 'sections/auth/AuthWrapper';

// ================================|| SUPABASE - FORGOT PASSWORD ||================================ //

export default function ForgotPassword() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  useEffect(() => {
    navigate('/auth/login', { replace: true });
  }, [navigate, searchParams]);
  return null;
}
