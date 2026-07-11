export const REPORT_DOMAINS = Object.freeze([
  {
    key: 'claims',
    titleAr: 'المطالبات',
    titleEn: 'Claims',
    resource: 'report_domain_claims'
  },
  {
    key: 'members',
    titleAr: 'المستفيدون',
    titleEn: 'Members',
    resource: 'report_domain_members'
  },
  {
    key: 'employers',
    titleAr: 'جهات العمل',
    titleEn: 'Employers',
    resource: 'report_domain_employers'
  },
  {
    key: 'providers',
    titleAr: 'مقدمو الخدمة',
    titleEn: 'Providers',
    resource: 'report_domain_providers'
  },
  {
    key: 'contracts',
    titleAr: 'العقود',
    titleEn: 'Contracts',
    resource: 'report_domain_contracts'
  },
  {
    key: 'price-lists',
    titleAr: 'قوائم الأسعار',
    titleEn: 'Price Lists',
    resource: 'report_domain_price_lists'
  },
  {
    key: 'benefit-policies',
    titleAr: 'وثائق المنافع',
    titleEn: 'Benefit Policies',
    resource: 'report_domain_benefit_policies'
  },
  {
    key: 'financial-settlements',
    titleAr: 'التسويات المالية',
    titleEn: 'Financial Settlements',
    resource: 'report_domain_financial_settlements'
  },
  {
    key: 'audit',
    titleAr: 'التدقيق',
    titleEn: 'Audit',
    resource: 'report_domain_audit'
  },
  {
    key: 'system-analytics',
    titleAr: 'إحصائيات النظام',
    titleEn: 'System Analytics',
    resource: 'report_domain_system_analytics'
  }
]);

const DEFAULT_CAPABILITIES = Object.freeze({
  filters: true,
  excel: true,
  pdf: true,
  print: true,
  columnSelection: true,
  savedFilters: true,
  help: true
});

const mergeCapabilities = (capabilities) => ({
  ...DEFAULT_CAPABILITIES,
  ...(capabilities || {})
});

const deepFreeze = (obj) => {
  Object.freeze(obj);
  Object.getOwnPropertyNames(obj).forEach((prop) => {
    const value = obj[prop];
    if (
      value &&
      (typeof value === 'object' || typeof value === 'function') &&
      !Object.isFrozen(value)
    ) {
      deepFreeze(value);
    }
  });
  return obj;
};

// Final R1 architecture freeze: every report is an asset governed by one contract.
export const REPORT_ASSET_BASELINE = Object.freeze({
  engine: 'REPORT_ENGINE_V1',
  identityTemplate: 'WAAD_GLOBAL_PDF_PRINT_V1',
  excelProfile: 'WAAD_REPORT_EXCEL_V1',
  identitySource: 'SYSTEM_SETTINGS',
  identityFields: Object.freeze([
    'institutionName',
    'logo',
    'address',
    'phone',
    'email',
    'website'
  ])
});

export const REPORT_REGISTRY_GOVERNANCE = Object.freeze({
  reportCodeImmutable: true,
  reportCodeRouteIndependent: true,
  singleSourceOfTruth: true,
  executionChain: Object.freeze([
    'registry',
    'menu',
    'routing',
    'permissions',
    'engine',
    'print',
    'exports'
  ])
});

export const REQUIRED_REPORT_FIELDS = Object.freeze([
  'code',
  'owner',
  'version',
  'description',
  'domain',
  'dataSource',
  'resource',
  'supportsScheduling',
  'engine',
  'identityTemplate',
  'excelProfile',
  'identitySource'
]);

const buildReportAsset = (report) =>
  deepFreeze({
    ...REPORT_ASSET_BASELINE,
    surfaces: ['report-center'],
    ...report,
    capabilities: deepFreeze(mergeCapabilities(report.capabilities))
  });

