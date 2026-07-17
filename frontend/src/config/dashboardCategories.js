// ============================================================================
// Dashboard "System Categories" whitelist (presentation config only)
// ----------------------------------------------------------------------------
// A CLOSED whitelist of operational modules shown in the home quick-access and
// the System Categories dialog. It is intentionally limited to the modules
// approved for this UI. Financial / settlements / payments / invoices modules
// are deliberately excluded HERE ONLY — their routes and RBAC are untouched and
// they remain reachable from the sidebar.
//
// RBAC: each entry references a real menu node id. A card is shown ONLY when
// that node is present in the role-filtered menu tree (useRBACSidebar). This is
// the intersection of (whitelist) ∩ (RBAC-allowed) — no role names hard-coded,
// no permission strings invented.
//
// Navigation: the target URL is resolved from the actual menu node (first
// reachable leaf), so links always match existing routes.
// ============================================================================

// Category group keys + labels (order matters for display)
export const CATEGORY_GROUPS = Object.freeze([
  { key: 'medical_ops', title: 'العمليات الطبية' },
  { key: 'network_contracts', title: 'إدارة الشبكة والتعاقدات' },
  { key: 'admin_analysis', title: 'الإدارة والتحليل' }
]);

// The closed whitelist. `menuId` is matched against the RBAC-filtered menu tree.
// `countKey` (optional) maps to a DashboardSummary field for a real badge value.
export const DASHBOARD_MODULES = Object.freeze([
  // أ. العمليات الطبية
  {
    id: 'claims-approvals',
    group: 'medical_ops',
    menuId: 'group-claims-approvals',
    // Explicit destination: the claims group's first leaf is /claims/batches
    // (the batch system), which is NOT the operational claims-review entry.
    // This system has no dedicated operational claims-LIST route; the closest
    // real, review-oriented, non-batch route is the claims review report.
    destination: '/reports/claims',
    iconKey: 'claims',
    title: 'المطالبات والموافقات',
    description: 'مراجعة المطالبات والموافقات الطبية',
    countKey: 'openClaims',
    countLabel: 'قيد المراجعة',
    highlight: true
  },
  {
    id: 'providers',
    group: 'medical_ops',
    menuId: 'group-providers',
    iconKey: 'providers',
    title: 'مقدمو الخدمات',
    description: 'المستشفيات والعيادات والصيدليات المعتمدة',
    countKey: 'activeProviders',
    countLabel: 'نشط'
  },
  {
    id: 'members',
    group: 'medical_ops',
    menuId: 'group-members',
    iconKey: 'members',
    title: 'المستفيدون',
    description: 'بطاقات وسجلات ومستحقات المستفيدين',
    countKey: 'activeMembers',
    countLabel: 'مستفيد'
  },
  // ب. إدارة الشبكة والتعاقدات
  {
    id: 'contracts',
    group: 'network_contracts',
    menuId: 'provider-contracts',
    iconKey: 'contracts',
    title: 'العقود وقوائم الأسعار',
    description: 'إدارة الاتفاقيات والتسعيرات مع المزودين',
    countKey: 'activeContracts',
    countLabel: 'عقد نشط'
  },
  {
    id: 'employers',
    group: 'network_contracts',
    menuId: 'group-employers',
    iconKey: 'employers',
    title: 'جهات العمل',
    description: 'الشركات وحصصها والعقود المرتبطة'
  },
  // ج. الإدارة والتحليل
  {
    id: 'reports',
    group: 'admin_analysis',
    menuId: 'group-reports-center',
    iconKey: 'reports',
    title: 'التقارير',
    description: 'تقارير المطالبات والمستفيدين والمزودين'
  },
  {
    id: 'dashboard',
    group: 'admin_analysis',
    menuId: 'dashboard',
    iconKey: 'dashboard',
    title: 'لوحة المعلومات',
    description: 'نظرة عامة تشغيلية محدثة'
  },
  {
    id: 'settings',
    group: 'admin_analysis',
    menuId: 'group-system-settings',
    iconKey: 'settings',
    title: 'إعدادات النظام',
    description: 'الإعدادات والتهيئة والصلاحيات'
  }
]);

// Modules featured in the compact home "quick access" panel (subset of above).
export const QUICK_ACCESS_IDS = Object.freeze(['claims-approvals', 'providers', 'members']);

// ── menu-tree helpers (RBAC + navigation) ──────────────────────────────────

/** Depth-first find a node by id within the RBAC-filtered menu tree. */
export const findMenuNodeById = (groups, id) => {
  if (!Array.isArray(groups)) return null;
  for (const node of groups) {
    if (!node) continue;
    if (node.id === id) return node;
    if (node.children) {
      const found = findMenuNodeById(node.children, id);
      if (found) return found;
    }
  }
  return null;
};

/** First reachable leaf url under a node (or the node's own url). */
export const firstLeafUrl = (node) => {
  if (!node) return null;
  if (node.type === 'item' && node.url) return node.url;
  if (node.url && node.type !== 'group' && node.type !== 'collapse') return node.url;
  if (node.children) {
    for (const child of node.children) {
      const url = firstLeafUrl(child);
      if (url) return url;
    }
  }
  // group/collapse with an explicit url but no item children
  return node.url || null;
};

/**
 * Resolve the visible + navigable modules for a role, given the RBAC-filtered
 * menu tree. Returns whitelist entries enriched with { url } for the ones the
 * user can actually access; hides the rest.
 */
export const resolveAccessibleModules = (sidebarGroups) => {
  return DASHBOARD_MODULES.map((mod) => {
    const node = findMenuNodeById(sidebarGroups, mod.menuId);
    if (!node) return null; // RBAC: role cannot access this module
    // Explicit destination wins over firstLeafUrl (which can be semantically
    // wrong, e.g. a group whose first leaf is the batch system).
    const url = mod.destination || firstLeafUrl(node);
    if (!url) return null;
    return { ...mod, url };
  }).filter(Boolean);
};
