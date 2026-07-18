import axiosClient from 'utils/axios';

// Providers report API (read-only). Maps to backend /api/v1/reports/providers.
const BASE = '/reports/providers';

const clean = (params = {}) => Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''));

export const providersReportService = {
  /** Paged rows + backend summary + appliedFilters. */
  getReport: async (params) => {
    const res = await axiosClient.get(BASE, { params: clean(params) });
    return res.data?.data ?? res.data; // unwrap ApiResponse envelope
  },

  /**
   * Server-side Excel export of the FULL filtered result (same filters as
   * preview). Streams the xlsx from the backend and triggers a download.
   */
  exportReport: async (params) => {
    const res = await axiosClient.get(`${BASE}/export`, { params: clean(params), responseType: 'blob' });
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `تقرير_مقدمي_الخدمة_${new Date().toISOString().slice(0, 10)}.xlsx`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }
};

export default providersReportService;
