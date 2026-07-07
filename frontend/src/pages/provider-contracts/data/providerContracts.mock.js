/**
 * Provider Contracts Mock Data
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * This file contains mock data for the Provider Contracts module.
 * Data structure is derived from typical TPA contract Excel files:
 *
 * Excel Mapping:
 * - Sheet 1: Contract List (provider, code, dates, status)
 * - Sheet 2: Pricing Details (service code, name, category, price)
 *
 * Backend Integration Notes:
 * - When backend is ready, replace these exports with API service calls
 * - Data structure matches expected backend DTO format
 * - Pagination/filtering will be handled by backend
 *
 * @version 1.0.0
 * @lastUpdated 2024-12-24
 */

// ═══════════════════════════════════════════════════════════════════════════
// CONTRACT STATUS ENUM
// ═══════════════════════════════════════════════════════════════════════════

export const CONTRACT_STATUS = {
  DRAFT: 'DRAFT',
  ACTIVE: 'ACTIVE',
  EXPIRED: 'EXPIRED',
  SUSPENDED: 'SUSPENDED',
  TERMINATED: 'TERMINATED'
};

export const CONTRACT_STATUS_CONFIG = {
  DRAFT: {
    label: 'مسودة',
    labelEn: 'Draft',
    color: 'default',
    description: 'العقد قيد الإعداد'
  },
  ACTIVE: {
    label: 'نشط',
    labelEn: 'Active',
    color: 'success',
    description: 'العقد ساري المفعول'
  },
  EXPIRED: {
    label: 'منتهي',
    labelEn: 'Expired',
    color: 'error',
    description: 'انتهت صلاحية العقد'
  },
  SUSPENDED: {
    label: 'موقوف',
    labelEn: 'Suspended',
    color: 'warning',
    description: 'العقد موقوف مؤقتاً'
  },
  TERMINATED: {
    label: 'ملغي',
    labelEn: 'Terminated',
    color: 'error',
    description: 'تم إنهاء العقد'
  }
};

// ═══════════════════════════════════════════════════════════════════════════
// PRICING MODEL TYPES
// ═══════════════════════════════════════════════════════════════════════════

export const PRICING_MODEL = {
  FIXED: 'FIXED', // سعر ثابت
  DISCOUNT: 'DISCOUNT', // نسبة خصم من السعر الرسمي
  TIERED: 'TIERED', // تسعير متدرج حسب الكمية
  NEGOTIATED: 'NEGOTIATED' // سعر تفاوضي خاص
};

export const PRICING_MODEL_CONFIG = {
  FIXED: { label: 'سعر ثابت', labelEn: 'Fixed Price' },
  DISCOUNT: { label: 'نسبة خصم', labelEn: 'Discount Rate' },
  TIERED: { label: 'تسعير متدرج', labelEn: 'Tiered Pricing' },
  NEGOTIATED: { label: 'سعر تفاوضي', labelEn: 'Negotiated' }
};

// ═══════════════════════════════════════════════════════════════════════════
// SERVICE CATEGORIES (for pricing items)
// ═══════════════════════════════════════════════════════════════════════════

export const SERVICE_CATEGORIES = [
  { id: 1, code: 'CONS', name: 'استشارات طبية' },
  { id: 2, code: 'LAB', name: 'تحاليل مخبرية' },
  { id: 3, code: 'RAD', name: 'أشعة تشخيصية' },
  { id: 4, code: 'SURG', name: 'عمليات جراحية' },
  { id: 5, code: 'PHARM', name: 'أدوية' },
  { id: 6, code: 'DENT', name: 'علاج أسنان' },
  { id: 7, code: 'OPT', name: 'بصريات' },
  { id: 8, code: 'PHYS', name: 'علاج طبيعي' },
  { id: 9, code: 'EMER', name: 'طوارئ' },
  { id: 10, code: 'INP', name: 'إقامة' }
];

// ═══════════════════════════════════════════════════════════════════════════
// MOCK PROVIDERS (simplified from providers module)
// ═══════════════════════════════════════════════════════════════════════════

