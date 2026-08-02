/**
 * System Role Definitions — Phase 5 (Static Role-Based Auth),
 * extended by WAAD-RBAC-PHASE-2-FRONTEND-INTEGRATION.
 *
 * AUTHORITATIVE source for role constants in frontend.
 * Mirrors backend SystemRole enum (see backend/.../security/rbac/SystemRole.java).
 *
 * ARCHITECTURE (2026-02-18):
 * - Backend removed dynamic RBAC (permissions table, roles table) — Phase 5
 * - Each user has exactly ONE role stored in user.userType
 * - All authorization is role-based — no permission strings
 *
 * WAAD-RBAC-PHASE-1/2 (2026-07-31): the backend re-introduced a *read-model*
 * permission catalog (permissions/role_permissions/user_permission_overrides)
 * purely for reporting — /auth/session/me now returns a real, non-empty
 * `permissions: string[]` (effective permissions). This does NOT change the
 * statement above: role/resource checks in PermissionGuard remain the actual
 * enforcement; `permissions` is additive, UI-only signal (see PermissionGuard.jsx).
 *
 * NOTE: WAAD_ADMIN is intentionally NOT given broad resource access in
 * roleAccessMap.js yet. Phase 1 only widened the backend's UserController to
 * accept WAAD_ADMIN — every other controller (dashboard, claims, settlements,
 * etc.) still requires SUPER_ADMIN. Granting broader frontend resources today
 * would show WAAD_ADMIN menu items/pages whose API calls all 403 — see
 * docs/rbac/WAAD-RBAC-PHASE-2-FRONTEND-INTEGRATION-REPORT.md §3.
 *
 * BENEFICIARY is a reserved future role on the backend (not assignable via
 * UserCreateDto/UserUpdateDto yet) and is deliberately NOT added here — there
 * is nothing for it to do on the frontend until that phase starts.
 */

// ============================================
// System Roles (matches backend SystemRole enum)
// ============================================

export const SystemRole = Object.freeze({
  SUPER_ADMIN: 'SUPER_ADMIN',
  WAAD_ADMIN: 'WAAD_ADMIN',
  MEDICAL_REVIEWER: 'MEDICAL_REVIEWER',
  ACCOUNTANT: 'ACCOUNTANT',
  PROVIDER_STAFF: 'PROVIDER_STAFF',
  EMPLOYER_ADMIN: 'EMPLOYER_ADMIN',
  DATA_ENTRY: 'DATA_ENTRY',
  FINANCE_VIEWER: 'FINANCE_VIEWER'
});

// ============================================
// Role Display Names (Arabic + English)
// ============================================

export const RoleDisplayNames = Object.freeze({
  [SystemRole.SUPER_ADMIN]: { ar: 'مدير النظام', en: 'Super Admin' },
  [SystemRole.WAAD_ADMIN]: { ar: 'مدير وعد', en: 'WAAD Admin' },
  [SystemRole.MEDICAL_REVIEWER]: { ar: 'مراجع طبي', en: 'Medical Reviewer' },
  [SystemRole.ACCOUNTANT]: { ar: 'محاسب', en: 'Accountant' },
  [SystemRole.PROVIDER_STAFF]: { ar: 'موظف مقدم خدمة', en: 'Provider Staff' },
  [SystemRole.EMPLOYER_ADMIN]: { ar: 'مدير جهة العمل', en: 'Employer Admin' },
  [SystemRole.DATA_ENTRY]: { ar: 'مدخل بيانات', en: 'Data Entry' },
  [SystemRole.FINANCE_VIEWER]: { ar: 'مشاهد مالي', en: 'Finance Viewer' }
});

// ============================================
// Role Privilege Levels (for hierarchy checks)
// ============================================

export const RolePrivilegeLevel = Object.freeze({
  [SystemRole.SUPER_ADMIN]: 999,
  [SystemRole.WAAD_ADMIN]: 900,
  [SystemRole.MEDICAL_REVIEWER]: 80,
  [SystemRole.ACCOUNTANT]: 70,
  [SystemRole.DATA_ENTRY]: 60,
  [SystemRole.FINANCE_VIEWER]: 50,
  [SystemRole.EMPLOYER_ADMIN]: 40,
  [SystemRole.PROVIDER_STAFF]: 30
});

// ============================================
// RBAC UI labels (WAAD-RBAC-PHASE-2-FRONTEND-INTEGRATION)
// ============================================

