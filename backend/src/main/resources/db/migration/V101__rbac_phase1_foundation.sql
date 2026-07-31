-- WAAD-RBAC-PHASE-1-FOUNDATION
--
-- Phase 1 of the enterprise RBAC model: RBAC (role) + Data Scope (already
-- present via users.employer_id/provider_id + AuthorizationService) + a new,
-- limited, AUDITED user-permission-override mechanism.
--
-- This migration:
--   1) widens the users.user_type CHECK constraint to allow WAAD_ADMIN and
--      BENEFICIARY (BENEFICIARY is reserved for a future phase — see
--      SystemRole.java javadoc — no permissions are seeded for it here).
--   2) creates the permission catalog (`permissions`), the static role -> permission
--      mapping (`role_permissions`), and the audited per-user override table
--      (`user_permission_overrides`).
--   3) seeds the catalog and the initial role mapping.
--
-- IMPORTANT: this does NOT change any @PreAuthorize/hasRole(...) enforcement.
-- Route/method authorization remains role-based (the existing, tested security
-- boundary). The tables added here back a new, additive "effective permissions"
-- read model (EffectivePermissionService) that reports/audits what a user can
-- do; they are not yet consulted by @PreAuthorize checks. See
-- docs/rbac/WAAD-RBAC-PHASE-1-FOUNDATION-REPORT.md for the full rationale.

-- ===================== 1) users.user_type: add WAAD_ADMIN, BENEFICIARY =====================
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users ADD CONSTRAINT users_user_type_check CHECK (user_type IN (
    'SUPER_ADMIN', 'WAAD_ADMIN', 'EMPLOYER_ADMIN', 'MEDICAL_REVIEWER',
    'PROVIDER_STAFF', 'ACCOUNTANT', 'FINANCE_VIEWER', 'DATA_ENTRY', 'BENEFICIARY'
));

-- ===================== 2) permission catalog =====================
CREATE TABLE IF NOT EXISTS permissions (
    code               VARCHAR(100) PRIMARY KEY,
    group_name         VARCHAR(100) NOT NULL,
    label_ar           VARCHAR(255) NOT NULL,
    label_en           VARCHAR(255) NOT NULL,
    sensitive          BOOLEAN NOT NULL DEFAULT FALSE,
    critical_security  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===================== 3) static role -> permission mapping =====================
CREATE TABLE IF NOT EXISTS role_permissions (
    role            VARCHAR(50) NOT NULL,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
    PRIMARY KEY (role, permission_code)
);
CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role);

-- ===================== 4) audited per-user overrides =====================
CREATE TABLE IF NOT EXISTS user_permission_overrides (
    id              BIGINT PRIMARY KEY DEFAULT nextval('user_seq'),
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
    effect          VARCHAR(10) NOT NULL CHECK (effect IN ('GRANT', 'REVOKE')),
    reason          TEXT NOT NULL,
    granted_by      BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP,
    revoked_at      TIMESTAMP,
    revoked_by      BIGINT REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_user_permission_overrides_user ON user_permission_overrides(user_id);

-- ===================== 5) seed permission catalog =====================
-- Groups mirror the frontend's dashboardCategories.js CATEGORY_GROUPS
-- (records / medical network / claims & review / system / reports / finance).
INSERT INTO permissions (code, group_name, label_ar, label_en, sensitive, critical_security) VALUES
    ('beneficiaries.read',  'records',  'عرض المستفيدين',            'View beneficiaries',            FALSE, FALSE),
    ('employers.read',      'records',  'عرض جهات العمل',            'View employers',                FALSE, FALSE),
    ('providers.read',      'network',  'عرض مقدمي الخدمة',          'View providers',                FALSE, FALSE),
    ('providers.manage',    'network',  'إدارة مقدمي الخدمة',        'Manage providers',              TRUE,  FALSE),
    ('contracts.read',      'network',  'عرض العقود',                'View contracts',                FALSE, FALSE),
    ('portal.provider',     'network',  'بوابة مقدم الخدمة',         'Provider portal access',        FALSE, FALSE),
    ('claims.read',         'claims',   'عرض المطالبات',             'View claims',                   FALSE, FALSE),
    ('claims.review',       'claims',   'اعتماد / رفض المطالبات',    'Approve/reject claims',         TRUE,  FALSE),
    ('dashboard.read',      'system',   'لوحة المؤشرات',             'View dashboard',                FALSE, FALSE),
    ('settings.manage',     'system',   'إعدادات النظام',            'Manage system settings',        TRUE,  TRUE),
    ('reports.medical',     'reports',  'التقارير الطبية',           'Medical reports',               FALSE, FALSE),
    ('reports.financial',   'reports',  'التقارير المالية',          'Financial reports',             FALSE, FALSE),
    ('settlements.read',    'finance',  'عرض الدفعات',               'View settlements',              FALSE, FALSE),
    ('settlements.approve', 'finance',  'اعتماد الدفعات',            'Approve settlements',           TRUE,  FALSE)
