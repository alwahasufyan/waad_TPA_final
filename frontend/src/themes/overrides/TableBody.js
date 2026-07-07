// ==============================|| OVERRIDES - TABLE ROW ||============================== //

export default function TableBody(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  const hoverStyle = {
    '&:hover': {
      backgroundColor:
        (varsPalette.action && varsPalette.action.hover) ?? (theme.palette && theme.palette.action && theme.palette.action.hover)
    }
  };

  return {
    MuiTableBody: {
      styleOverrides: {
        root: {
          backgroundColor: varsPalette.background?.paper ?? theme.palette.background?.paper,
          '& .MuiTableRow-root': {
            ...hoverStyle
          }
        }
      }
    }
  };
}
