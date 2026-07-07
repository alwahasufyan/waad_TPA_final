// ==============================|| OVERRIDES - ALERT TITLE ||============================== //

export default function AccordionDetails(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  const secondaryVars = varsPalette.secondary || theme.palette.secondary || theme.palette.primary || {};
  return {
    MuiAccordionDetails: {
      styleOverrides: {
        root: {
          padding: theme.spacing(2),
          borderTop: '1px solid',
          borderTopColor: secondaryVars.light ?? secondaryVars.main
        }
      }
    }
  };
}
