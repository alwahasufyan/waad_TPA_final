// material-ui icons
import {
  Dashboard as DashboardIcon,
  Business as BusinessIcon,
  LocalHospital as LocalHospitalIcon,
  Receipt as ReceiptIcon,
  Description as DescriptionIcon,
  PeopleAlt as PeopleAltIcon,
  Category as CategoryIcon,
  Assignment as AssignmentIcon,
  Settings as SettingsIcon,
  Assessment as AssessmentIcon,
  Inbox as InboxIcon,
  Payment as PaymentIcon,
  Policy as PolicyIcon,
  Handshake as HandshakeIcon,
  Security as SecurityIcon,
  HowToReg as HowToRegIcon,
  FormatListBulleted as FormatListBulletedIcon,
  Folder as FolderIcon,
  VerifiedUser as VerifiedUserIcon,
  History as HistoryIcon,
  AccountBalanceWallet as AccountBalanceWalletIcon
} from '@mui/icons-material';

// ═══════════════════════════════════════════════════════════════════════════════
// ROLE-BASED MENU FILTERING — Static ROLE_RESOURCE_ACCESS Map
// ═══════════════════════════════════════════════════════════════════════════════
//
// ARCHITECTURE (2026-02-18):
// - Each menu item has: resource (string)
// - ROLE_RESOURCE_ACCESS maps each role to its allowed resources
// - SUPER_ADMIN gets '*' → sees everything
// - No can(), no action-level checks, no permission matrix
//
// ═══════════════════════════════════════════════════════════════════════════════

import { ROLE_RESOURCE_ACCESS } from 'config/roleAccessMap';

/**
 * Filter menu items based on static Role → Resource map.
 *
 * @param {Array} items - Menu items to filter
 * @param {string} role - User's canonical role (e.g. 'SUPER_ADMIN')
 * @returns {Array} Filtered menu items visible to specified role
 */
export const filterMenuItemsByRole = (items, role) => {
  const allowedResources = ROLE_RESOURCE_ACCESS[role] || [];
  const providerPortalEnabled = (() => {
    try {
      const cached = sessionStorage.getItem('__sys_config__');
      if (!cached) return false;
      const parsed = JSON.parse(cached);
      return Boolean(parsed?.data?.flags?.PROVIDER_PORTAL_ENABLED);
    } catch {
      return false;
    }
  })();

  const isAllowed = (resource) => {
    if (!resource) return true; // group headers without resource → always visible
    if (resource === 'provider_portal' && !providerPortalEnabled && role !== 'PROVIDER_STAFF') return false;
    if (resource.startsWith('__hidden_')) return false; // Explicitly hidden items
    if (allowedResources.includes('*')) return true; // SUPER_ADMIN wildcard
    return allowedResources.includes(resource);
  };

  return items
    .filter((item) => isAllowed(item.resource))
    .map((item) => ({
      ...item,
      children: item.children
        ? filterMenuItemsByRole(item.children, role)
        : undefined
    }))
    .filter((item) => {
      // Remove groups/collapses with no visible children
      if ((item.type === 'group' || item.type === 'collapse') && item.children) {
        return item.children.length > 0;
      }
      return true;
    });
};

// ═══════════════════════════════════════════════════════════════════════════════
// MENU ITEMS (Static Role → Resource Map)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 🏥 PROFESSIONAL TPA SYSTEM - NAVIGATION MENU (2026 STANDARD)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * DESIGN PHILOSOPHY:
 * ✅ Professional TPA Industry Standards
 * ✅ Static ROLE_RESOURCE_ACCESS map drives visibility (see config/roleAccessMap.js)
 * ✅ Future-proof structure
 * ✅ No can(), no action-level checks, no permission matrix
 *
 * NAVIGATION STRUCTURE:
 * 📊 Dashboard          → resource: 'dashboard'
 * 👥 Members            → resource: 'members'
 * 🏥 Provider Portal    → resource: 'provider_portal'
 * 🏢 Employers          → resource: 'employers'
 * 🏥 Providers          → resource: 'providers'
 * 💰 Claims & Approvals → resource: 'claims', 'pre_auth'
 * 💰 Settlements        → resource: 'settlements'
 * 📈 Reports            → resource: 'report_*'
 * 📂 Documents          → resource: 'documents'
 * ⚙️ System Settings    → resource: 'system_settings', 'users', 'audit_logs'
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */

