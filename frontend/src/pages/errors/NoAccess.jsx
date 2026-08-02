import { Box, Button, Container, Typography, Paper } from '@mui/material';
import { Logout as LogoutIcon } from '@mui/icons-material';

import useAuth from 'hooks/useAuth';

// ==============================|| زيرو-أكسِس: لا توجد صفحات متاحة ||============================== //
//
// WAAD-RBAC-PER-USER-LANDING-PAGE-1 requirement 8: with PermissionGuard now
// redirecting a denied direct/stale URL straight to the user's own resolved
// accessible page (see utils/roleRoutes.js resolveLandingRoute()), this route
// is reached ONLY in the genuine zero-access case — a user whose effective
// permissions grant no page at all. That's an admin configuration state, not
// a system error, so this is deliberately calm: no red icon, no "Access
// Denied"/error code, no button pointing at a page they can't reach (there
// isn't one) — just the required message and a way out (logout).
const NoAccess = () => {
  const { logout } = useAuth();

  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        bgcolor: 'background.default'
      }}
    >
      <Container maxWidth="sm">
        <Paper
          elevation={3}
          sx={{
            p: '2.5rem',
            textAlign: 'center',
            borderRadius: '0.1875rem'
          }}
        >
          <Typography variant="h6" sx={{ color: 'text.primary', mb: '2.0rem' }}>
            لا توجد صفحات متاحة لك حاليًا — تواصل مع مسؤول النظام
          </Typography>

          <Button
            variant="outlined"
            color="inherit"
            size="large"
            startIcon={<LogoutIcon />}
            onClick={logout}
            sx={{ textTransform: 'none' }}
          >
            تسجيل الخروج
          </Button>
        </Paper>
      </Container>
    </Box>
  );
};

export default NoAccess;
