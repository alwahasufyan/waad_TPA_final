// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import AuthWrapper from 'sections/auth/AuthWrapper';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

// ================================|| SUPABASE - CODE VERIFICATION ||================================ //

export default function CodeVerification() {
  const navigate = useNavigate();
  useEffect(() => {
    navigate('/auth/login', { replace: true });
  }, [navigate]);
  return null;
}
