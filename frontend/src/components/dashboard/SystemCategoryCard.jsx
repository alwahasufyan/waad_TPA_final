import PropTypes from 'prop-types';
import { ButtonBase, Box, Stack, Typography, alpha } from '@mui/material';
import AppsIcon from '@mui/icons-material/Apps';
import { dashboardNeutral, dashboardShape } from 'themes/dashboardTokens';

/**
 * SystemCategoryCard — an Odoo-style app tile inside the System Categories dialog.
 * A small, colourful icon on a white rounded tile with the item name beneath it.
 * Renders any menu leaf (icon component + title); no headings, no descriptions.
 */
export default function SystemCategoryCard({ title, icon: Icon = AppsIcon, color = '#667573', onClick }) {
  const IconComp = Icon || AppsIcon;

  return (
    <ButtonBase
      onClick={onClick}
      focusRipple
      aria-label={title}
      sx={{
        width: '100%',
        borderRadius: `${dashboardShape.radius}px`,
        p: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        textAlign: 'center',
        transition: `background-color ${dashboardShape.transition}, transform ${dashboardShape.transition}`,
        '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
        '&:hover': { bgcolor: alpha(color, 0.06), transform: 'translateY(-2px)' },
        '&:focus-visible': { outline: `2px solid ${alpha(color, 0.6)}`, outlineOffset: 2 }
      }}
    >
      <Stack spacing={0.75} alignItems="center" sx={{ width: '100%' }}>
        {/* White tile with a small colourful icon (Odoo launcher style) */}
        <Box
          sx={{
            width: 48,
            height: 48,
            borderRadius: `${dashboardShape.radiusSm + 4}px`,
            bgcolor: dashboardNeutral.surface,
            color,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: '1px solid',
            borderColor: dashboardNeutral.border,
            boxShadow: dashboardShape.shadowSoft
          }}
        >
          <IconComp sx={{ fontSize: '1.5rem' }} />
        </Box>

        <Typography
          sx={{
            fontSize: '0.75rem',
            fontWeight: 600,
            color: dashboardNeutral.textPrimary,
            lineHeight: 1.3,
            width: '100%'
          }}
        >
          {title}
        </Typography>
      </Stack>
    </ButtonBase>
  );
}

SystemCategoryCard.propTypes = {
  title: PropTypes.string.isRequired,
  icon: PropTypes.elementType,
  color: PropTypes.string,
  onClick: PropTypes.func.isRequired
};
