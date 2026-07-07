// ==============================|| OVERRIDES - CHART AXIS HIGHLIGHT ||============================== //

export default function ChartsAxiasHighlight(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  const secondaryVars = varsPalette.secondary || theme.palette.secondary || theme.palette.primary || {};
  return {
    MuiChartsAxisHighlight: {
      styleOverrides: {
        root: {
          '&&': {
            stroke: secondaryVars.light ?? secondaryVars.main,
            strokeDasharray: '4 4',
            strokeWidth: 1
          },
          ':where([data-color-scheme="light"]) &, :where([data-color-scheme="dark"]) &': {
            stroke: secondaryVars.light ?? secondaryVars.main
          }
        }
      }
    }
  };
}
