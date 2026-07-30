// System Categories is the single curated navigation surface.  Menu nodes are
// still used as the RBAC source of truth; routes are never invented here.
export const CATEGORY_GROUPS = Object.freeze([
  { key: 'records', title: '\u0627\u0644\u0633\u062c\u0644\u0627\u062a \u0627\u0644\u0623\u0633\u0627\u0633\u064a\u0629' },
  { key: 'claims', title: '\u0627\u0644\u0645\u0637\u0627\u0644\u0628\u0627\u062a \u0648\u0627\u0644\u0645\u0648\u0627\u0641\u0642\u0627\u062a' },
  { key: 'reports', title: '\u0627\u0644\u062a\u0642\u0627\u0631\u064a\u0631' },
  { key: 'settings', title: '\u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a' },
  // NAVIGATION-CATEGORIES-CLEANUP-1: the 'superAdminOnly' flag previously here
  // was dead code \u2014 nothing in this file or resolveAccessibleModules() ever
  // read it. Actual visibility for this group's tiles is enforced by the
  // real gate: each tile's menuId resolves to a sidebar node with
  // resource: 'system_settings' in menu-items/components.jsx, filtered
  // through ROLE_RESOURCE_ACCESS (config/roleAccessMap.js). Do not add a
  // second, parallel RBAC flag here \u2014 keep visibility resolution in the one
  // real place.
  { key: 'maintenance', title: '\u0623\u062f\u0648\u0627\u062a \u0627\u0644\u0635\u064a\u0627\u0646\u0629' }
]);

