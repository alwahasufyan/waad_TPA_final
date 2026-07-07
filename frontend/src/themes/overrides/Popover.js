// ==============================|| OVERRIDES - DIALOG CONTENT TEXT ||============================== //

export default function Popover(theme) {
  const varsCustomShadows = (theme.vars && theme.vars.customShadows) || theme.customShadows || {};
  return {
    MuiPopover: {
      styleOverrides: {
        paper: {
          boxShadow: varsCustomShadows.z1 ?? (theme.customShadows && theme.customShadows.z1)
        }
      }
    }
  };
}
