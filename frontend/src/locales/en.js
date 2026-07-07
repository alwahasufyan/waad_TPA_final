/**
 * English Translations - WAAD System
 * Phase D1.5 - English Fallback
 *
 * Note: Arabic is the default language. English is for technical/admin reference.
 */

const en = {
  // ==================== NAVIGATION ====================
  nav: {
    dashboard: 'Dashboard',
    members: 'Insured Members',
    employers: 'Contracted Employers',
    providers: 'Healthcare Providers',
    claims: 'Claims',
    visits: 'Medical Visits',
    preApprovals: 'Pre-Approvals',
    medicalCategories: 'Medical Categories',
    medicalServices: 'Medical Services',
    medicalPackages: 'Medical Packages',
    benefitPackages: 'Benefit Packages',
    providerContracts: 'Provider Contracts',
    policies: 'Insurance Policies',
    settings: 'System Settings',
    audit: 'Audit Log',
    rbac: 'Permissions',
    profile: 'Profile',
    logout: 'Logout'
  },

  // ==================== MENU GROUPS ====================
  groups: {
    main: 'Main',
    dataManagement: 'Data Management',
    claimsManagement: 'Claims Management',
    medicalManagement: 'Medical Management',
    contractsDocuments: 'Contracts & Documents',
    settings: 'Settings'
  },

  // ==================== COMMON ACTIONS ====================
  actions: {
    add: 'Add',
    edit: 'Edit',
    delete: 'Delete',
    view: 'View',
    save: 'Save',
    cancel: 'Cancel',
    search: 'Search',
    filter: 'Filter',
    export: 'Export',
    import: 'Import',
    refresh: 'Refresh',
    back: 'Back',
    next: 'Next',
    previous: 'Previous',
    submit: 'Submit',
    approve: 'Approve',
    reject: 'Reject',
    pending: 'Pending'
  },

  // ==================== COMMON LABELS ====================
  common: {
    name: 'Name',
    name: 'Arabic Name',
    name: 'English Name',
    code: 'Code',
    status: 'Status',
    active: 'Active',
    inactive: 'Inactive',
    date: 'Date',
    startDate: 'Start Date',
    endDate: 'End Date',
    createdAt: 'Created At',
    updatedAt: 'Updated At',
    description: 'Description',
    notes: 'Notes',
    amount: 'Amount',
    total: 'Total',
    phone: 'Phone',
    email: 'Email',
    address: 'Address',
    type: 'Type',
    category: 'Category',
    details: 'Details',
    attachments: 'Attachments',
    noData: 'No data available',
    loading: 'Loading...',
    error: 'An error occurred',
    success: 'Operation successful',
    confirm: 'Confirm',
    warning: 'Warning',
    required: 'Required',
    optional: 'Optional',
    all: 'All',
    select: 'Select',
    none: 'None'
  },

  // ==================== MEMBERS MODULE ====================
  members: {
    title: 'Insured Members',
    singular: 'Insured Member',
    list: 'Insured Members List',
    add: 'Add Insured Member',
    edit: 'Edit Insured Member',
    view: 'View Insured Member',
    nationalNumber: 'National Number',
    cardNumber: 'Card Number',
    fullName: 'Full Name',
    birthDate: 'Birth Date',
    gender: 'Gender',
    male: 'Male',
    female: 'Female',
    nationality: 'Nationality',
    maritalStatus: 'Marital Status',
    employer: 'Contracted Employer',
    benefitPackage: 'Benefit Package',
    policyNumber: 'Policy Number',
    membershipPeriod: 'Membership Period',
    familyMembers: 'Family Members',
    principalMember: 'Principal Member',
    dependent: 'Dependent'
  },

  // ==================== EMPLOYERS MODULE ====================
  employers: {
    title: 'Contracted Employers',
    singular: 'Contracted Employer',
    list: 'Contracted Employers List',
    add: 'Add Contracted Employer',
    edit: 'Edit Contracted Employer',
    view: 'View Contracted Employer',
    contactPerson: 'Contact Person',
    contractNumber: 'Contract Number',
    membersCount: 'Insured Members Count'
  },

  // ==================== PROVIDERS MODULE ====================
  providers: {
    title: 'Healthcare Providers',
    singular: 'Healthcare Provider',
    list: 'Healthcare Providers List',
    add: 'Add Healthcare Provider',
    edit: 'Edit Healthcare Provider',
    view: 'View Healthcare Provider',
    providerType: 'Provider Type',
    hospital: 'Hospital',
    clinic: 'Clinic',
    pharmacy: 'Pharmacy',
    laboratory: 'Laboratory',
    specialty: 'Specialty',
    license: 'License Number'
  },

  // ==================== CLAIMS MODULE ====================
  claims: {
    title: 'Claims',
    singular: 'Claim',
    list: 'Claims List',
    add: 'Add Claim',
    edit: 'Edit Claim',
    view: 'View Claim',
    claimNumber: 'Claim Number',
    claimDate: 'Claim Date',
    claimAmount: 'Claim Amount',
    approvedAmount: 'Approved Amount',
    status: {
      pending: 'Pending Review',
      approved: 'Approved',
      rejected: 'Rejected',
      paid: 'Paid',
      partiallyPaid: 'Partially Paid'
    }
  },

  // ==================== VISITS MODULE ====================
  visits: {
    title: 'Medical Visits',
    singular: 'Medical Visit',
    list: 'Medical Visits List',
    add: 'Add Medical Visit',
    edit: 'Edit Medical Visit',
    view: 'View Medical Visit',
    visitDate: 'Visit Date',
    visitType: 'Visit Type',
    diagnosis: 'Diagnosis',
    treatment: 'Treatment',
    doctor: 'Treating Doctor'
  },

  // ==================== PRE-APPROVALS MODULE ====================
  preApprovals: {
    title: 'Pre-Approvals',
    singular: 'Pre-Approval',
    list: 'Pre-Approvals List',
    add: 'Request Pre-Approval',
    edit: 'Edit Pre-Approval Request',
    view: 'View Pre-Approval Request',
    requestNumber: 'Request Number',
    requestDate: 'Request Date',
    procedure: 'Requested Procedure',
    estimatedCost: 'Estimated Cost',
    status: {
      pending: 'Pending Review',
      approved: 'Approved',
      rejected: 'Rejected',
      expired: 'Expired'
    }
  },

  // ==================== MEDICAL CATEGORIES ====================
  medicalCategories: {
    title: 'Medical Categories',
    singular: 'Medical Category',
    list: 'Medical Categories List',
    add: 'Add Medical Category',
    edit: 'Edit Medical Category',
    view: 'View Medical Category'
  },

  // ==================== MEDICAL SERVICES ====================
  medicalServices: {
    title: 'Medical Services',
    singular: 'Medical Service',
    list: 'Medical Services List',
    add: 'Add Medical Service',
    edit: 'Edit Medical Service',
    view: 'View Medical Service',
    serviceCode: 'Service Code',
    servicePrice: 'Service Price',
    coveragePercentage: 'Coverage Percentage'
  },

  // ==================== MEDICAL PACKAGES ====================
  medicalPackages: {
    title: 'Medical Packages',
    singular: 'Medical Package',
    list: 'Medical Packages List',
    add: 'Add Medical Package',
    edit: 'Edit Medical Package',
    view: 'View Medical Package',
    includedServices: 'Included Services'
  },

  // ==================== BENEFIT PACKAGES ====================
  benefitPackages: {
    title: 'Benefit Packages',
    singular: 'Benefit Package',
    list: 'Benefit Packages List',
    add: 'Add Benefit Package',
    edit: 'Edit Benefit Package',
    view: 'View Benefit Package',
    coverageLimit: 'Coverage Limit',
    annualLimit: 'Annual Limit',
    deductible: 'Deductible'
  },

  // ==================== POLICIES ====================
  policies: {
    title: 'Insurance Policies',
    singular: 'Insurance Policy',
    list: 'Insurance Policies List',
    add: 'Add Insurance Policy',
    edit: 'Edit Insurance Policy',
    view: 'View Insurance Policy',
    policyNumber: 'Policy Number',
    effectiveDate: 'Effective Date',
    expiryDate: 'Expiry Date'
  },

  // ==================== SETTINGS ====================
  settings: {
    title: 'System Settings',
    general: 'General Settings',
    theme: 'Theme',
    language: 'Language',
    notifications: 'Notifications',
    security: 'Security'
  },

  // ==================== AUDIT ====================
  audit: {
    title: 'Audit Log',
    action: 'Action',
    user: 'User',
    timestamp: 'Timestamp',
    details: 'Details',
    ipAddress: 'IP Address'
  },

  // ==================== INSURANCE COMPANY ====================
  insuranceCompany: {
    fixed: 'Insurance Company: Al-Waha Insurance',
    name: 'Al-Waha Insurance',
    locked: 'Insurance company is fixed in the system'
  },

  // ==================== AUTH ====================
  auth: {
    login: 'Login',
    logout: 'Logout',
    username: 'Username',
    password: 'Password',
    rememberMe: 'Remember Me',
    forgotPassword: 'Forgot Password?',
    profile: 'Profile',
    changePassword: 'Change Password'
  },

  // ==================== SYSTEM ====================
  system: {
    name: 'WAAD System',
    subtitle: 'Insurance Claims Management',
    copyright: '© {year} WAAD System - All Rights Reserved',
    version: 'Version'
  }
};

export default en;
