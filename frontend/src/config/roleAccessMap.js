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

  // WAAD-RBAC-PHASE-2-FRONTEND-INTEGRATION: intentionally narrow. Phase 1
  // (backend) only widened UserController's @PreAuthorize to accept
  // WAAD_ADMIN — every other controller (dashboard, claims, settlements,
  // providers, reports, system settings, ...) still requires SUPER_ADMIN
  // only. Listing more resources here would show WAAD_ADMIN menu items whose
  // API calls all 403 — a worse UX than not showing them, and exactly the
  // "obvious wrong action" the ticket says the UI should prevent. Widen this
  // array only in lockstep with widening the corresponding backend
  // @PreAuthorize (tracked as Phase 3 work, see the Phase 2 report §10).
  WAAD_ADMIN: [
    'users'
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
    'report_domain_audit',
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
    'report_domain_audit',
    'report_financial',
    'report_provider_settlement'
  ]
});

export default ROLE_RESOURCE_ACCESS;
