import { Link, useSearchParams } from 'react-router-dom';

// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import useAuth from 'hooks/useAuth';
import AuthWrapper from 'sections/auth/AuthWrapper';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

// ================================|| AUTH0 - LOGIN ||================================ //

export default function Login() {
  const navigate = useNavigate();
  useEffect(() => {
    navigate('/auth/login', { replace: true });
  }, [navigate]);
  return null;
}
