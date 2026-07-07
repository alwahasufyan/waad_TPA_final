// ==============================|| OVERRIDES - LIST ITEM ICON ||============================== //

export default function ListItemButton(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiListItemButton: {
      styleOverrides: {
        root: {
          '&.Mui-selected': {
            color: varsPalette.primary?.main ?? theme.palette.primary?.main,
            ...theme.applyStyles('dark', { color: varsPalette.primary?.darker ?? theme.palette.primary?.darker }),
            '& .MuiListItemIcon-root': {
              color: varsPalette.primary?.main ?? theme.palette.primary?.main
            }
          }
        }
      }
    }
  };
}
