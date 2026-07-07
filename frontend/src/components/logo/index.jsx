import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import ButtonBase from '@mui/material/ButtonBase';

// project imports
import Logo from './LogoMain';
import LogoIcon from './LogoIcon';
import { APP_DEFAULT_PATH } from 'config';
import useAuth from 'hooks/useAuth';
import { getDefaultRouteForRole } from 'utils/roleRoutes';

export default function LogoSection({ reverse, isIcon, sx, to }) {
  const { user } = useAuth();
  const homePath = to || (user?.role ? getDefaultRouteForRole(user.role) : APP_DEFAULT_PATH);

  return (
    <ButtonBase disableRipple component={Link} to={homePath} sx={sx} aria-label="Logo">
      {isIcon ? <LogoIcon /> : <Logo reverse={reverse} />}
    </ButtonBase>
  );
}

LogoSection.propTypes = { reverse: PropTypes.bool, isIcon: PropTypes.bool, sx: PropTypes.any, to: PropTypes.any };