export const RbacUiLabels = Object.freeze({
  effectivePermissions: 'صلاحيات فعلية',
  inheritedPermissions: 'صلاحيات موروثة من الدور',
  overridePermissions: 'صلاحيات خاصة',
  superAdminProtected: 'غير مسموح بتعديل مدير النظام الأعلى',
  sensitivePermission: 'صلاحية حساسة',
  protected: 'محمي'
});

// Arabic labels for the WAAD-RBAC-PHASE-1 permission catalog (mirrors the
// label_ar column seeded in V101__rbac_phase1_foundation.sql). The
// effective-permissions endpoint returns bare codes only (no labels/groups),
// so this is a display-only lookup, not a second source of truth for access
// control. Codes absent from this map fall back to the raw code string.
export const PermissionLabels = Object.freeze({
  'beneficiaries.read': 'عرض المستفيدين',
  'employers.read': 'عرض جهات العمل',
  'providers.read': 'عرض مقدمي الخدمة',
  'providers.manage': 'إدارة مقدمي الخدمة',
  'contracts.read': 'عرض العقود',
  'portal.provider': 'بوابة مقدم الخدمة',
  'claims.read': 'عرض المطالبات',
  'claims.review': 'اعتماد / رفض المطالبات',
  'dashboard.read': 'لوحة المؤشرات',
  'settings.manage': 'إعدادات النظام',
  'reports.claims': 'تقارير المطالبات',
  'reports.members': 'تقارير المستفيدين',
  'reports.employers': 'تقارير جهات العمل',
  'reports.providers': 'تقارير مقدمي الخدمة',
  'reports.contracts': 'تقارير العقود',
  'reports.price_lists': 'تقارير قوائم الأسعار',
  'reports.benefit_policies': 'تقارير وثائق المنافع',
  'reports.financial_settlements': 'تقارير التسويات المالية',
  'reports.audit': 'تقارير التدقيق',
  'reports.system_analytics': 'تقارير إحصائيات النظام',
  'settlements.read': 'عرض الدفعات',
  'settlements.approve': 'اعتماد الدفعات',
  'preauth.read': 'عرض طلبات الموافقات المسبقة',
  'medical_catalog.read': 'عرض التصنيف الطبي',
  'benefit_policies.read': 'عرض وثائق المنافع',
  'provider_accounts.read': 'عرض حسابات مقدمي الخدمة',
  'documents.read': 'عرض مكتبة المستندات'
});

// Matches the `critical_security` flag seeded in V101 — currently only
// settings.manage. Kept in sync manually since the catalog is small and
// static; if this drifts, worst case is a missing "protected" badge in the
// UI, not a security gap (WAAD_ADMIN's inability to override this permission
// is enforced backend-side in EffectivePermissionService, not here).
export const CriticalSecurityPermissions = Object.freeze(['settings.manage']);

// ============================================
// Utility Functions
// ============================================

export const isSuperAdminRole = (role) => role === SystemRole.SUPER_ADMIN;

export const getPrivilegeLevel = (role) => RolePrivilegeLevel[role] ?? 0;

export const getAssignableRoles = (currentRole) => {
  if (currentRole === SystemRole.SUPER_ADMIN) {
    return Object.values(SystemRole);
  }
  // WAAD_ADMIN "manages normal users and operational roles" (Phase 1 ticket
  // rule 2) — every role except SUPER_ADMIN itself.
  if (currentRole === SystemRole.WAAD_ADMIN) {
    return Object.values(SystemRole).filter((role) => role !== SystemRole.SUPER_ADMIN);
  }
  return [];
};

export const canModifyRole = (currentRole, targetRole) => {
  if (currentRole === SystemRole.SUPER_ADMIN) return true;
  return getPrivilegeLevel(currentRole) > getPrivilegeLevel(targetRole);
};

export const getRoleDisplayName = (role, lang = 'ar') => {
  const names = RoleDisplayNames[role];
  return names ? names[lang] : role;
};

export const isProviderRole = (role) => {
  return role === SystemRole.PROVIDER_STAFF || role === 'PROVIDER';
};

export const isAdminRole = (role) => {
  return role === SystemRole.SUPER_ADMIN || role === SystemRole.WAAD_ADMIN;
};

export default {
  SystemRole,
  RoleDisplayNames,
  RolePrivilegeLevel,
  RbacUiLabels,
  PermissionLabels,
  CriticalSecurityPermissions,
  isSuperAdminRole,
  getPrivilegeLevel,
  getAssignableRoles,
  canModifyRole,
  getRoleDisplayName,
  isProviderRole,
  isAdminRole
};
