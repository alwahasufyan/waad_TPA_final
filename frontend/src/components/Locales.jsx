import PropTypes from 'prop-types';

// ==============================|| LOCALIZATION ||============================== //
// Simplified Locales wrapper - Static Arabic UX for Closed Enterprise
// react-intl removed to prevent runtime crashes from missing message IDs
// All text now uses static Arabic strings via utils/labels.js

export default function Locales({ children }) {
  // Simply render children - no IntlProvider needed
  // Static Arabic terminology is used throughout the application
  return <>{children}</>;
}

Locales.propTypes = { children: PropTypes.node };