ON CONFLICT (code) DO NOTHING;

-- ===================== 6) seed role -> permission mapping =====================
-- SUPER_ADMIN is intentionally NOT seeded here: EffectivePermissionService
-- short-circuits SUPER_ADMIN to "all permissions" in code, so its effective
-- set always tracks the full catalog even as new permissions are added later
-- without requiring a fresh migration.

-- WAAD_ADMIN: full operational access. It is NOT excluded from
-- settings.manage at the role-membership level (it needs to operate the
-- system day to day) — the "cannot manage critical security permissions"
-- rule from the ticket is enforced instead at the override-granting layer
-- (a WAAD_ADMIN actor cannot GRANT/REVOKE a critical_security permission via
-- user_permission_overrides for anyone), and at the user-management layer
-- (a WAAD_ADMIN actor cannot touch SUPER_ADMIN accounts at all). See
-- EffectivePermissionService / UserService for the enforcement code.
INSERT INTO role_permissions (role, permission_code) VALUES
    ('WAAD_ADMIN', 'beneficiaries.read'),
    ('WAAD_ADMIN', 'employers.read'),
    ('WAAD_ADMIN', 'providers.read'),
    ('WAAD_ADMIN', 'providers.manage'),
    ('WAAD_ADMIN', 'contracts.read'),
    ('WAAD_ADMIN', 'claims.read'),
    ('WAAD_ADMIN', 'claims.review'),
    ('WAAD_ADMIN', 'dashboard.read'),
    ('WAAD_ADMIN', 'settings.manage'),
    ('WAAD_ADMIN', 'reports.medical'),
    ('WAAD_ADMIN', 'reports.financial'),
    ('WAAD_ADMIN', 'settlements.read'),
    ('WAAD_ADMIN', 'settlements.approve'),

    ('MEDICAL_REVIEWER', 'beneficiaries.read'),
    ('MEDICAL_REVIEWER', 'providers.read'),
    ('MEDICAL_REVIEWER', 'claims.read'),
    ('MEDICAL_REVIEWER', 'claims.review'),
    ('MEDICAL_REVIEWER', 'dashboard.read'),
    ('MEDICAL_REVIEWER', 'reports.medical'),

    ('ACCOUNTANT', 'employers.read'),
    ('ACCOUNTANT', 'providers.read'),
    ('ACCOUNTANT', 'contracts.read'),
    ('ACCOUNTANT', 'dashboard.read'),
    ('ACCOUNTANT', 'reports.financial'),
    ('ACCOUNTANT', 'settlements.read'),
    ('ACCOUNTANT', 'settlements.approve'),

    ('FINANCE_VIEWER', 'dashboard.read'),
    ('FINANCE_VIEWER', 'reports.financial'),
    ('FINANCE_VIEWER', 'settlements.read'),

    ('PROVIDER_STAFF', 'portal.provider'),

    ('EMPLOYER_ADMIN', 'beneficiaries.read'),
    ('EMPLOYER_ADMIN', 'employers.read'),
    ('EMPLOYER_ADMIN', 'dashboard.read'),

    ('DATA_ENTRY', 'beneficiaries.read'),
    ('DATA_ENTRY', 'employers.read'),
    ('DATA_ENTRY', 'providers.read'),
    ('DATA_ENTRY', 'claims.read'),
    ('DATA_ENTRY', 'dashboard.read')
ON CONFLICT DO NOTHING;
