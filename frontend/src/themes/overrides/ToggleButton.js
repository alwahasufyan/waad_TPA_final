// ==============================|| OVERRIDES - TOGGLE BUTTON ||============================== //

export default function ToggleButton(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiToggleButton: {
      styleOverrides: {
        root: {
          '&.Mui-disabled': {
            borderColor: varsPalette.divider ?? theme.palette.divider,
            color: (varsPalette.text && varsPalette.text.disabled) ?? (theme.palette && theme.palette.text && theme.palette.text.disabled)
          },
          '&:focus-visible': {
            outline: `2px solid ${(varsPalette.secondary && varsPalette.secondary.dark) || (theme.palette && theme.palette.secondary && theme.palette.secondary.dark)}`,
            outlineOffset: 2
          }
        }
      }
    }
  };
}