export const DASHBOARD_MODULES = Object.freeze([
  { id: 'members', group: 'records', menuId: 'group-members', iconKey: 'members', title: '\u0627\u0644\u0645\u0633\u062a\u0641\u064a\u062f\u0648\u0646', destination: '/members' },
  { id: 'employers', group: 'records', menuId: 'group-employers', iconKey: 'employers', title: '\u062c\u0647\u0627\u062a \u0627\u0644\u0639\u0645\u0644' },
  { id: 'providers', group: 'records', menuId: 'group-providers', iconKey: 'providers', title: '\u0645\u0642\u062f\u0645\u0648 \u0627\u0644\u062e\u062f\u0645\u0627\u062a' },
  { id: 'contracts', group: 'records', menuId: 'provider-contracts', iconKey: 'contracts', title: '\u0627\u0644\u0639\u0642\u0648\u062f' },
  { id: 'medical-services', group: 'records', menuId: 'medical-categories', destination: '/medical-categories', iconKey: 'medical', title: '\u0627\u0644\u062e\u062f\u0645\u0627\u062a \u0627\u0644\u0637\u0628\u064a\u0629' },
  { id: 'claims', group: 'claims', menuId: 'group-claims-approvals', destination: '/reports/claims', iconKey: 'claims', title: '\u0627\u0644\u0645\u0637\u0627\u0644\u0628\u0627\u062a \u0648\u0627\u0644\u0645\u0648\u0627\u0641\u0642\u0627\u062a' },
  { id: 'claims-review', group: 'claims', menuId: 'claims-review-inbox', destination: '/claims/review', iconKey: 'claims', title: '\u0645\u0631\u0627\u062c\u0639\u0629 \u0627\u0644\u0645\u0637\u0627\u0644\u0628\u0627\u062a' },
  { id: 'claims-batches', group: 'claims', menuId: 'claims-batches', iconKey: 'claims', title: '\u062f\u0641\u0639\u0627\u062a \u0627\u0644\u0645\u0637\u0627\u0644\u0628\u0627\u062a' },
  { id: 'preauth', group: 'claims', menuId: 'email-preauth-requests', destination: '/pre-approvals/email-inbox', iconKey: 'approvals', title: '\u0627\u0644\u0645\u0648\u0627\u0641\u0642\u0627\u062a \u0627\u0644\u0645\u0633\u0628\u0642\u0629' },
  { id: 'preauth-review', group: 'claims', menuId: 'preauth-review-inbox', destination: '/pre-approvals/review', iconKey: 'approvals', title: '\u0645\u0631\u0627\u062c\u0639\u0629 \u0627\u0644\u0645\u0648\u0627\u0641\u0642\u0627\u062a \u0627\u0644\u0645\u0633\u0628\u0642\u0629' },
  // Unified visits register: the administrative list includes the provider
  // column and keeps provider filtering in one place.  The provider-portal
  // visit log remains available from the provider menu for provider users.
  { id: 'visits', group: 'claims', menuId: 'group-claims-approvals', destination: '/visits', iconKey: 'claims', title: '\u0627\u0644\u0632\u064a\u0627\u0631\u0627\u062a' },
  { id: 'reports', group: 'reports', menuId: 'group-reports-center', destination: '/reports', iconKey: 'reports', title: '\u0645\u0631\u0643\u0632 \u0627\u0644\u062a\u0642\u0627\u0631\u064a\u0631' },
  { id: 'benefit-policies', group: 'records', menuId: 'benefit-policies', destination: '/benefit-policies', iconKey: 'reports', title: '\u0648\u062b\u0627\u0626\u0642 \u0627\u0644\u0645\u0646\u0627\u0641\u0639' },
  { id: 'users', group: 'settings', menuId: 'users-management', destination: '/admin/users', iconKey: 'settings', title: '\u0627\u0644\u0645\u0633\u062a\u062e\u062f\u0645\u0648\u0646 \u0648\u0627\u0644\u0635\u0644\u0627\u062d\u064a\u0627\u062a' },
  { id: 'settings', group: 'settings', menuId: 'group-system-settings', destination: '/settings/system', iconKey: 'settings', title: '\u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0646\u0638\u0627\u0645' },
  { id: 'dashboard', group: 'records', menuId: 'dashboard', destination: '/dashboard', iconKey: 'dashboard', title: '\u0644\u0648\u062d\u0629 \u0627\u0644\u062a\u062d\u0643\u0645' },
  { id: 'price-lists', group: 'records', menuId: 'classification-imports', destination: '/classification/imports', iconKey: 'medical', title: '\u0642\u0648\u0627\u0626\u0645 \u0627\u0644\u0623\u0633\u0639\u0627\u0631' },
  // Provider staff only has provider_portal resources. These cards resolve
  // against the same RBAC-filtered provider menu tree, so the categories
  // dialog remains useful without exposing administrative modules.
  { id: 'provider-eligibility', group: 'records', menuId: 'provider-eligibility-check', destination: '/provider/eligibility-check', iconKey: 'approvals', title: '\u0627\u0644\u062a\u062d\u0642\u0642 \u0645\u0646 \u0627\u0644\u0623\u0647\u0644\u064a\u0629' },
  { id: 'provider-visits', group: 'records', menuId: 'provider-visit-log', destination: '/provider/visits', iconKey: 'claims', title: '\u0633\u062c\u0644 \u0627\u0644\u0632\u064a\u0627\u0631\u0627\u062a' },
  { id: 'provider-documents', group: 'records', menuId: 'provider-documents', destination: '/provider/documents', iconKey: 'documents', title: '\u0627\u0644\u0645\u0633\u062a\u0646\u062f\u0627\u062a' },
  { id: 'provider-preauth', group: 'records', menuId: 'provider-preauth-inbox', destination: '/provider/pre-auth-inbox', iconKey: 'approvals', title: '\u0637\u0644\u0628\u0627\u062a \u0627\u0644\u0645\u0648\u0627\u0641\u0642\u0629 \u0627\u0644\u0645\u0633\u0628\u0642\u0629' },
  { id: 'maintenance-kinship', group: 'maintenance', menuId: 'maintenance-kinship', iconKey: 'maintenance', title: '\u062a\u0635\u062d\u064a\u062d \u0628\u064a\u0627\u0646\u0627\u062a \u0627\u0644\u0645\u0633\u062a\u0641\u064a\u062f\u064a\u0646' },
  { id: 'maintenance-duplicates', group: 'maintenance', menuId: 'maintenance-duplicates', iconKey: 'maintenance', title: '\u062f\u0645\u062c \u0627\u0644\u0633\u062c\u0644\u0627\u062a \u0627\u0644\u0645\u062a\u0643\u0631\u0631\u0629' },
  { id: 'maintenance-backups', group: 'maintenance', menuId: 'maintenance-backups', iconKey: 'maintenance', title: '\u0627\u0644\u0646\u0633\u062e \u0627\u0644\u0627\u062d\u062a\u064a\u0627\u0637\u064a' },
  { id: 'maintenance-monitoring', group: 'maintenance', menuId: 'maintenance-monitoring', iconKey: 'maintenance', title: '\u0627\u0644\u062a\u0646\u0628\u064a\u0647\u0627\u062a \u0648\u0627\u0644\u0645\u0631\u0627\u0642\u0628\u0629' },
  { id: 'maintenance-errors', group: 'maintenance', menuId: 'maintenance-errors', iconKey: 'maintenance', title: '\u0633\u062c\u0644 \u0623\u062e\u0637\u0627\u0621 \u0627\u0644\u0646\u0638\u0627\u0645' }
]);

