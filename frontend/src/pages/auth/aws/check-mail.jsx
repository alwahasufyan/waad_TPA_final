import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

// material-ui
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// project imports
import AnimateButton from 'components/@extended/AnimateButton';
import AuthWrapper from 'sections/auth/AuthWrapper';

// ================================|| AWS - CHECK MAIL ||================================ //

export default function CheckMail() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  useEffect(() => {
    navigate('/auth/login', { replace: true });
  }, [navigate, searchParams]);
  return null;
}