const MOCK_PROVIDERS = [
  { id: 1, code: 'PRV-001', name: 'مستشفى طرابلس المركزي', type: 'HOSPITAL', city: 'طرابلس' },
  { id: 2, code: 'PRV-002', name: 'مركز بنغازي الطبي', type: 'HOSPITAL', city: 'بنغازي' },
  { id: 3, code: 'PRV-003', name: 'عيادة السلامة', type: 'CLINIC', city: 'طرابلس' },
  { id: 4, code: 'PRV-004', name: 'مختبرات الحياة', type: 'LABORATORY', city: 'طرابلس' },
  { id: 5, code: 'PRV-005', name: 'صيدلية الشفاء', type: 'PHARMACY', city: 'مصراتة' },
  { id: 6, code: 'PRV-006', name: 'مركز النور للأشعة', type: 'DIAGNOSTIC_CENTER', city: 'طرابلس' },
  { id: 7, code: 'PRV-007', name: 'مستشفى الأمل الخاص', type: 'HOSPITAL', city: 'سبها' },
  { id: 8, code: 'PRV-008', name: 'عيادة أسنان الابتسامة', type: 'DENTAL_CLINIC', city: 'طرابلس' }
];

// ═══════════════════════════════════════════════════════════════════════════
// MOCK PRICING ITEMS (per contract)
// ═══════════════════════════════════════════════════════════════════════════

const generatePricingItems = (contractId, providerType) => {
  const basePricing = [
    // Consultations
    {
      serviceCode: 'CONS-001',
      serviceNameAr: 'استشارة طبيب عام',
      serviceNameEn: 'GP Consultation',
      categoryId: 1,
      basePrice: 50,
      contractPrice: 40,
      unit: 'زيارة'
    },
    {
      serviceCode: 'CONS-002',
      serviceNameAr: 'استشارة أخصائي',
      serviceNameEn: 'Specialist Consultation',
      categoryId: 1,
      basePrice: 100,
      contractPrice: 80,
      unit: 'زيارة'
    },
    {
      serviceCode: 'CONS-003',
      serviceNameAr: 'استشارة استشاري',
      serviceNameEn: 'Consultant Visit',
      categoryId: 1,
      basePrice: 150,
      contractPrice: 120,
      unit: 'زيارة'
    },

    // Laboratory
    {
      serviceCode: 'LAB-001',
      serviceNameAr: 'فحص دم شامل CBC',
      serviceNameEn: 'Complete Blood Count',
      categoryId: 2,
      basePrice: 25,
      contractPrice: 20,
      unit: 'فحص'
    },
    {
      serviceCode: 'LAB-002',
      serviceNameAr: 'فحص وظائف الكبد',
      serviceNameEn: 'Liver Function Test',
      categoryId: 2,
      basePrice: 45,
      contractPrice: 35,
      unit: 'فحص'
    },
    {
      serviceCode: 'LAB-003',
      serviceNameAr: 'فحص وظائف الكلى',
      serviceNameEn: 'Kidney Function Test',
      categoryId: 2,
      basePrice: 40,
      contractPrice: 32,
      unit: 'فحص'
    },
    {
      serviceCode: 'LAB-004',
      serviceNameAr: 'فحص السكر التراكمي HbA1c',
      serviceNameEn: 'HbA1c Test',
      categoryId: 2,
      basePrice: 35,
      contractPrice: 28,
      unit: 'فحص'
    },
    {
      serviceCode: 'LAB-005',
      serviceNameAr: 'تحليل بول كامل',
      serviceNameEn: 'Complete Urine Analysis',
      categoryId: 2,
      basePrice: 15,
      contractPrice: 12,
      unit: 'فحص'
    },

    // Radiology
    {
      serviceCode: 'RAD-001',
      serviceNameAr: 'أشعة سينية صدر',
      serviceNameEn: 'Chest X-Ray',
      categoryId: 3,
      basePrice: 60,
      contractPrice: 48,
      unit: 'صورة'
    },
    {
      serviceCode: 'RAD-002',
      serviceNameAr: 'تصوير مقطعي CT',
      serviceNameEn: 'CT Scan',
      categoryId: 3,
      basePrice: 350,
      contractPrice: 280,
      unit: 'صورة'
    },
    {
      serviceCode: 'RAD-003',
      serviceNameAr: 'رنين مغناطيسي MRI',
      serviceNameEn: 'MRI Scan',
      categoryId: 3,
      basePrice: 500,
      contractPrice: 400,
      unit: 'صورة'
    },
    {
      serviceCode: 'RAD-004',
      serviceNameAr: 'سونار بطن',
      serviceNameEn: 'Abdominal Ultrasound',
      categoryId: 3,
      basePrice: 80,
      contractPrice: 65,
      unit: 'صورة'
    },

    // Surgery
    {
      serviceCode: 'SURG-001',
      serviceNameAr: 'عملية زائدة دودية',
      serviceNameEn: 'Appendectomy',
      categoryId: 4,
      basePrice: 3000,
      contractPrice: 2400,
      unit: 'عملية'
    },
    {
      serviceCode: 'SURG-002',
      serviceNameAr: 'عملية مرارة بالمنظار',
      serviceNameEn: 'Laparoscopic Cholecystectomy',
      categoryId: 4,
      basePrice: 4500,
      contractPrice: 3600,
      unit: 'عملية'
    },
    {
      serviceCode: 'SURG-003',
      serviceNameAr: 'عملية فتق إربي',
      serviceNameEn: 'Inguinal Hernia Repair',
      categoryId: 4,
      basePrice: 2500,
      contractPrice: 2000,
      unit: 'عملية'
    },

    // Emergency
    {
      serviceCode: 'EMER-001',
      serviceNameAr: 'رسوم طوارئ',
      serviceNameEn: 'Emergency Room Fee',
      categoryId: 9,
      basePrice: 100,
      contractPrice: 80,
      unit: 'زيارة'
    },
    {
      serviceCode: 'EMER-002',
      serviceNameAr: 'غرز جراحية',
      serviceNameEn: 'Surgical Stitches',
      categoryId: 9,
      basePrice: 150,
      contractPrice: 120,
      unit: 'إجراء'
    },

    // Dental
    {
      serviceCode: 'DENT-001',
      serviceNameAr: 'فحص أسنان',
      serviceNameEn: 'Dental Checkup',
      categoryId: 6,
      basePrice: 30,
      contractPrice: 25,
      unit: 'زيارة'
    },
    {
      serviceCode: 'DENT-002',
      serviceNameAr: 'تنظيف أسنان',
      serviceNameEn: 'Dental Cleaning',
      categoryId: 6,
      basePrice: 50,
      contractPrice: 40,
      unit: 'جلسة'
    },
    {
      serviceCode: 'DENT-003',
      serviceNameAr: 'حشوة عادية',
      serviceNameEn: 'Regular Filling',
      categoryId: 6,
      basePrice: 60,
      contractPrice: 48,
      unit: 'سن'
    },
    {
      serviceCode: 'DENT-004',
      serviceNameAr: 'خلع سن',
      serviceNameEn: 'Tooth Extraction',
      categoryId: 6,
      basePrice: 80,
      contractPrice: 65,
      unit: 'سن'
    },

    // Inpatient
    {
      serviceCode: 'INP-001',
      serviceNameAr: 'غرفة عادية',
      serviceNameEn: 'Standard Room',
      categoryId: 10,
      basePrice: 200,
      contractPrice: 160,
      unit: 'ليلة'
    },
    {
      serviceCode: 'INP-002',
      serviceNameAr: 'غرفة خاصة',
      serviceNameEn: 'Private Room',
      categoryId: 10,
      basePrice: 400,
      contractPrice: 320,
      unit: 'ليلة'
    },
    {
      serviceCode: 'INP-003',
      serviceNameAr: 'عناية مركزة ICU',
      serviceNameEn: 'ICU Room',
      categoryId: 10,
      basePrice: 800,
      contractPrice: 640,
      unit: 'ليلة'
    }
  ];

  // Add unique IDs and contract reference
  return basePricing.map((item, index) => ({
    id: contractId * 100 + index + 1,
    contractId,
    ...item,
    discountPercent: Math.round(((item.basePrice - item.contractPrice) / item.basePrice) * 100),
    currency: 'LYD',
    effectiveFrom: '2024-01-01',
    effectiveTo: '2024-12-31',
    notes: null,
    isActive: true
  }));
};

