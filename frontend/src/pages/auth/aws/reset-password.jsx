// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import AuthWrapper from 'sections/auth/AuthWrapper';

// ================================|| AWS - RESET PASSWORD ||================================ //

export default function ResetPassword() {
  const navigate = useNavigate();
  useEffect(() => {
    navigate('/auth/login', { replace: true });
  }, [navigate]);
  return null;
}
