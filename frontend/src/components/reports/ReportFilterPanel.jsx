import { useState } from 'react';
import PropTypes from 'prop-types';
import Paper from '@mui/material/Paper';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Collapse from '@mui/material/Collapse';
import Grid from '@mui/material/Grid';
import Divider from '@mui/material/Divider';
import FilterListIcon from '@mui/icons-material/FilterList';
import SearchIcon from '@mui/icons-material/Search';
import FilterAltOffIcon from '@mui/icons-material/FilterAltOff';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

import ActiveFilterChips from './ActiveFilterChips';

/**
 * ReportFilterPanel — collapsible, RTL filter panel shared by all reports.
 * The caller passes the filter inputs as children (each wrapped in a Grid item)
 * plus apply/clear handlers. Active-filter chips are shown beneath.
 */
export default function ReportFilterPanel({
  children,
  onApply,
  onClear,
  applying = false,
  activeChips = [],
  onRemoveChip,
  defaultOpen = true,
  title = 'الفلاتر'
}) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <Paper variant="outlined" sx={{ borderRadius: 2, p: { xs: 1.5, sm: 2 } }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between">
        <Stack direction="row" alignItems="center" spacing={1}>
          <FilterListIcon color="primary" fontSize="small" />
          <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
            {title}
          </Typography>
        </Stack>
        <IconButton size="small" onClick={() => setOpen((v) => !v)} aria-label={open ? 'طيّ الفلاتر' : 'إظهار الفلاتر'}>
          <ExpandMoreIcon sx={{ transform: open ? 'rotate(180deg)' : 'none', transition: '0.2s' }} />
        </IconButton>
      </Stack>

      <Collapse in={open} timeout="auto" unmountOnExit>
        <Divider sx={{ my: 1.5 }} />
        <Box
          component="form"
          onSubmit={(e) => {
            e.preventDefault();
            onApply?.();
          }}
        >
          <Grid container spacing={2}>
            {children}
          </Grid>

          <Stack direction="row" spacing={1} sx={{ mt: 2, flexWrap: 'wrap' }} useFlexGap>
            <Button type="submit" variant="contained" color="primary" startIcon={<SearchIcon />} disabled={applying}>
              تطبيق الفلاتر
            </Button>
            <Button type="button" variant="outlined" color="inherit" startIcon={<FilterAltOffIcon />} onClick={onClear} disabled={applying}>
              مسح الفلاتر
            </Button>
          </Stack>
        </Box>
      </Collapse>

      <ActiveFilterChips chips={activeChips} onRemove={onRemoveChip} />
    </Paper>
  );
}

ReportFilterPanel.propTypes = {
  children: PropTypes.node,
  onApply: PropTypes.func,
  onClear: PropTypes.func,
  applying: PropTypes.bool,
  activeChips: PropTypes.array,
  onRemoveChip: PropTypes.func,
  defaultOpen: PropTypes.bool,
  title: PropTypes.string
};
