import { Link, useSearchParams, useNavigate } from 'react-router-dom';

// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import useAuth from 'hooks/useAuth';
import AuthWrapper from 'sections/auth/AuthWrapper';
import { useEffect } from 'react';

// ================================|| SUPERBASE - REGISTER ||================================ //

export default function Register() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  useEffect(() => {
    navigate('/auth/login', { replace: true });
  }, [navigate, searchParams]);
  return null;
}
