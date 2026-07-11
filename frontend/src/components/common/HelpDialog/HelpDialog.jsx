import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Button,
  IconButton,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Tooltip
} from '@mui/material';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import CircleIcon from '@mui/icons-material/Circle';

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * HELP DIALOG — system-wide UX standard (Design Review §9.1, approved v1.1)
 * ═══════════════════════════════════════════════════════════════════════════
 * A small «؟» button that opens a dialog of AT MOST 7 Arabic, action-phrased
 * bullets. No PDFs, no manuals, no videos. Help content lives beside the page
 * code and is reviewed with it.
 *
 * Usage:  <HelpDialog title="استيراد قوائم الأسعار" points={[ '...', ... ]} />
 */
const HelpDialog = ({ title, points = [] }) => {
  const [open, setOpen] = useState(false);
  const shown = points.slice(0, 7); // hard cap — the standard, enforced

  return (
    <>
      <Tooltip title="مساعدة">
        <IconButton size="small" color="primary" onClick={() => setOpen(true)} aria-label="مساعدة">
          <HelpOutlineIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{title}</DialogTitle>
        <DialogContent>
          <List dense>
            {shown.map((point, i) => (
              <ListItem key={i} disableGutters>
                <ListItemIcon sx={{ minWidth: 22 }}>
                  <CircleIcon sx={{ fontSize: 8 }} color="primary" />
                </ListItemIcon>
                <ListItemText primary={point} primaryTypographyProps={{ variant: 'body2' }} />
              </ListItem>
            ))}
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>فهمت</Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

HelpDialog.propTypes = {
  title: PropTypes.string.isRequired,
  points: PropTypes.arrayOf(PropTypes.string).isRequired
};

export default HelpDialog;
