// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';

// project imports
import MainCard from 'components/MainCard';

// ==============================|| ACCOUNT PROFILE - SETTINGS ||============================== //

export default function TabSettings() {
  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard title="Notification Settings">
          <Grid container spacing={3}>
            <Grid size={12}>
              <Stack spacing={2}>
                <FormControlLabel control={<Switch defaultChecked />} label="Email Notifications" />
                <FormControlLabel control={<Switch defaultChecked />} label="SMS Notifications" />
                <FormControlLabel control={<Switch />} label="Push Notifications" />
              </Stack>
            </Grid>
            <Grid size={12}>
              <Stack direction="row" justifyContent="flex-end" spacing={2}>
                <Button variant="outlined" color="secondary">
                  Cancel
                </Button>
                <Button variant="contained">Save Settings</Button>
              </Stack>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
    </Grid>
  );
}
