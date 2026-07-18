import { forwardRef } from 'react';
import PropTypes from 'prop-types';

/**
 * ReportPrintDocument — a Crystal-Reports-style printable document.
 * Rendered off-screen and printed via react-to-print (contentRef). It contains
 * ONLY report content: branding, title, applied filters, generation date,
 * summary totals, and a page-friendly read-only table. No nav / actions / filter
 * controls. RTL, with repeating table header and page-break-safe rows.
 *
 * columns: [{ key, header, align?, render?(row) }]
 */
const ReportPrintDocument = forwardRef(function ReportPrintDocument(
  {
    title,
    subtitle,
    orgName = 'وعد الطبي',
    logoSrc,
    generatedAt,
    filters = [],
    summary = [],
    columns = [],
    rows = [],
    footerNote,
    landscape = false
  },
  ref
) {
  const genText = generatedAt ? new Date(generatedAt).toLocaleString('ar-LY') : new Date().toLocaleString('ar-LY');

  return (
    // Hidden on screen; react-to-print clones this node into the print frame.
    <div style={{ display: 'none' }}>
      <div ref={ref} dir="rtl" className="waad-report-print">
        <style>{`
          @page { size: A4 ${landscape ? 'landscape' : 'portrait'}; margin: 14mm 10mm; }
          .waad-report-print { font-family: 'Tajawal','Cairo','Segoe UI',sans-serif; color: #111; direction: rtl; }
          .waad-report-print * { box-sizing: border-box; }
          .wrp-head { display: flex; align-items: center; justify-content: space-between; border-bottom: 2px solid #0d9488; padding-bottom: 8px; margin-bottom: 10px; }
          .wrp-org { font-size: 16px; font-weight: 800; color: #0d9488; }
          .wrp-logo { height: 46px; width: auto; object-fit: contain; }
          .wrp-title { font-size: 18px; font-weight: 800; margin: 6px 0 2px; }
          .wrp-sub { font-size: 12px; color: #555; }
          .wrp-meta { display: flex; flex-wrap: wrap; gap: 6px 18px; font-size: 11px; color: #333; margin: 8px 0; }
          .wrp-meta b { color: #0d9488; }
          .wrp-summary { display: flex; flex-wrap: wrap; gap: 8px; margin: 8px 0 12px; }
          .wrp-kpi { border: 1px solid #cbd5e1; border-radius: 6px; padding: 6px 10px; min-width: 110px; }
          .wrp-kpi .k { font-size: 10px; color: #555; }
          .wrp-kpi .v { font-size: 14px; font-weight: 800; }
          table.wrp-table { width: 100%; border-collapse: collapse; font-size: 11px; }
          table.wrp-table thead { display: table-header-group; }
          table.wrp-table th { background: #0d9488; color: #fff; border: 1px solid #94a3b8; padding: 5px 6px; text-align: right; font-weight: 700; }
          table.wrp-table td { border: 1px solid #cbd5e1; padding: 4px 6px; text-align: right; }
          table.wrp-table tr { page-break-inside: avoid; }
          table.wrp-table tbody tr:nth-child(even) { background: #f1f5f9; }
          .wrp-foot { margin-top: 10px; border-top: 1px solid #cbd5e1; padding-top: 6px; font-size: 10px; color: #666; display: flex; justify-content: space-between; }
        `}</style>

        {/* Header: branding + generation date */}
        <div className="wrp-head">
          <div>
            <div className="wrp-org">{orgName}</div>
            {subtitle ? <div className="wrp-sub">{subtitle}</div> : null}
          </div>
          {logoSrc ? <img className="wrp-logo" src={logoSrc} alt={orgName} /> : null}
        </div>

        <div className="wrp-title">{title}</div>
        <div className="wrp-meta">
          <span>
            <b>تاريخ التوليد:</b> {genText}
          </span>
          <span>
            <b>عدد الصفوف:</b> {rows.length.toLocaleString('en-US')}
          </span>
        </div>

        {/* Applied filters */}
        {filters.length > 0 && (
          <div className="wrp-meta">
            {filters.map((f, i) => (
              <span key={i}>
                <b>{f.label}:</b> {f.value}
              </span>
            ))}
          </div>
        )}

        {/* Summary totals */}
        {summary.length > 0 && (
          <div className="wrp-summary">
            {summary.map((s, i) => (
              <div className="wrp-kpi" key={i}>
                <div className="k">{s.label}</div>
                <div className="v">{s.value}</div>
              </div>
            ))}
          </div>
        )}

        {/* Read-only table */}
        <table className="wrp-table">
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c.key} style={{ textAlign: c.align || 'right' }}>
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, r) => (
              <tr key={row.id ?? r}>
                {columns.map((c) => (
                  <td key={c.key} style={{ textAlign: c.align || 'right' }}>
                    {c.render ? c.render(row) : (row[c.key] ?? '—')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>

        <div className="wrp-foot">
          <span>{footerNote || `${orgName} — تقرير رسمي`}</span>
          <span>{genText}</span>
        </div>
      </div>
    </div>
  );
});

ReportPrintDocument.propTypes = {
  title: PropTypes.string.isRequired,
  subtitle: PropTypes.string,
  orgName: PropTypes.string,
  logoSrc: PropTypes.string,
  generatedAt: PropTypes.oneOfType([PropTypes.string, PropTypes.number, PropTypes.instanceOf(Date)]),
  filters: PropTypes.array,
  summary: PropTypes.array,
  columns: PropTypes.array,
  rows: PropTypes.array,
  footerNote: PropTypes.string,
  landscape: PropTypes.bool
};

export default ReportPrintDocument;
