import { useCallback } from 'react';
import useReportEngine from 'hooks/reports/useReportEngine';
import providersReportService from 'services/api/providers-report.service';

// Draft-filter shape. Tri-state booleans are '' (all) | 'true' | 'false' strings
// in the UI and are converted to real booleans for the API.
export const DEFAULT_PROVIDER_FILTERS = {
  providerId: '', // chosen from the provider picker ('' = all)
  providerLabel: '', // display-only (never sent to the API)
  providerType: '',
  city: '',
  active: '',
  hasActiveContract: '',
  hasActivePriceList: '',
  expiringSoon: '',
  expired: '',
  contractStartFrom: '',
  contractStartTo: '',
  contractEndFrom: '',
  contractEndTo: ''
};

const TEXT_KEYS = ['providerType', 'city', 'contractStartFrom', 'contractStartTo', 'contractEndFrom', 'contractEndTo'];
const BOOL_KEYS = ['active', 'hasActiveContract', 'hasActivePriceList', 'expiringSoon', 'expired'];

/** Convert the UI draft filters into backend query params. */
export const providerFiltersToParams = (filters = {}) => {
  const params = {};
  if (filters.providerId !== undefined && filters.providerId !== null && filters.providerId !== '') {
    params.providerId = Number(filters.providerId);
  }
  TEXT_KEYS.forEach((k) => {
    if (filters[k] !== undefined && filters[k] !== null && filters[k] !== '') params[k] = filters[k];
  });
  BOOL_KEYS.forEach((k) => {
    if (filters[k] === 'true') params[k] = true;
    else if (filters[k] === 'false') params[k] = false;
  });
  return params;
};

/**
 * useProvidersReport — binds the shared report engine to the Providers API.
 * Filtering/pagination/sorting are all server-side.
 */
export default function useProvidersReport() {
  const fetchPage = useCallback(async (filters, { page, pageSize, sort }) => {
    const params = {
      ...providerFiltersToParams(filters),
      page,
      size: pageSize,
      sortBy: sort?.key || 'name',
      sortDir: sort?.dir || 'asc'
    };
    const data = await providersReportService.getReport(params);
    return {
      rows: data?.rows?.content || [],
      total: data?.rows?.totalElements || 0,
      summary: data?.summary || null
    };
  }, []);

  return useReportEngine({
    fetchPage,
    initialFilters: DEFAULT_PROVIDER_FILTERS,
    initialSort: { key: 'name', dir: 'asc' },
    pageSize: 25
  });
}
