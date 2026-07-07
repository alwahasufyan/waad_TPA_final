// project imports
import getColors from 'utils/getColors';
import getShadow from 'utils/getShadow';

// ==============================|| OVERRIDES - INPUT BORDER & SHADOWS ||============================== //

function getColor({ variant, theme }) {
  const colors = getColors(theme, variant);
  const { light } = colors;

  const shadows = getShadow(theme, `${variant}`);

  return {
    '&:hover .MuiPickersOutlinedInput-notchedOutline': { borderColor: light },
    '&.Mui-focused': { boxShadow: shadows, '& .MuiPickersOutlinedInput-notchedOutline': { border: '1px solid', borderColor: light } }
  };
}

// ==============================|| OVERRIDES - PICKERS TEXT FIELD ||============================== //

export default function PickersTextField(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiPickersTextField: {
      defaultProps: {
        variant: 'outlined',
        color: 'primary'
      },
      styleOverrides: {
        root: {
          '& .MuiPickersInputBase-sectionsContainer': {
            padding: '10.5px 14px 10.5px 0px'
          },
          '& .MuiPickersOutlinedInput-notchedOutline': {
            borderColor: varsPalette.grey?.[300] ?? theme.palette.grey?.[300],
            ...theme.applyStyles('dark', { borderColor: varsPalette.grey?.[200] ?? theme.palette.grey?.[200] })
          },
          variants: [
            {
              props: { variant: 'outlined' },
              style: ({ color }) => {
                return {
                  '& .MuiPickersInputBase-root': {
                    ...getColor({ variant: color, theme })
                  }
                };
              }
            },
            {
              props: { size: 'small' },
              style: {
                '& .MuiPickersInputBase-sectionsContainer': {
                  padding: '7.5px 8px 7.5px 0px'
                }
              }
            }
          ]
        }
      }
    }
  };
}