// Maintenance is one workspace with five internal tabs, not five dashboard cards.
export const DASHBOARD_MAINTENANCE_MODULE = Object.freeze({
  id: 'maintenance', group: 'maintenance', menuId: 'group-maintenance',
  destination: '/settings/maintenance?tab=kinship', iconKey: 'maintenance', title: 'أدوات الصيانة'
});

export const QUICK_ACCESS_IDS = Object.freeze(['claims', 'providers', 'members']);

export const findMenuNodeById = (groups, id) => {
  if (!Array.isArray(groups)) return null;
  for (const node of groups) {
    if (!node) continue;
    if (node.id === id) return node;
    const found = findMenuNodeById(node.children, id);
    if (found) return found;
  }
  return null;
};

export const firstLeafUrl = (node) => {
  if (!node) return null;
  if (node.url && node.type !== 'group' && node.type !== 'collapse') return node.url;
  for (const child of node.children || []) {
    const url = firstLeafUrl(child);
    if (url) return url;
  }
  return node.url || null;
};

// Provider navigation is intentionally derived from the same RBAC-filtered
// menu tree used by the sidebar. Keeping provider links here as a second
// hard-coded list caused the System Categories launcher to omit reports and
// the provider's own pre-authorization inbox.
const flattenProviderLeaves = (nodes, result = []) => {
  (nodes || []).forEach((node) => {
    if (!node || node.type === 'divider') return;
    if (node.type === 'item' && node.url) {
      result.push({
        id: `provider-${node.id}`,
        group: 'records',
        menuId: node.id,
        destination: node.url,
        title: node.title,
        icon: node.icon,
        iconKey: 'provider'
      });
      return;
    }
    flattenProviderLeaves(node.children, result);
  });
  return result;
};

const resolveProviderPortalModules = (sidebarGroups) => {
  const providerGroup = findMenuNodeById(sidebarGroups, 'group-provider-portal');
  return providerGroup ? flattenProviderLeaves(providerGroup.children) : [];
};

export const resolveAccessibleModules = (sidebarGroups) => [
  ...DASHBOARD_MODULES.filter((mod) => mod.group !== 'maintenance' && !mod.id.startsWith('provider-')),
  ...resolveProviderPortalModules(sidebarGroups),
  DASHBOARD_MAINTENANCE_MODULE
].map((mod) => {
  // Domain cards belong to the unified Reports center. Some role-filtered
  // trees expose the center permission but omit individual domain leaves;
  // retain the domain route guard while using the center as the navigation
  // visibility anchor in that case.
  const node = findMenuNodeById(sidebarGroups, mod.menuId) ||
    (mod.group === 'reports' ? findMenuNodeById(sidebarGroups, 'group-reports-center') : null);
  if (!node) return null;
  const url = mod.destination || firstLeafUrl(node);
  return url ? { ...mod, url, icon: node.icon } : null;
}).filter(Boolean);
