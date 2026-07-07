// ==============================|| OVERRIDES - ALERT TITLE ||============================== //

export default function Accordion(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  const secondaryVars = varsPalette.secondary || theme.palette.secondary || theme.palette.primary || {};
  return {
    MuiAccordion: {
      defaultProps: {
        disableGutters: true,
        square: true,
        elevation: 0
      },
      styleOverrides: {
        root: {
          border: '1px solid',
          borderColor: secondaryVars.light ?? secondaryVars.main,
          '&:not(:last-child)': {
            borderBottom: 0
          },
          '&:before': {
            display: 'none'
          },
          '&.Mui-disabled': {
            backgroundColor: secondaryVars.lighter ?? secondaryVars.main
          }
        }
      }
    }
  };
}
