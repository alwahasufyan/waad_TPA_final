// project imports
import getColors from 'utils/getColors';
import getShadow from 'utils/getShadow';

// ==============================|| OVERRIDES - INPUT BORDER & SHADOWS ||============================== //

function getColor({ variant, theme }) {
  const colors = getColors(theme, variant);
  const { light } = colors;

  const shadows = getShadow(theme, `${variant}`);

  return {
    '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: light },
    '&.Mui-focused': { boxShadow: shadows, '& .MuiOutlinedInput-notchedOutline': { border: '1px solid', borderColor: light } }
  };
}

// ==============================|| OVERRIDES - OUTLINED INPUT ||============================== //

export default function OutlinedInput(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiOutlinedInput: {
      styleOverrides: {
        input: { padding: '10.5px 14px 10.5px 12px' },
        notchedOutline: {
          borderColor: varsPalette.grey?.[300] ?? theme.palette.grey?.[300],
          ...theme.applyStyles('dark', { borderColor: varsPalette.grey?.[200] ?? theme.palette.grey?.[200] })
        },
        root: { ...getColor({ variant: 'primary', theme }), '&.Mui-error': { ...getColor({ variant: 'error', theme }) } },
        inputSizeSmall: { padding: '7.5px 8px 7.5px 12px' },
        inputMultiline: { padding: 0 },
        colorSecondary: getColor({ variant: 'secondary', theme }),
        colorError: getColor({ variant: 'error', theme }),
        colorWarning: getColor({ variant: 'warning', theme }),
        colorInfo: getColor({ variant: 'info', theme }),
        colorSuccess: getColor({ variant: 'success', theme })
      }
    }
  };
}
