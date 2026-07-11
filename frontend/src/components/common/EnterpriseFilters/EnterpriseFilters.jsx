import { useState, useMemo } from 'react';
import PropTypes from 'prop-types';
import {
  Box,
  Grid,
  Paper,
  Stack,
  Chip,
  Button,
  Badge,
  Collapse,
  Drawer,
  Divider,
  Typography,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  OutlinedInput,
  InputAdornment,
  IconButton,
  useMediaQuery,
  useTheme
} from '@mui/material';
import FilterListIcon from '@mui/icons-material/FilterList';
import ClearIcon from '@mui/icons-material/Clear';
import CloseIcon from '@mui/icons-material/Close';

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ENTERPRISE FILTER FRAMEWORK (Stage 2 / Workflow 1 — W1.1)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * A single, reusable, config-driven filter surface for every operational table.
 * Built to reclaim vertical space for the table (the primary element) while
 * keeping filters one click away.
 *
 * Features:
 *  ✅ Collapsible inline panel — collapsed by default → maximum table area
 *  ✅ Drawer variant (and automatic drawer on small screens)
 *  ✅ Active filter chips (each removable) always visible in the compact bar
 *  ✅ Clear-all
 *  ✅ Remembers open/closed state per `filterKey` (values are remembered by the
 *     page via the companion `useFilterState` hook)
 *  ✅ Responsive + keyboard friendly (real buttons, Esc closes the drawer)
 *
 * Config-driven: pass `fields`, current `values`, and `onChange`. No workflow
 * or data logic lives here — pages keep full control of their queries.
 *
 * field: {
 *   key, label,
 *   type: 'text' | 'select' | 'multiselect' | 'date',
 *   options?: [{ value, label }],   // select / multiselect
 *   icon?: node,
 *   placeholder?: string,
 *   hidden?: boolean,               // e.g. employer selector hidden for non-admins
 *   gridSize?: number               // md columns (default 3)
 * }
 */
const isEmpty = (v) => v === undefined || v === null || v === '' || (Array.isArray(v) && v.length === 0);

