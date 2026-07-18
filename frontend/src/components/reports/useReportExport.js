import { useCallback, useState } from 'react';
import { exportToExcel } from 'utils/exportUtils';

/**
 * useReportExport — turns the FULL filtered result set into an Excel file whose
 * columns/headers/types match the report table. It calls fetchAllForExport()
 * (from useReportEngine), so the export always reflects the active filters — not
 * just the visible page.
 *
 * columns: the same array passed to ReportTable, optionally with:
 *   - exportValue(row): primitive value for the cell (defaults to row[key])
 *   - exportType: 'string' | 'number' | 'currency' | 'date'
 *
 * @param {Object}   cfg
 * @param {Function} cfg.fetchAllForExport  async () => rows[]
 * @param {Array}    cfg.columns
 * @param {string}   cfg.filename           base filename (date is appended)
 * @param {string}   [cfg.reportTitle]
 * @param {string}   [cfg.companyName]
 */
export default function useReportExport({ fetchAllForExport, columns, filename, reportTitle, companyName }) {
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);

  const exportExcel = useCallback(async () => {
    setExporting(true);
    setExportError(null);
    try {
      const rows = (await fetchAllForExport()) || [];
      if (!rows.length) {
        setExportError('لا توجد بيانات للتصدير.');
        return;
      }

      const exportColumns = columns
        .filter((c) => c.exportable !== false)
        .map((c) => ({ key: c.key, header: typeof c.header === 'string' ? c.header : c.key, type: c.exportType || 'string' }));

      const data = rows.map((row) =>
        exportColumns.reduce((acc, col) => {
          const source = columns.find((c) => c.key === col.key);
          acc[col.key] = source?.exportValue ? source.exportValue(row) : row[col.key];
          return acc;
        }, {})
      );

      const stamp = new Date().toISOString().slice(0, 10);
      await exportToExcel(data, `${filename}_${stamp}`, { companyName, reportTitle, columns: exportColumns });
    } catch (err) {
      setExportError(err?.message || 'تعذّر تصدير الملف.');
    } finally {
      setExporting(false);
    }
  }, [fetchAllForExport, columns, filename, reportTitle, companyName]);

  return { exportExcel, exporting, exportError };
}
