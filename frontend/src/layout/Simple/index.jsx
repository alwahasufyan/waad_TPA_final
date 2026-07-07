import PropTypes from 'prop-types';
import { lazy, Suspense } from 'react';
import { Outlet } from 'react-router-dom';

// project imports
import Loader from 'components/Loader';
import { SimpleLayoutType } from 'config';

const Header = lazy(() => import('components/pages/Header'));
const Footer = lazy(() => import('components/pages/Footer'));

// ==============================|| LAYOUT - SIMPLE / LANDING ||============================== //

export default function SimpleLayout({ layout = SimpleLayoutType.SIMPLE, enableElevationScroll = false }) {
  return (
    <Suspense fallback={<Loader />}>
      <Header enableElevationScroll={enableElevationScroll} />
      <Outlet />
      <Footer isFull={layout === SimpleLayoutType.LANDING} />
    </Suspense>
  );
}

SimpleLayout.propTypes = {
  layout: PropTypes.any,
  SimpleLayoutType: PropTypes.any,
  SIMPLE: PropTypes.any,
  enableElevationScroll: PropTypes.bool
};
