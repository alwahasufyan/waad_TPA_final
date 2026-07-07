// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';

// project imports
import MainCard from 'components/MainCard';

// ==============================|| ACCOUNT PROFILE - ROLE ||============================== //

export default function TabRole() {
  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard title="Role & Permissions">
          <Grid container spacing={3}>
            <Grid size={12}>
              <Stack spacing={1}>
                <Typography variant="subtitle1">Current Role</Typography>
                <TextField fullWidth value="Administrator" disabled />
              </Stack>
            </Grid>
            <Grid size={12}>
              <Stack spacing={1}>
                <Typography variant="subtitle1">Permissions</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Chip label="Manage Users" color="primary" />
                  <Chip label="Manage Members" color="primary" />
                  <Chip label="Manage Claims" color="primary" />
                  <Chip label="View Reports" color="primary" />
                </Stack>
              </Stack>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
    </Grid>
  );
}