// R1 baseline registry. R2 will add remaining reports gradually (MVR-first).
export const REPORT_REGISTRY = Object.freeze([
  {
    code: 'REP-CLM-001',
    titleAr: 'المطالبات اليومية',
    titleEn: 'Daily Claims',
    description: 'تقرير تشغيلي لمتابعة المطالبات اليومية.',
    owner: 'Claims Team',
    version: '1.0.0',
    domain: 'claims',
    dataSource: 'Claims',
    classification: 'operational',
    route: '/reports/claims',
    surfaces: ['report-center'],
    resource: 'report_domain_claims',
    supportsScheduling: true,
    status: 'active'
  },
  {
    code: 'REP-CLM-003',
    titleAr: 'معاينة كشف المطالبات',
    titleEn: 'Claim Statement Preview',
    description: 'مدخل المعاينة والطباعة لكشف المطالبات ضمن سياق المطالبات.',
    owner: 'Claims Team',
    version: '1.0.0',
    domain: 'claims',
    dataSource: 'Claims',
    classification: 'operational',
    route: '/reports/claims',
    surfaces: ['report-center'],
    resource: 'report_domain_claims',
    supportsScheduling: true,
    status: 'active'
  },
  {
    code: 'REP-CLM-004',
    titleAr: 'تقرير مطالبات مقدم الخدمة',
    titleEn: 'Provider Claims Report',
    description: 'تقرير مطالبات داخل بوابة مقدم الخدمة دون نقله إلى مركز التقارير الإداري.',
    owner: 'Provider Portal Team',
    version: '1.0.0',
    domain: 'claims',
    dataSource: 'Claims',
    classification: 'operational',
    route: '/provider/reports/claims',
    surfaces: ['provider-portal'],
    resource: 'provider_portal',
    supportsScheduling: true,
    status: 'active'
  },
  {
    code: 'REP-CLM-002',
    titleAr: 'المطالبات المرفوضة',
    titleEn: 'Rejected Claims',
    description: 'تقرير تحليلي لأسباب رفض المطالبات.',
    owner: 'Claims Team',
    version: '1.0.0',
    domain: 'claims',
    dataSource: 'Claims',
    classification: 'analytical',
    route: null,
    resource: 'report_domain_claims',
    supportsScheduling: true,
    status: 'planned'
  },
  {
    code: 'REP-FIN-001',
    titleAr: 'التسويات الشهرية',
    titleEn: 'Monthly Settlements',
    description: 'ملخص التسويات الشهرية لمقدمي الخدمة.',
    owner: 'Finance Team',
    version: '1.0.0',
    domain: 'financial-settlements',
    dataSource: 'Settlements',
    classification: 'operational',
    route: '/reports/provider-settlement-summary',
    resource: 'report_domain_financial_settlements',
    supportsScheduling: true,
    status: 'active'
  },
  {
    code: 'REP-FIN-002',
    titleAr: 'الخلاصة المالية المجمعة',
    titleEn: 'Financial Consolidation',
    description: 'تقرير تجميعي مالي على مستوى الجهات والتعاقدات.',
    owner: 'Finance Team',
    version: '1.0.0',
    domain: 'financial-settlements',
    dataSource: 'Settlements',
    classification: 'analytical',
    route: '/reports/financial-consolidation',
    resource: 'report_domain_financial_settlements',
    supportsScheduling: true,
    status: 'active'
  },
  {
    code: 'REP-FIN-003',
    titleAr: 'تقرير أرباح الخصومات',
    titleEn: 'Discount Profit Report',
    description: 'تحليل أرباح الخصومات للشركة حسب الفترة.',
    owner: 'Finance Team',
    version: '1.0.0',
    domain: 'financial-settlements',
    dataSource: 'Settlements',
    classification: 'analytical',
    route: '/reports/accountant-profit',
    resource: 'report_domain_financial_settlements',
    supportsScheduling: true,
    status: 'active'
  },
  {
    code: 'REP-PRL-001',
    titleAr: 'آخر قائمة أسعار منشورة',
    titleEn: 'Latest Published Price List',
    description: 'يعرض آخر نسخة منشورة من قوائم الأسعار.',
    owner: 'Pricing Team',
    version: '1.0.0',
    domain: 'price-lists',
    dataSource: 'Provider Price Lists',
    classification: 'operational',
    route: null,
    resource: 'report_domain_price_lists',
    supportsScheduling: true,
    status: 'planned'
  },
  {
    code: 'REP-PRL-002',
    titleAr: 'تقرير أثر تغيير الأسعار',
    titleEn: 'Price Change Impact',
    description: 'قياس أثر الزيادات والانخفاضات والتغييرات على الخدمات.',
    owner: 'Pricing Team',
    version: '1.0.0',
    domain: 'price-lists',
    dataSource: 'Provider Price Lists',
    classification: 'analytical',
    route: null,
    resource: 'report_domain_price_lists',
    supportsScheduling: true,
    status: 'planned'
  },
  {
    code: 'REP-BEN-001',
    titleAr: 'تقرير الخدمات غير المغطاة',
    titleEn: 'Uncovered Requested Services',
    description: 'أكثر الخدمات المطلوبة غير المشمولة في وثائق المنافع.',
    owner: 'Benefit Team',
    version: '1.0.0',
    domain: 'benefit-policies',
    dataSource: 'Benefit Policies',
    classification: 'analytical',
    route: null,
    resource: 'report_domain_benefit_policies',
    supportsScheduling: true,
    status: 'planned'
  }
].map(buildReportAsset));

export const getRegistryContractIssues = () =>
  REPORT_REGISTRY.flatMap((report) =>
    REQUIRED_REPORT_FIELDS.filter((field) => !report[field]).map((field) => ({
      code: report.code || 'UNKNOWN',
      field
    }))
  );

export const getRegistryDuplicateCodeIssues = () => {
  const counts = REPORT_REGISTRY.reduce((acc, report) => {
    acc[report.code] = (acc[report.code] || 0) + 1;
    return acc;
  }, {});

  return Object.entries(counts)
    .filter(([, count]) => count > 1)
    .map(([code, count]) => ({ code, count }));
};
