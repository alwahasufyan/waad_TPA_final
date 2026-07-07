// ==============================|| OVERRIDES - DIALOG CONTENT TEXT ||============================== //

export default function DialogContentText(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiDialogContentText: {
      styleOverrides: {
        root: {
          fontSize: '0.875rem',
          color: varsPalette.text?.primary ?? theme.palette.text?.primary
        }
      }
    }
  };
}