const menuItem = [
  // ═══════════════════════════════════════════════════════════════════════════
  // 📊 DASHBOARD
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-statistics',
    title: 'لوحة المعلومات',
    titleEn: 'Dashboard',
    type: 'group',
    children: [
      {
        id: 'dashboard',
        title: 'لوحة المعلومات',
        titleEn: 'Dashboard',
        type: 'item',
        url: '/dashboard',
        icon: AssessmentIcon,
        resource: 'dashboard',
        action: 'view',
        breadcrumbs: false,
        chip: {
          label: '📊',
          color: 'info',
          size: 'small',
          variant: 'outlined'
        }
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 👥 MEMBERS
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-members',
    title: 'المستفيدين',
    titleEn: 'Insured',
    type: 'group',
    children: [
      {
        id: 'members-list',
        title: 'قائمة المستفيدين',
        titleEn: 'Insured List',
        type: 'item',
        url: '/members',
        icon: PeopleAltIcon,
        resource: 'members',
        action: 'view',
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 🏥 PROVIDER PORTAL (VISIT-CENTRIC FLOW)
  // For Provider Staff only
  // ═══════════════════════════════════════════════════════════════════════════
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-provider-portal',
    title: 'بوابة مقدم الخدمة',
    titleEn: 'Provider Portal',
    type: 'group',
    children: [
      {
        id: 'provider-portal',
        title: 'بوابة مقدم الخدمة',
        titleEn: 'Provider Portal',
        type: 'collapse',
        icon: LocalHospitalIcon,
        resource: 'provider_portal',
        action: 'view',
        children: [
          {
            id: 'provider-eligibility-check',
            title: 'التحقق من الأهلية',
            titleEn: 'Eligibility Check',
            type: 'item',
            url: '/provider/eligibility-check',
            icon: HowToRegIcon,
            resource: 'provider_portal',
            action: 'view',
            chip: {
              label: '1️⃣',
              color: 'primary',
              size: 'small'
            }
          },
          {
            id: 'provider-visit-log',
            title: 'سجل الزيارات',
            titleEn: 'Visit Log',
            type: 'item',
            url: '/provider/visits',
            icon: AssignmentIcon,
            resource: 'provider_portal',
            action: 'view',
            chip: {
              label: '2️⃣',
              color: 'info',
              size: 'small'
            }
          },
          {
            id: 'provider-documents',
            title: 'المستندات',
            titleEn: 'Documents',
            type: 'item',
            url: '/provider/documents',
            icon: FolderIcon,
            resource: 'provider_portal',
            action: 'view',
            chip: {
              label: '3️⃣',
              color: 'secondary',
              size: 'small'
            }
          },
          {
            id: 'provider-reports-divider',
            type: 'divider'
          },
          {
            id: 'provider-claims-report',
            title: 'تقرير المطالبات',
            titleEn: 'Claims Report',
            type: 'item',
            url: '/provider/reports/claims',
            icon: ReceiptIcon,
            resource: 'provider_portal',
            action: 'view'
          },
          {
            id: 'provider-preauth-report',
            title: 'تقرير الموافقات',
            titleEn: 'Pre-Auth Report',
            type: 'item',
            url: '/provider/reports/pre-auth',
            icon: VerifiedUserIcon,
            resource: 'provider_portal',
            action: 'view'
          },
          {
            id: 'provider-visits-report',
            title: 'تقرير الزيارات',
            titleEn: 'Visits Report',
            type: 'item',
            url: '/provider/reports/visits',
            icon: AssessmentIcon,
            resource: 'provider_portal',
            action: 'view'
          }
        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 🏢 EMPLOYERS (PARTNERS)
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-employers',
    title: 'جهات العمل',
    titleEn: 'Employers',
    type: 'group',
    children: [
      {
        id: 'employers',
        title: 'إدارة جهات العمل',
        titleEn: 'Employers Management',
        type: 'collapse',
        icon: BusinessIcon,
        resource: 'employers',
        action: 'view',
        children: [
          {
            id: 'employers-list',
            title: 'قائمة جهات العمل',
            titleEn: 'Employers List',
            type: 'item',
            url: '/employers',
            icon: FormatListBulletedIcon,
            resource: 'employers',
            action: 'view',
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          },
          {
            id: 'benefit-policies',
            title: 'وثائق التأمين',
            titleEn: 'Benefit Policies',
            type: 'item',
            url: '/benefit-policies',
            icon: PolicyIcon,
            resource: 'benefit_policies',
            action: 'view',
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          }
        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 🏥 PROVIDERS
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-providers',
    title: 'مقدمو الخدمات',
    titleEn: 'Providers',
    type: 'group',
    children: [
      {
        id: 'providers',
        title: 'إدارة مقدمي الخدمات',
        titleEn: 'Providers Management',
        type: 'collapse',
        icon: LocalHospitalIcon,
        resource: 'providers',
        action: 'view',
        children: [
          {
            id: 'providers-list',
            title: 'قائمة المقدمين',
            titleEn: 'Providers List',
            type: 'item',
            url: '/providers',
            icon: FormatListBulletedIcon,
            resource: 'providers',
            action: 'view',
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          },
          {
            id: 'provider-contracts',
            title: 'عقود مقدمي الخدمات',
            titleEn: 'Provider Contracts',
            type: 'item',
            url: '/provider-contracts',
            icon: HandshakeIcon,
            resource: 'provider_contracts',
            action: 'view',
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          },
          // hidden: إدارة حسابات المقدمين
          // { id: 'provider-users', title: 'إدارة حسابات المقدمين', ... }
        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 💰 CLAIMS & APPROVALS
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-claims-approvals',
    title: 'المطالبات والموافقات',
    titleEn: 'Claims & Approvals',
    type: 'group',
    children: [
      {
        id: 'claims-approvals',
        title: 'مراجعة المطالبات والموافقات',
        titleEn: 'Review Claims & Approvals',
        type: 'collapse',
        icon: ReceiptIcon,
        resource: 'claims',
        action: 'view',
        children: [
          // NOTE: Claims/Pre-Auth creation happens ONLY from Provider Portal (Visit-Based Flow)
          // Admin panel has NO direct creation - only review and processing
          {
            id: 'claims-batches',
            title: 'نظام الدفعات (Batches)',
            titleEn: 'Claims Batch System',
            type: 'item',
            url: '/claims/batches',
            icon: FolderIcon,
            resource: 'claims',
            action: 'view',
          },
          {
            id: 'email-preauth-requests',
            title: 'طلبات البريد (Pre-Auth)',
            titleEn: 'Email Pre-Auth Requests',
            type: 'item',
            url: '/pre-approvals/email-inbox',
            icon: InboxIcon,
            resource: 'pre_auth',
            action: 'view',
            chip: {
              label: 'وارد',
              color: 'error',
              size: 'small'
            }
          },
          {
            id: 'claims-report',
            title: 'تقرير المطالبات (مراجعة)',
            titleEn: 'Claims Report',
            type: 'item',
            url: '/reports/claims',
            icon: AssessmentIcon,
            resource: 'claims',
            action: 'view',
            chip: {
              label: 'تقرير',
              color: 'info',
              size: 'small'
            }
          }
        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 💰 SETTLEMENT MODULE
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-settlement',
    title: 'التسويات المالية',
    titleEn: 'Financial Settlement',
    type: 'group',
    children: [
      {
        id: 'settlement',
        title: 'إدارة التسويات',
        titleEn: 'Settlement Management',
        type: 'collapse',
        icon: PaymentIcon,
        resource: 'settlements',
        action: 'view',
        children: [
          {
            id: 'provider-accounts',
            title: 'حسابات مقدمي الخدمة',
            titleEn: 'Provider Accounts',
            type: 'item',
            url: '/settlement/provider-accounts',
            icon: BusinessIcon,
            resource: 'provider_accounts',
            action: 'view',
            chip: {
              label: 'معدل',
              color: 'primary',
              size: 'small'
            }
          },
          {
            id: 'provider-payments',
            title: 'دفعات مقدمي الخدمة',
            titleEn: 'Provider Payments',
            type: 'item',
            url: '/settlement/provider-payments',
            icon: AccountBalanceWalletIcon,
            resource: 'provider_accounts',
            action: 'view',
            chip: {
              label: 'جديد',
              color: 'success',
              size: 'small'
            }
          },
          {
            id: 'payments-management',
            title: 'سجل دفعات التسويات',
            titleEn: 'Settlement Payments Register',
            type: 'item',
            url: '/settlement/payments',
            icon: AccountBalanceWalletIcon,
            resource: 'provider_accounts',
            action: 'view',
            chip: {
              label: 'جديد',
              color: 'success',
              size: 'small'
            }
          }

        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 📈 REPORTS CENTER (R1 FOUNDATION)
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-reports-center',
    title: 'التقارير',
    titleEn: 'Reports Center',
    type: 'group',
    children: [
      {
        id: 'reports-center',
        title: 'مركز التقارير',
        titleEn: 'Reports Center',
        type: 'collapse',
        icon: AssessmentIcon,
        resource: 'report_center',
        action: 'view',
        children: [
          {
            id: 'reports-domain-claims',
            title: 'المطالبات',
            titleEn: 'Claims',
            type: 'item',
            url: '/reports/domain/claims',
            icon: ReceiptIcon,
            resource: 'report_domain_claims',
            action: 'view'
          },
          {
            id: 'reports-domain-members',
            title: 'المستفيدون',
            titleEn: 'Members',
            type: 'item',
            url: '/reports/domain/members',
            icon: PeopleAltIcon,
            resource: 'report_domain_members',
            action: 'view'
          },
          {
            id: 'reports-domain-employers',
            title: 'جهات العمل',
            titleEn: 'Employers',
            type: 'item',
            url: '/reports/domain/employers',
            icon: BusinessIcon,
            resource: 'report_domain_employers',
            action: 'view'
          },
          {
            id: 'reports-domain-providers',
            title: 'مقدمو الخدمة',
            titleEn: 'Providers',
            type: 'item',
            // REPORTS-ENGINE-2: flat dedicated report route (was /reports/domain/providers).
            url: '/reports/providers',
            icon: LocalHospitalIcon,
            resource: 'report_domain_providers',
            action: 'view'
          },
          {
            id: 'reports-domain-contracts',
            title: 'العقود',
            titleEn: 'Contracts',
            type: 'item',
            url: '/reports/domain/contracts',
            icon: HandshakeIcon,
            resource: 'report_domain_contracts',
            action: 'view'
          },
          {
            id: 'reports-domain-price-lists',
            title: 'قوائم الأسعار',
            titleEn: 'Price Lists',
            type: 'item',
            url: '/reports/domain/price-lists',
            icon: CategoryIcon,
            resource: 'report_domain_price_lists',
            action: 'view'
          },
          {
            id: 'reports-domain-benefit-policies',
            title: 'وثائق المنافع',
            titleEn: 'Benefit Policies',
            type: 'item',
            url: '/reports/domain/benefit-policies',
            icon: PolicyIcon,
            resource: 'report_domain_benefit_policies',
            action: 'view'
          },
          {
            id: 'reports-domain-financial-settlements',
            title: 'التسويات المالية',
            titleEn: 'Financial Settlements',
            type: 'item',
            url: '/reports/domain/financial-settlements',
            icon: PaymentIcon,
            resource: 'report_domain_financial_settlements',
            action: 'view'
          },
          {
            id: 'reports-domain-audit',
            title: 'التدقيق',
            titleEn: 'Audit',
            type: 'item',
            url: '/reports/domain/audit',
            icon: HistoryIcon,
            resource: 'report_domain_audit',
            action: 'view'
          },
          {
            id: 'reports-domain-system-analytics',
            title: 'إحصائيات النظام',
            titleEn: 'System Analytics',
            type: 'item',
            url: '/reports/domain/system-analytics',
            icon: DashboardIcon,
            resource: 'report_domain_system_analytics',
            action: 'view'
          }
        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 📂 DOCUMENTS
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-documents',
    title: 'الوثائق',
    titleEn: 'Documents',
    type: 'group',
    children: [
      {
        id: 'documents-library',
        title: 'مكتبة الوثائق',
        titleEn: 'Documents Library',
        type: 'item',
        url: '/documents',
        icon: DescriptionIcon,
        resource: '__hidden_documents', // Hidden per user request
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // ⚙️ SYSTEM SETTINGS
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-system-settings',
    title: 'إعدادات النظام',
    titleEn: 'System Settings',
    type: 'group',
    children: [
      {
        id: 'users-management',
        title: 'إدارة المستخدمين',
        titleEn: 'User Management',
        type: 'item',
        url: '/admin/users',
        icon: SecurityIcon,
        resource: 'users',
        action: 'view',
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      },
      {
        id: 'medical-categories',
        title: 'إدارة التصنيفات',
        titleEn: 'Manage Categories',
        type: 'item',
        url: '/medical-categories',
        icon: CategoryIcon,
        resource: 'medical_catalog',
        action: 'view',
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      },
      {
        id: 'classification-imports',
        title: 'قوائم أسعار المرافق',
        titleEn: 'Provider Price Lists',
        type: 'item',
        url: '/classification/imports',
        icon: CategoryIcon,
        resource: 'medical_catalog',
        action: 'view'
      },
      {
        id: 'system-configuration',
        title: 'تكوين النظام والمؤسسة',
        titleEn: 'System & Organization Configuration',
        type: 'item',
        url: '/settings/system',
        icon: SettingsIcon,
        resource: 'system_settings',
        action: 'view',
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      },
      {
        id: 'kinship-mismatch',
        title: 'تصحيح بيانات المستفيدين',
        titleEn: 'Beneficiary Kinship Mismatch',
        type: 'item',
        url: '/settings/kinship-mismatch',
        icon: PeopleAltIcon,
        resource: 'system_settings',
        action: 'view',
        chip: {
          label: 'جديد',
          color: 'primary',
          size: 'small'
        }
      },
      // MC-4B (design review §10): old experimental preparation screen HIDDEN
      // from the menu — superseded by «قوائم أسعار المرافق» (the classification
      // module). Route kept until the M3 regression gate passes, then deleted.
      // {
      //   id: 'facility-price-preparation',
      //   title: 'تجهيز قوائم أسعار المرافق',
      //   url: '/settings/facility-price-preparation',
      //   icon: FormatListBulletedIcon, resource: 'system_settings', action: 'view'
      // },
      {
        id: 'medical-audit-logs',
        title: 'سجل التدقيق الطبي',
        titleEn: 'Medical Audit Logs',
        type: 'item',
        url: '/admin/users/medical-audit-logs',
        icon: HistoryIcon,
        resource: 'users',
        action: 'view',
        chip: {
          label: 'جديد',
          color: 'warning',
          size: 'small'
        }
      },
      {
        id: 'member-duplicates',
        title: 'دمج السجلات المكررة',
        titleEn: 'Member Duplicates Resolver',
        type: 'item',
        url: '/settings/member-duplicates',
        icon: PeopleAltIcon,
        resource: 'system_settings',
        action: 'view',
        chip: {
          label: 'هام',
          color: 'error',
          size: 'small'
        }
      }
    ]
  }
];

export default menuItem;
