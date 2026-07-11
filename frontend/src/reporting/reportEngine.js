import {
  REPORT_DOMAINS,
  REPORT_REGISTRY,
  REQUIRED_REPORT_FIELDS,
  REPORT_REGISTRY_GOVERNANCE,
  getRegistryContractIssues,
  getRegistryDuplicateCodeIssues
} from './reportRegistry';

export const getReportDomains = () => REPORT_DOMAINS;

export const getDomainByKey = (domainKey) =>
  REPORT_DOMAINS.find((domain) => domain.key === domainKey) || null;

const isReportVisibleOnSurface = (report, surfaceKey) => {
  if (!surfaceKey) return true;
  const surfaces = Array.isArray(report.surfaces) ? report.surfaces : ['report-center'];
  return surfaces.includes(surfaceKey);
};

export const getReportsByDomain = (domainKey, surfaceKey = null) =>
  REPORT_REGISTRY.filter((report) => report.domain === domainKey && isReportVisibleOnSurface(report, surfaceKey));

export const getReportsByClassification = (domainKey, classification, surfaceKey = null) =>
  getReportsByDomain(domainKey, surfaceKey).filter((report) => report.classification === classification);

export const getReportByCode = (code) =>
  REPORT_REGISTRY.find((report) => report.code === code) || null;

export const getReportContractRequirements = () => REQUIRED_REPORT_FIELDS;

export const getRegistryGovernance = () => REPORT_REGISTRY_GOVERNANCE;

export const verifyRegistryContractCompliance = () => {
  const issues = getRegistryContractIssues();
  const duplicateCodes = getRegistryDuplicateCodeIssues();
  return {
    compliant: issues.length === 0 && duplicateCodes.length === 0,
    issues,
    duplicateCodes
  };
};

export const getDomainStats = (domainKey, surfaceKey = null) => {
  const reports = getReportsByDomain(domainKey, surfaceKey);
  const activeReports = reports.filter((r) => r.status === 'active');
  const plannedReports = reports.filter((r) => r.status !== 'active');

  return {
    total: reports.length,
    active: activeReports.length,
    planned: plannedReports.length,
    operational: reports.filter((r) => r.classification === 'operational').length,
    analytical: reports.filter((r) => r.classification === 'analytical').length
  };
};