// ═══════════════════════════════════════════════════════════════════════════
// MOCK CONTRACTS DATA
// ═══════════════════════════════════════════════════════════════════════════

export const MOCK_CONTRACTS = [
  {
    id: 1,
    contractCode: 'CON-2024-001',
    provider: MOCK_PROVIDERS[0],
    providerId: 1,
    status: CONTRACT_STATUS.ACTIVE,
    pricingModel: PRICING_MODEL.DISCOUNT,
    discountPercent: 20,
    startDate: '2024-01-01',
    endDate: '2024-12-31',
    signedDate: '2023-12-15',
    totalValue: 500000,
    currency: 'LYD',
    paymentTerms: 'صافي 30 يوم',
    notes: 'عقد شامل لجميع الخدمات الطبية',
    contactPerson: 'د. أحمد محمد',
    contactPhone: '+218 91 1234567',
    contactEmail: 'ahmed@tripoli-hospital.ly',
    createdAt: '2023-12-10T10:00:00Z',
    updatedAt: '2024-01-01T08:00:00Z',
    createdBy: 'admin',
    pricingItemsCount: 25
  },
  {
    id: 2,
    contractCode: 'CON-2024-002',
    provider: MOCK_PROVIDERS[1],
    providerId: 2,
    status: CONTRACT_STATUS.ACTIVE,
    pricingModel: PRICING_MODEL.FIXED,
    discountPercent: 15,
    startDate: '2024-02-01',
    endDate: '2025-01-31',
    signedDate: '2024-01-20',
    totalValue: 750000,
    currency: 'LYD',
    paymentTerms: 'صافي 45 يوم',
    notes: 'عقد رئيسي للمنطقة الشرقية',
    contactPerson: 'د. فاطمة علي',
    contactPhone: '+218 91 7654321',
    contactEmail: 'fatima@benghazi-medical.ly',
    createdAt: '2024-01-15T09:00:00Z',
    updatedAt: '2024-02-01T07:00:00Z',
    createdBy: 'admin',
    pricingItemsCount: 25
  },
  {
    id: 3,
    contractCode: 'CON-2024-003',
    provider: MOCK_PROVIDERS[2],
    providerId: 3,
    status: CONTRACT_STATUS.DRAFT,
    pricingModel: PRICING_MODEL.NEGOTIATED,
    discountPercent: 10,
    startDate: '2024-04-01',
    endDate: '2025-03-31',
    signedDate: null,
    totalValue: 150000,
    currency: 'LYD',
    paymentTerms: 'صافي 30 يوم',
    notes: 'عقد جديد - قيد المراجعة',
    contactPerson: 'د. محمود سالم',
    contactPhone: '+218 92 1111222',
    contactEmail: 'mahmoud@salama-clinic.ly',
    createdAt: '2024-03-01T14:00:00Z',
    updatedAt: '2024-03-10T11:00:00Z',
    createdBy: 'admin',
    pricingItemsCount: 18
  },
  {
    id: 4,
    contractCode: 'CON-2023-015',
    provider: MOCK_PROVIDERS[3],
    providerId: 4,
    status: CONTRACT_STATUS.EXPIRED,
    pricingModel: PRICING_MODEL.DISCOUNT,
    discountPercent: 25,
    startDate: '2023-01-01',
    endDate: '2023-12-31',
    signedDate: '2022-12-20',
    totalValue: 200000,
    currency: 'LYD',
    paymentTerms: 'صافي 30 يوم',
    notes: 'عقد منتهي - بانتظار التجديد',
    contactPerson: 'أ. سارة يوسف',
    contactPhone: '+218 91 3333444',
    contactEmail: 'sara@hayat-labs.ly',
    createdAt: '2022-12-15T10:00:00Z',
    updatedAt: '2024-01-05T09:00:00Z',
    createdBy: 'admin',
    pricingItemsCount: 12
  },
  {
    id: 5,
    contractCode: 'CON-2024-004',
    provider: MOCK_PROVIDERS[4],
    providerId: 5,
    status: CONTRACT_STATUS.ACTIVE,
    pricingModel: PRICING_MODEL.TIERED,
    discountPercent: 18,
    startDate: '2024-01-15',
    endDate: '2025-01-14',
    signedDate: '2024-01-10',
    totalValue: 300000,
    currency: 'LYD',
    paymentTerms: 'صافي 15 يوم',
    notes: 'عقد صيدلية - تسعير حسب الكمية',
    contactPerson: 'صيدلي. خالد عمر',
    contactPhone: '+218 91 5555666',
    contactEmail: 'khaled@shifaa-pharmacy.ly',
    createdAt: '2024-01-08T11:00:00Z',
    updatedAt: '2024-01-15T08:00:00Z',
    createdBy: 'admin',
    pricingItemsCount: 150
  },
  {
    id: 6,
    contractCode: 'CON-2024-005',
    provider: MOCK_PROVIDERS[5],
    providerId: 6,
    status: CONTRACT_STATUS.ACTIVE,
    pricingModel: PRICING_MODEL.FIXED,
    discountPercent: 22,
    startDate: '2024-03-01',
    endDate: '2025-02-28',
    signedDate: '2024-02-25',
    totalValue: 180000,
    currency: 'LYD',
    paymentTerms: 'صافي 30 يوم',
    notes: 'عقد خدمات الأشعة والتصوير',
    contactPerson: 'د. ليلى أحمد',
    contactPhone: '+218 91 7777888',
    contactEmail: 'layla@noor-radiology.ly',
    createdAt: '2024-02-20T09:00:00Z',
    updatedAt: '2024-03-01T07:00:00Z',
    createdBy: 'admin',
    pricingItemsCount: 20
  },
  {
    id: 7,
    contractCode: 'CON-2024-006',
    provider: MOCK_PROVIDERS[6],
    providerId: 7,
    status: CONTRACT_STATUS.SUSPENDED,
    pricingModel: PRICING_MODEL.DISCOUNT,
    discountPercent: 12,
    startDate: '2024-01-01',
    endDate: '2024-12-31',
    signedDate: '2023-12-28',
    totalValue: 400000,
    currency: 'LYD',
    paymentTerms: 'صافي 60 يوم',
    notes: 'موقوف مؤقتاً - بانتظار تسوية مالية',
    contactPerson: 'د. عبدالله حسن',
    contactPhone: '+218 91 9999000',
    contactEmail: 'abdullah@amal-hospital.ly',
    createdAt: '2023-12-25T10:00:00Z',
    updatedAt: '2024-02-15T14:00:00Z',
    createdBy: 'admin',
    pricingItemsCount: 25
  },
  {
    id: 8,
    contractCode: 'CON-2024-007',
    provider: MOCK_PROVIDERS[7],
    providerId: 8,
    status: CONTRACT_STATUS.ACTIVE,
    pricingModel: PRICING_MODEL.FIXED,
    discountPercent: 20,
    startDate: '2024-02-15',
    endDate: '2025-02-14',
    signedDate: '2024-02-10',
    totalValue: 80000,
    currency: 'LYD',
    paymentTerms: 'صافي 30 يوم',
    notes: 'عقد خدمات الأسنان',
    contactPerson: 'د. نورا الطاهر',
    contactPhone: '+218 91 1234000',
    contactEmail: 'noura@smile-dental.ly',
    createdAt: '2024-02-05T11:00:00Z',
    updatedAt: '2024-02-15T08:00:00Z',
    createdBy: 'admin',
    pricingItemsCount: 15
  }
];

