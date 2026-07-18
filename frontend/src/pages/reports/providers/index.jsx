import { useRef, useState } from 'react';
import { useReactToPrint } from 'react-to-print';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';

import { ReportShell, ReportFilterPanel, ReportSummaryCards, ReportTable, ReportPrintDocument, EmptyReportState } from 'components/reports';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import useProvidersReport, { providerFiltersToParams } from 'hooks/reports/useProvidersReport';
import providersReportService from 'services/api/providers-report.service';
import ProvidersReportFilters from './ProvidersReportFilters';
import { providerTableColumns, providerPrintColumns, buildProviderSummaryItems, buildProviderFilterChips } from './columns';

// ==============================|| REPORT — PROVIDERS (تقرير مقدمي الخدمة) ||============================== //

export default function ProvidersReportPage() {
  const { companyName, getLogoSrc } = useCompanySettings();
  const engine = useProvidersReport();
  const [exporting, setExporting] = useState(false);

  const printRef = useRef(null);
  const handlePrint = useReactToPrint({
    contentRef: printRef,
    documentTitle: `تقرير_مقدمي_الخدمة_${new Date().toISOString().slice(0, 10)}`
  });

  const handleExport = async () => {
    setExporting(true);
    try {
      await providersReportService.exportReport({
        ...providerFiltersToParams(engine.appliedFilters),
        sortBy: engine.sort?.key || 'name',
        sortDir: engine.sort?.dir || 'asc'
      });
    } catch (err) {
      // surface as a soft alert via engine error channel is not available here;
      // export errors are non-fatal — the preview keeps working.
      console.error('Providers report export failed:', err);
    } finally {
      setExporting(false);
    }
  };

  const summaryItems = buildProviderSummaryItems(engine.summary);
  const filterChips = buildProviderFilterChips(engine.appliedFilters);
  const logoSrc = typeof getLogoSrc === 'function' ? getLogoSrc() : undefined;

  return (
    <ReportShell
      title="تقرير مقدمي الخدمة"
      description="عرض تحليلي للقراءة فقط: مقدمو الخدمة وعقودهم وقوائم أسعارهم النشطة."
      icon={LocalHospitalIcon}
      lastRefreshed={engine.lastRefreshed}
      loading={engine.loading}
      error={engine.error}
      onRefresh={engine.refresh}
      onExport={handleExport}
      exporting={exporting}
      exportDisabled={engine.total === 0}
      onPrint={handlePrint}
      printDisabled={engine.rows.length === 0}
      filters={
        <ReportFilterPanel onApply={engine.applyFilters} onClear={engine.clearFilters} applying={engine.loading} activeChips={filterChips}>
          <ProvidersReportFilters draft={engine.draftFilters} setDraftFilter={engine.setDraftFilter} />
        </ReportFilterPanel>
      }
      summary={<ReportSummaryCards items={summaryItems} loading={engine.loading && !engine.summary} />}
    >
      <ReportTable
        columns={providerTableColumns}
        rows={engine.rows}
        loading={engine.loading}
        page={engine.page}
        pageSize={engine.pageSize}
        total={engine.total}
        onPageChange={engine.changePage}
        onPageSizeChange={engine.changePageSize}
        sort={engine.sort}
        onSortChange={engine.changeSort}
        getRowKey={(r) => r.id}
        emptyState={<EmptyReportState onClear={engine.clearFilters} />}
      />

      {/* Crystal-style print (current filtered page). Full data set → Excel export. */}
      <ReportPrintDocument
        ref={printRef}
        title="تقرير مقدمي الخدمة"
        subtitle="عرض للقراءة فقط"
        orgName={companyName || 'وعد الطبي'}
        logoSrc={logoSrc}
        generatedAt={engine.lastRefreshed}
        filters={filterChips.map((c) => ({ label: c.label, value: c.value }))}
        summary={summaryItems.map((s) => ({ label: s.label, value: s.value }))}
        columns={providerPrintColumns}
        rows={engine.rows}
        landscape
        footerNote={`${companyName || 'وعد الطبي'} — تقرير مقدمي الخدمة`}
      />
    </ReportShell>
  );
}
