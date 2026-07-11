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

  MEDICAL_REVIEWER: [
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