// ═══════════════════════════════════════════════════════════════════════════
// MOCK DATA ACCESS FUNCTIONS
// These will be replaced with API calls when backend is ready
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Get all contracts (simulates paginated API response)
 * @param {Object} params - Query parameters
 * @returns {Object} Paginated response
 */
export const getMockContracts = (params = {}) => {
  const { page = 0, size = 20, status, search } = params;

  let filtered = [...MOCK_CONTRACTS];

  // Filter by status
  if (status && status !== 'ALL') {
    filtered = filtered.filter((c) => c.status === status);
  }

  // Search filter
  if (search) {
    const searchLower = search.toLowerCase();
    filtered = filtered.filter(
      (c) =>
        c.contractCode.toLowerCase().includes(searchLower) ||
        c.provider.name.includes(search) ||
        c.provider.name.toLowerCase().includes(searchLower)
    );
  }

  // Paginate
  const start = page * size;
  const end = start + size;
  const content = filtered.slice(start, end);

  return {
    content,
    totalElements: filtered.length,
    totalPages: Math.ceil(filtered.length / size),
    number: page,
    size,
    first: page === 0,
    last: end >= filtered.length
  };
};

/**
 * Get single contract by ID
 * @param {number} id - Contract ID
 * @returns {Object|null} Contract details with pricing items
 */
