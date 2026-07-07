import PropTypes from 'prop-types';
// material-ui
import Avatar from '@mui/material/Avatar';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import MainCard from 'components/MainCard';

export default function RoundIconCard({ primary, secondary, content, iconPrimary, color, bgcolor }) {
  const IconPrimary = iconPrimary;
  const primaryIcon = iconPrimary ? <IconPrimary fontSize="large" /> : null;

  return (
    <MainCard>
      <Grid container sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Grid>
          <Stack sx={{ gap: 1 }}>
            <Typography variant="h5" color="inherit">
              {primary}
            </Typography>
            <Typography variant="h3">{secondary}</Typography>
            <Typography variant="subtitle2" color="secondary">
              {content}
            </Typography>
          </Stack>
        </Grid>
        <Grid>
          <Avatar variant="rounded" sx={{ bgcolor, color }}>
            {primaryIcon}
          </Avatar>
        </Grid>
      </Grid>
    </MainCard>
  );
}

RoundIconCard.propTypes = {
  primary: PropTypes.string,
  secondary: PropTypes.string,
  content: PropTypes.string,
  iconPrimary: PropTypes.any,
  color: PropTypes.string,
  bgcolor: PropTypes.string
};
