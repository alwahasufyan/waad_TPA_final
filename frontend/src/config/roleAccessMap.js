/**
 * ROLE_RESOURCE_ACCESS — Static Role → Resource Visibility Map
 *
 * AUTHORITATIVE source for which menu resources each role can see.
 * '*' means all resources (SUPER_ADMIN only).
 *
 * This map is consumed by filterMenuItemsByRole() in menu-items/components.jsx
 * and drives the entire sidebar/navigation visibility.
 *
 * RULES:
 * - Backend enforces actual data access — this is UI visibility only.
 * - PROVIDER_STAFF only sees 'provider_portal' — no extra runtime scoping needed.
 * - Add new resources here when adding new menu items.
 */
export const ROLE_RESOURCE_ACCESS = Object.freeze({
  SUPER_ADMIN: ['*'],

  // WAAD-RBAC-PHASE-3A-WAAD-ADMIN-FULL-ACCESS: product decision — WAAD_ADMIN
  // is a full operational administrator, same navigation as SUPER_ADMIN,
  // except SUPER_ADMIN-account protection (enforced backend-side in
  // UserService, not a resource key) and the danger-zone system
  // reset/restore/OTP actions (DangerZoneController stayed SUPER_ADMIN-only
  // on the backend — there is no separate frontend "danger zone" resource to
  // exclude here; those actions simply aren't reachable because their
  // backend endpoints still reject WAAD_ADMIN). Also NOT reachable for
  // WAAD_ADMIN, deliberately, even though it's technically part of
  // "system_settings": SystemAdminController's test-data reset/seed
  // endpoints and MedicalAuditLogController's bulk-delete endpoint remain
  // SUPER_ADMIN-only on the backend (destructive/irreversible actions on
  // real business data or immutable audit trails) — see the Phase 3A report
  // §9 for the full list of backend endpoints deliberately not widened.
  WAAD_ADMIN: [
    'dashboard',
    'members',
    'employers',
    'providers',
    'provider_contracts',
    'provider_portal',
    'claims',
    'pre_auth',
    'medical_catalog',
    'benefit_policies',
    'settlements',
    'provider_accounts',
    'documents',
    'users',
    'system_settings',
    'report_center',
    'report_domain_claims',
    'report_domain_members',
    'report_domain_employers',
    'report_domain_providers',
    'report_domain_contracts',
    'report_domain_price_lists',
    'report_domain_benefit_policies',
    'report_domain_financial_settlements',
    'report_domain_audit',
    'report_domain_system_analytics',
    'report_claims',
    'report_pre_approvals',
    'report_financial',
    'report_provider_settlement',
    'report_employers',
    'report_beneficiaries',
    'report_benefit_policy'
  ],

  MEDICAL_REVIEWER: [
    'dashboard',
    'claims',
    'pre_auth',
    'approvals_dashboard',
    'documents',
    'report_center',
    'report_domain_claims',
    'report_domain_providers',
    'report_domain_system_analytics',
    'report_claims',
    'report_pre_approvals'
  ],

  ACCOUNTANT: [
    'settlements',
    'provider_accounts',
    'documents',
    'report_center',
    'report_domain_financial_settlements',
    'report_domain_price_lists',
    'report_financial',
    'report_provider_settlement'
  ],

  PROVIDER_STAFF: [
    'provider_portal'
  ],

  // Legacy provider accounts may still report PROVIDER while migrated
  // accounts report PROVIDER_STAFF. Both represent the same portal scope.
  PROVIDER: [
    'provider_portal'
  ],

  EMPLOYER_ADMIN: [
    'members',
    'benefit_policies',
    'documents',
    'report_center',
    'report_domain_members',
    'report_domain_employers',
    'report_domain_benefit_policies',
    'report_employers',
    'report_beneficiaries',
    'report_benefit_policy'
  ],

  DATA_ENTRY: [
    'members',
    'employers',
    'providers',
    'claims',
    'documents',
    'medical_catalog',
    'report_center',
    'report_domain_claims'
  ],

  FINANCE_VIEWER: [
    'report_center',
    'report_domain_financial_settlements',
    'report_financial',
    'report_provider_settlement'
  ]
});

export default ROLE_RESOURCE_ACCESS;