export const getMockContractById = (id) => {
  const contract = MOCK_CONTRACTS.find((c) => c.id === Number(id));
  if (!contract) return null;

  return {
    ...contract,
    pricingItems: generatePricingItems(contract.id, contract.provider.type)
  };
};

/**
 * Get pricing items for a contract
 * @param {number} contractId - Contract ID
 * @returns {Array} List of pricing items
 */
export const getMockPricingItems = (contractId) => {
  return generatePricingItems(Number(contractId), 'HOSPITAL');
};

/**
 * Get contract statistics
 * @returns {Object} Summary statistics
 */
export const getMockContractStats = () => {
  const contracts = MOCK_CONTRACTS;
  return {
    total: contracts.length,
    active: contracts.filter((c) => c.status === CONTRACT_STATUS.ACTIVE).length,
    draft: contracts.filter((c) => c.status === CONTRACT_STATUS.DRAFT).length,
    expired: contracts.filter((c) => c.status === CONTRACT_STATUS.EXPIRED).length,
    suspended: contracts.filter((c) => c.status === CONTRACT_STATUS.SUSPENDED).length,
    totalValue: contracts.reduce((sum, c) => sum + c.totalValue, 0)
  };
};

// Default export for convenience
export default {
  MOCK_CONTRACTS,
  CONTRACT_STATUS,
  CONTRACT_STATUS_CONFIG,
  PRICING_MODEL,
  PRICING_MODEL_CONFIG,
  SERVICE_CATEGORIES,
  getMockContracts,
  getMockContractById,
  getMockPricingItems,
  getMockContractStats
};