const EnterpriseFilters = ({
  fields = [],
  values = {},
  onChange,
  onClear,
  filterKey,
  title = 'الفلاتر',
  variant = 'auto',
  defaultOpen = false,
  embedded = false
}) => {
  const theme = useTheme();
  const isSmall = useMediaQuery(theme.breakpoints.down('md'));

  const openStorageKey = filterKey ? `waad:flt:${filterKey}:open` : null;
  const [open, setOpen] = useState(() => {
    if (!openStorageKey) return defaultOpen;
    try {
      const raw = localStorage.getItem(openStorageKey);
      return raw === null ? defaultOpen : raw === '1';
    } catch {
      return defaultOpen;
    }
  });

  const visibleFields = useMemo(() => fields.filter((f) => !f.hidden), [fields]);

  // Drawer when explicitly requested, or on small screens (keeps the table full-width).
  const useDrawer = variant === 'drawer' || (variant === 'auto' && isSmall);

  const toggleOpen = () => {
    setOpen((prev) => {
      const next = !prev;
      try { if (openStorageKey) localStorage.setItem(openStorageKey, next ? '1' : '0'); } catch { /* ignore */ }
      return next;
    });
  };

  const setField = (key, value) => onChange?.({ ...values, [key]: value });

  const clearField = (field) => {
    const cleared = field.type === 'multiselect' ? [] : field.type === 'date' || field.type === 'select' ? null : '';
    setField(field.key, cleared);
  };

  const removeMultiValue = (field, val) =>
    setField(field.key, (values[field.key] || []).filter((v) => v !== val));

  const handleClearAll = () => {
    if (onClear) return onClear();
    const cleared = {};
    visibleFields.forEach((f) => {
      cleared[f.key] = f.type === 'multiselect' ? [] : f.type === 'text' ? '' : null;
    });
    onChange?.({ ...values, ...cleared });
  };

  // Active filter chips (each removable)
  const chips = useMemo(() => {
    const out = [];
    visibleFields.forEach((field) => {
      const v = values[field.key];
      if (isEmpty(v)) return;
      if (field.type === 'multiselect') {
        (v || []).forEach((val) => {
          const opt = (field.options || []).find((o) => o.value === val);
          out.push({ id: `${field.key}:${val}`, label: opt ? opt.label : val, onDelete: () => removeMultiValue(field, val) });
        });
      } else if (field.type === 'select') {
        const opt = (field.options || []).find((o) => String(o.value) === String(v));
        out.push({ id: field.key, label: `${field.label}: ${opt ? opt.label : v}`, onDelete: () => clearField(field) });
      } else {
        out.push({ id: field.key, label: `${field.label}: ${v}`, onDelete: () => clearField(field) });
      }
    });
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visibleFields, values]);

  const activeCount = chips.length;

  const renderField = (field) => {
    const v = values[field.key];
    const startAdornment = field.icon ? (
      <InputAdornment position="start">{field.icon}</InputAdornment>
    ) : undefined;

    switch (field.type) {
      case 'select':
        return (
          <FormControl fullWidth size="small">
            <InputLabel id={`ef-${field.key}`}>{field.label}</InputLabel>
            <Select
              labelId={`ef-${field.key}`}
              value={v ?? ''}
              label={field.label}
              onChange={(e) => setField(field.key, e.target.value === '' ? null : e.target.value)}
              startAdornment={startAdornment}
            >
              <MenuItem value=""><em>{field.placeholder || 'الكل'}</em></MenuItem>
              {(field.options || []).map((o) => (
                <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
              ))}
            </Select>
          </FormControl>
        );
      case 'multiselect':
        return (
          <FormControl fullWidth size="small">
            <InputLabel id={`ef-${field.key}`}>{field.label}</InputLabel>
            <Select
              labelId={`ef-${field.key}`}
              multiple
              value={v || []}
              onChange={(e) => setField(field.key, e.target.value)}
              input={<OutlinedInput label={field.label} />}
              renderValue={(selected) => `${selected.length} محدد`}
            >
              {(field.options || []).map((o) => (
                <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
              ))}
            </Select>
          </FormControl>
        );
      case 'date':
        return (
          <TextField
            fullWidth
            size="small"
            type="date"
            label={field.label}
            value={v || ''}
            onChange={(e) => setField(field.key, e.target.value || null)}
            InputLabelProps={{ shrink: true }}
            InputProps={{ startAdornment }}
          />
        );
      case 'text':
      default:
        return (
          <TextField
            fullWidth
            size="small"
            label={field.label}
            value={v || ''}
            placeholder={field.placeholder}
            onChange={(e) => setField(field.key, e.target.value)}
            InputProps={{
              startAdornment,
              endAdornment: v ? (
                <InputAdornment position="end">
                  <IconButton size="small" aria-label="مسح" onClick={() => setField(field.key, '')}>
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : undefined
            }}
          />
        );
    }
  };

  const fieldsGrid = (
    <Grid container spacing={2}>
      {visibleFields.map((field) => (
        // Embedded (e.g. inside the Workspace Sidebar) → stack fields full-width,
        // one per row, for a clean vertical filter list.
        <Grid key={field.key} size={embedded ? 12 : { xs: 12, sm: 6, md: field.gridSize || 3 }}>
          {renderField(field)}
        </Grid>
      ))}
    </Grid>
  );

  // Compact bar (always visible) — keeps the table as large as possible.
  const compactBar = (
    <Stack
      direction="row"
      alignItems="center"
      flexWrap="wrap"
      spacing={1}
      useFlexGap
      sx={{ mb: chips.length || (open && !useDrawer) ? 1 : 1 }}
    >
      <Badge badgeContent={activeCount} color="primary" overlap="circular">
        <Button
          size="small"
          variant="outlined"
          startIcon={<FilterListIcon />}
          onClick={toggleOpen}
          sx={{ whiteSpace: 'nowrap' }}
        >
          {title}
        </Button>
      </Badge>

      {chips.map((c) => (
        <Chip key={c.id} label={c.label} size="small" onDelete={c.onDelete} />
      ))}

      {activeCount > 0 && (
        <Button size="small" color="inherit" startIcon={<ClearIcon />} onClick={handleClearAll} sx={{ ml: 'auto' }}>
          مسح الكل
        </Button>
      )}
    </Stack>
  );

  // Embedded mode — render just the fields + active chips + clear-all, for hosting
  // inside another container (e.g. the Workspace Sidebar). No own toggle/collapse.
  if (embedded) {
    return (
      <Box>
        {activeCount > 0 && (
          <Stack direction="row" flexWrap="wrap" spacing={1} useFlexGap sx={{ mb: 1.5 }}>
            {chips.map((c) => (
              <Chip key={c.id} label={c.label} size="small" onDelete={c.onDelete} />
            ))}
            <Button size="small" color="inherit" startIcon={<ClearIcon />} onClick={handleClearAll}>
              مسح الكل
            </Button>
          </Stack>
        )}
        {fieldsGrid}
      </Box>
    );
  }

  return (
    <Box sx={{ mb: 1 }}>
      {compactBar}

      {/* Inline collapsible panel (desktop) */}
      {!useDrawer && (
        <Collapse in={open} timeout="auto" unmountOnExit>
          <Paper variant="outlined" sx={{ p: '1.0rem', mb: '0.5rem' }}>
            {fieldsGrid}
          </Paper>
        </Collapse>
      )}

      {/* Drawer (small screens / drawer variant) */}
      {useDrawer && (
        <Drawer anchor="left" open={open} onClose={toggleOpen} PaperProps={{ sx: { width: { xs: '85vw', sm: 400 } } }}>
          <Box sx={{ p: 2 }} role="region" aria-label={title}>
            <Stack direction="row" alignItems="center" sx={{ mb: 1 }}>
              <FilterListIcon sx={{ mr: 1, color: 'text.secondary' }} />
              <Typography sx={{ fontWeight: 600 }}>{title}</Typography>
              <IconButton size="small" onClick={toggleOpen} sx={{ ml: 'auto' }} aria-label="إغلاق">
                <CloseIcon fontSize="small" />
              </IconButton>
            </Stack>
            <Divider sx={{ mb: 2 }} />
            {fieldsGrid}
            {activeCount > 0 && (
              <Button fullWidth color="inherit" startIcon={<ClearIcon />} onClick={handleClearAll} sx={{ mt: 2 }}>
                مسح الكل
              </Button>
            )}
          </Box>
        </Drawer>
      )}
    </Box>
  );
};

EnterpriseFilters.propTypes = {
  fields: PropTypes.arrayOf(
    PropTypes.shape({
      key: PropTypes.string.isRequired,
      label: PropTypes.string.isRequired,
      type: PropTypes.oneOf(['text', 'select', 'multiselect', 'date']),
      options: PropTypes.array,
      icon: PropTypes.node,
      placeholder: PropTypes.string,
      hidden: PropTypes.bool,
      gridSize: PropTypes.number
    })
  ),
  values: PropTypes.object,
  onChange: PropTypes.func,
  onClear: PropTypes.func,
  filterKey: PropTypes.string,
  title: PropTypes.string,
  variant: PropTypes.oneOf(['auto', 'panel', 'drawer']),
  defaultOpen: PropTypes.bool,
  embedded: PropTypes.bool
};

export default EnterpriseFilters;
