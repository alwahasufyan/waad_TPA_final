// ==============================|| OVERRIDES - TOOLTIP ||============================== //

export default function Tooltip(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiTooltip: {
      styleOverrides: {
        tooltip: {
          color: varsPalette.background?.paper ?? theme.palette.background?.paper
        }
      }
    }
  };
}
