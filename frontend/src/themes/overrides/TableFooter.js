// ==============================|| OVERRIDES - TABLE CELL ||============================== //

export default function TableFooter(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiTableFooter: {
      styleOverrides: {
        root: {
          backgroundColor: varsPalette.grey?.[50] ?? theme.palette.grey?.[50],
          borderTop: '2px solid',
          borderTopColor: varsPalette.divider ?? theme.palette.divider,
          borderBottom: '1px solid',
          borderBottomColor: varsPalette.divider ?? theme.palette.divider
        }
      }
    }
  };
}
