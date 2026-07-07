// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';

// project imports
import MainCard from 'components/MainCard';

// assets
import EyeOutlined from '@ant-design/icons/EyeOutlined';
import EyeInvisibleOutlined from '@ant-design/icons/EyeInvisibleOutlined';
import { useState } from 'react';

// ==============================|| ACCOUNT PROFILE - PASSWORD ||============================== //

export default function TabPassword() {
  const [showPassword, setShowPassword] = useState(false);

  const handleClickShowPassword = () => {
    setShowPassword(!showPassword);
  };

  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard title="Change Password">
          <Grid container spacing={3}>
            <Grid size={12}>
              <Stack spacing={1}>
                <Typography variant="subtitle1">Current Password</Typography>
                <TextField
                  fullWidth
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Enter current password"
                  InputProps={{
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={handleClickShowPassword} edge="end">
                          {showPassword ? <EyeOutlined /> : <EyeInvisibleOutlined />}
                        </IconButton>
                      </InputAdornment>
                    )
                  }}
                />
              </Stack>
            </Grid>
            <Grid size={12}>
              <Stack spacing={1}>
                <Typography variant="subtitle1">New Password</Typography>
                <TextField fullWidth type="password" placeholder="Enter new password" />
              </Stack>
            </Grid>
            <Grid size={12}>
              <Stack spacing={1}>
                <Typography variant="subtitle1">Confirm Password</Typography>
                <TextField fullWidth type="password" placeholder="Confirm new password" />
              </Stack>
            </Grid>
            <Grid size={12}>
              <Stack direction="row" justifyContent="flex-end" spacing={2}>
                <Button variant="outlined" color="secondary">
                  Cancel
                </Button>
                <Button variant="contained">Change Password</Button>
              </Stack>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
    </Grid>
  );
}
