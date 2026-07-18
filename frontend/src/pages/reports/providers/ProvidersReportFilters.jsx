import { useEffect, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Autocomplete from '@mui/material/Autocomplete';

import { providersService } from 'services/api/providers.service';
import { PROVIDER_TYPE_AR } from './columns';

const TRISTATE = [
  { value: '', label: 'الكل' },
  { value: 'true', label: 'نعم' },
  { value: 'false', label: 'لا' }
];

const cell = { xs: 12, sm: 6, md: 3 };

/**
 * ProvidersReportFilters — report-only filters. The three old text inputs
 * (search / name / code) are replaced by ONE provider picker that autocompletes
 * over the registered providers; clearing it = "all providers".
 */
export default function ProvidersReportFilters({ draft, setDraftFilter }) {
  const [providerOptions, setProviderOptions] = useState([]);

  useEffect(() => {
    let alive = true;
    providersService
      .getSelector()
      .then((list) => {
        if (!alive) return;
        setProviderOptions(
          (Array.isArray(list) ? list : []).map((p) => ({ id: p.id, label: p.name || p.label || `#${p.id}`, code: p.code }))
        );
      })
      .catch(() => alive && setProviderOptions([]));
    return () => {
      alive = false;
    };
  }, []);

  const selectedProvider = useMemo(
    () => providerOptions.find((o) => String(o.id) === String(draft.providerId)) || null,
    [providerOptions, draft.providerId]
  );

  const select = (key, label, options) => (
    <Grid size={cell}>
      <TextField fullWidth size="small" select label={label} value={draft[key] ?? ''} onChange={(e) => setDraftFilter(key, e.target.value)}>
        {options.map((o) => (
          <MenuItem key={o.value} value={o.value}>
            {o.label}
          </MenuItem>
        ))}
      </TextField>
    </Grid>
  );

  const date = (key, label) => (
    <Grid size={cell}>
      <TextField
        fullWidth
        size="small"
        type="date"
        label={label}
        value={draft[key] ?? ''}
        onChange={(e) => setDraftFilter(key, e.target.value)}
        InputLabelProps={{ shrink: true }}
      />
    </Grid>
  );

  return (
    <>
      {/* Single provider picker (autocomplete of registered providers; empty = all) */}
      <Grid size={{ xs: 12, sm: 6, md: 6 }}>
        <Autocomplete
          options={providerOptions}
          value={selectedProvider}
          onChange={(_e, val) => {
            setDraftFilter('providerId', val?.id ?? '');
            setDraftFilter('providerLabel', val?.label ?? '');
          }}
          getOptionLabel={(o) => o?.label || ''}
          isOptionEqualToValue={(o, v) => o.id === v.id}
          noOptionsText="لا توجد نتائج"
          clearText="الكل"
          openText="فتح"
          renderInput={(params) => (
            <TextField {...params} size="small" label="مقدم الخدمة (الكل عند الترك فارغاً)" placeholder="ابحث بالاسم..." />
          )}
        />
      </Grid>

      {select('providerType', 'النوع', [
        { value: '', label: 'الكل' },
        ...Object.entries(PROVIDER_TYPE_AR).map(([value, label]) => ({ value, label }))
      ])}
      <Grid size={cell}>
        <TextField
          fullWidth
          size="small"
          label="المدينة"
          value={draft.city ?? ''}
          onChange={(e) => setDraftFilter('city', e.target.value)}
        />
      </Grid>
      {select('active', 'الحالة (نشط)', TRISTATE)}
      {select('hasActiveContract', 'عقد نشط', TRISTATE)}
      {select('hasActivePriceList', 'قائمة أسعار نشطة', TRISTATE)}
      {select('expiringSoon', 'عقد يقارب الانتهاء', TRISTATE)}
      {select('expired', 'عقد منتهٍ', TRISTATE)}
      {date('contractEndFrom', 'نهاية العقد من')}
      {date('contractEndTo', 'نهاية العقد إلى')}
    </>
  );
}

ProvidersReportFilters.propTypes = {
  draft: PropTypes.object.isRequired,
  setDraftFilter: PropTypes.func.isRequired
};
