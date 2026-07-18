import Chip from '@mui/material/Chip';

// ── Arabic label maps (display only) ────────────────────────────────────────
export const PROVIDER_TYPE_AR = {
  HOSPITAL: 'مستشفى',
  CLINIC: 'عيادة',
  LAB: 'مختبر',
  PHARMACY: 'صيدلية',
  RADIOLOGY: 'أشعة'
};

export const NETWORK_TIER_AR = {
  IN_NETWORK: 'داخل الشبكة',
  OUT_OF_NETWORK: 'خارج الشبكة',
  PREFERRED: 'مفضل'
};

export const CONTRACT_STATUS_AR = {
  ACTIVE: 'نشط',
  EXPIRING_SOON: 'قارب على الانتهاء',
  EXPIRED: 'منتهي',
  FUTURE: 'مستقبلي',
  INACTIVE: 'غير نشط',
  NONE: 'لا يوجد'
};

const CONTRACT_STATUS_COLOR = {
  ACTIVE: 'success',
  EXPIRING_SOON: 'warning',
  EXPIRED: 'error',
  FUTURE: 'info',
  INACTIVE: 'default',
  NONE: 'default'
};

const fmtDate = (d) => (d ? String(d).slice(0, 10) : '—');

// Single source of truth for the report columns. `value(row)` returns a plain
// primitive (used by print + as table fallback); `render(row)` is optional rich
// table output. No column carries any operational action.
export const providerColumns = [
  { key: 'contractNumber', header: 'رقم العقد', minWidth: 140, value: (r) => r.activeContractCode || '—' },
  { key: 'name', header: 'اسم المزود', sortable: true, minWidth: 200, value: (r) => r.name || '—' },
  { key: 'providerType', header: 'النوع', sortable: true, value: (r) => PROVIDER_TYPE_AR[r.providerType] || r.providerType || '—' },
  { key: 'city', header: 'المدينة', sortable: true, value: (r) => r.city || '—' },
  { key: 'networkStatus', header: 'الشبكة', value: (r) => NETWORK_TIER_AR[r.networkStatus] || r.networkStatus || '—' },
  {
    key: 'active',
    header: 'الحالة',
    sortable: true,
    value: (r) => (r.active ? 'نشط' : 'غير نشط'),
    render: (r) => <Chip size="small" label={r.active ? 'نشط' : 'غير نشط'} color={r.active ? 'success' : 'default'} variant="outlined" />
  },
  { key: 'contractStartDate', header: 'بداية العقد', sortable: true, value: (r) => fmtDate(r.contractStartDate) },
  { key: 'contractEndDate', header: 'نهاية العقد', sortable: true, value: (r) => fmtDate(r.contractEndDate) },
  {
    key: 'contractStatus',
    header: 'حالة العقد',
    value: (r) => CONTRACT_STATUS_AR[r.contractStatus] || r.contractStatus || '—',
    render: (r) => (
      <Chip
        size="small"
        label={CONTRACT_STATUS_AR[r.contractStatus] || r.contractStatus || '—'}
        color={CONTRACT_STATUS_COLOR[r.contractStatus] || 'default'}
        variant="outlined"
      />
    )
  },
  { key: 'hasActivePriceList', header: 'قائمة أسعار نشطة', align: 'center', value: (r) => (r.hasActivePriceList ? 'نعم' : 'لا') },
  { key: 'activePriceListVersionNo', header: 'النسخة النشطة', align: 'center', value: (r) => r.activePriceListVersionNo ?? '—' },
  { key: 'updatedAt', header: 'آخر تحديث', sortable: true, value: (r) => fmtDate(r.updatedAt) }
];

// Adapt to ReportTable (render node) and ReportPrintDocument (plain string).
export const providerTableColumns = providerColumns.map((c) => ({
  key: c.key,
  header: c.header,
  align: c.align,
  minWidth: c.minWidth,
  sortable: c.sortable,
  render: c.render || ((row) => c.value(row))
}));

export const providerPrintColumns = providerColumns.map((c) => ({
  key: c.key,
  header: c.header,
  align: c.align,
  render: (row) => c.value(row)
}));

// ── summary + active-filter chips ───────────────────────────────────────────
const num = (v) => (typeof v === 'number' ? v.toLocaleString('en-US') : (v ?? 0));

export const buildProviderSummaryItems = (summary) => {
  if (!summary) return [];
  return [
    { key: 'total', label: 'إجمالي المزودين', value: num(summary.totalProviders), color: 'primary.main' },
    { key: 'active', label: 'نشط', value: num(summary.activeProviders), color: 'success.main' },
    { key: 'inactive', label: 'غير نشط', value: num(summary.inactiveProviders), color: 'text.secondary' },
    { key: 'withContract', label: 'بعقود نشطة', value: num(summary.withActiveContracts), color: 'info.main' },
    { key: 'withoutContract', label: 'بدون عقد نشط', value: num(summary.withoutActiveContracts), color: 'warning.main' },
    { key: 'withPrice', label: 'بقوائم أسعار نشطة', value: num(summary.withActivePriceLists), color: 'success.main' },
    { key: 'expired', label: 'عقود منتهية', value: num(summary.expiredContracts), color: 'error.main' },
    { key: 'expiringSoon', label: 'تقارب الانتهاء', value: num(summary.expiringSoonContracts), color: 'warning.main' }
  ];
};

const FILTER_LABELS = {
  providerLabel: 'المزود',
  providerType: 'النوع',
  city: 'المدينة',
  active: 'نشط',
  hasActiveContract: 'عقد نشط',
  hasActivePriceList: 'قائمة أسعار نشطة',
  expired: 'منتهي',
  expiringSoon: 'قارب الانتهاء',
  contractStartFrom: 'بداية العقد من',
  contractStartTo: 'بداية العقد إلى',
  contractEndFrom: 'نهاية العقد من',
  contractEndTo: 'نهاية العقد إلى'
};

const boolAr = (v) => (v === true || v === 'true' ? 'نعم' : v === false || v === 'false' ? 'لا' : v);

export const buildProviderFilterChips = (appliedFilters) => {
  if (!appliedFilters) return [];
  return Object.entries(appliedFilters)
    .filter(([k, v]) => FILTER_LABELS[k] && v !== '' && v !== null && v !== undefined)
    .map(([k, v]) => ({
      key: k,
      label: FILTER_LABELS[k],
      value: k === 'providerType' ? PROVIDER_TYPE_AR[v] || v : boolAr(v)
    }));
};
