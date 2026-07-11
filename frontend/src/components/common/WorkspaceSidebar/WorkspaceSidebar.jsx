import { useState } from 'react';
import PropTypes from 'prop-types';
import { Box, Drawer, Stack, Typography, Divider, IconButton, Button, Badge, Tooltip } from '@mui/material';
import TuneIcon from '@mui/icons-material/Tune';
import CloseIcon from '@mui/icons-material/Close';

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * WORKSPACE SIDEBAR (Stage 2 / Workflow 1 — W1.2)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * A reusable, collapsible side panel that hosts a page's operational tools
 * (filters, export, print, refresh, view options…) so they no longer consume
 * vertical space above the table. The table becomes the primary workspace and
 * starts immediately below a slim page header; tools stay one click away.
 *
 * Renders both a compact trigger (with an active-count badge) and a right-side
 * Drawer holding `children`. Self-contained; no layout coupling — the page keeps
 * full width for its table. Reusable across Claims, Provider Portal, Financial
 * Settlements and Provider Contracts.
 *
 * Usage:
 *   <WorkspaceSidebar title="أدوات العمل" badgeCount={activeFilters}>
 *     <Actions/>            // refresh / export / print buttons
 *     <EnterpriseFilters embedded .../>
 *   </WorkspaceSidebar>
 */
const WorkspaceSidebar = ({ title = 'أدوات العمل', badgeCount = 0, children, triggerLabel, width = 360 }) => {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Tooltip title={title}>
        <Badge badgeContent={badgeCount} color="primary" overlap="rectangular">
          <Button
            variant="outlined"
            size="small"
            startIcon={<TuneIcon />}
            onClick={() => setOpen(true)}
            aria-label={title}
            sx={{ whiteSpace: 'nowrap' }}
          >
            {triggerLabel || title}
          </Button>
        </Badge>
      </Tooltip>

      <Drawer
        anchor="right"
        open={open}
        onClose={() => setOpen(false)}
        PaperProps={{ sx: { width: { xs: '90vw', sm: width } } }}
      >
        <Box sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column' }} role="region" aria-label={title}>
          <Stack direction="row" alignItems="center" sx={{ mb: 1 }}>
            <TuneIcon sx={{ mr: 1, color: 'text.secondary' }} />
            <Typography sx={{ fontWeight: 700 }}>{title}</Typography>
            <IconButton size="small" onClick={() => setOpen(false)} sx={{ ml: 'auto' }} aria-label="إغلاق">
              <CloseIcon fontSize="small" />
            </IconButton>
          </Stack>
          <Divider sx={{ mb: 2 }} />
          <Box sx={{ overflowY: 'auto', flexGrow: 1 }}>{children}</Box>
        </Box>
      </Drawer>
    </>
  );
};

/** Small labelled group inside the sidebar, for grouping related tools. */
export const WorkspaceSection = ({ label, children }) => (
  <Box sx={{ mb: 2.5 }}>
    {label && (
      <Typography variant="overline" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5 }}>
        {label}
      </Typography>
    )}
    {children}
  </Box>
);

WorkspaceSection.propTypes = {
  label: PropTypes.string,
  children: PropTypes.node
};

WorkspaceSidebar.propTypes = {
  title: PropTypes.string,
  badgeCount: PropTypes.number,
  children: PropTypes.node,
  triggerLabel: PropTypes.string,
  width: PropTypes.number
};

export default WorkspaceSidebar;
