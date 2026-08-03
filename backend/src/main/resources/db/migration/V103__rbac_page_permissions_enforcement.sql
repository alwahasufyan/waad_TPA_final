-- WAAD-RBAC-PERMISSION-CATALOG-ENFORCEMENT-1
--
-- V101/V102 built the permission catalog and a matrix UI to edit it, but only
-- the reports.* permissions (V102) were actually wired into frontend
-- enforcement (menu visibility + route guards) — every other page (members,
-- providers, pre-authorizations, medical catalog, benefit policies,
-- settlements, system settings, ...) still relied solely on the static
-- role->resource map in roleAccessMap.js, so toggling those permissions off
-- in the matrix had no real effect. This migration:
--
--   1) adds the 5 permission codes that had no catalog entry at all yet
--      (pre-authorizations, medical catalog, benefit policies, provider
--      accounts/settlement views, documents), so every real page now has a
--      corresponding permission code;
--   2) seeds role_permissions for these 5 codes to exactly mirror each
--      role's CURRENT resource grant in roleAccessMap.js, so this migration
--      changes no one's actual access on its own — it only makes the
--      existing access independently toggleable per role going forward.
--
-- The already-existing 12 non-report permissions (beneficiaries.read,
-- employers.read, providers.read, providers.manage, contracts.read,
-- portal.provider, claims.read, claims.review, dashboard.read,
-- settings.manage, settlements.read, settlements.approve) needed no new
-- catalog rows — only the frontend wiring (menu items + route guards) was
-- missing, done alongside this migration.

INSERT INTO permissions (code, group_name, label_ar, label_en, sensitive, critical_security) VALUES
    ('preauth.read',          'claims',  'عرض طلبات الموافقات المسبقة', 'View pre-authorizations',       FALSE, FALSE),
    ('medical_catalog.read',  'network', 'عرض التصنيف الطبي',           'View medical catalog',          FALSE, FALSE),
    ('benefit_policies.read', 'records', 'عرض وثائق المنافع',           'View benefit policies',         FALSE, FALSE),
    ('provider_accounts.read','finance', 'عرض حسابات مقدمي الخدمة',     'View provider accounts',        FALSE, FALSE),
    ('documents.read',        'records', 'عرض مكتبة المستندات',         'View documents library',        FALSE, FALSE)
ON CONFLICT (code) DO NOTHING;

-- Mirrors WAAD_ADMIN's full grant of pre_auth/medical_catalog/benefit_policies/provider_accounts/documents in roleAccessMap.js.
INSERT INTO role_permissions (role, permission_code) VALUES
    ('WAAD_ADMIN', 'preauth.read'),
    ('WAAD_ADMIN', 'medical_catalog.read'),
    ('WAAD_ADMIN', 'benefit_policies.read'),
    ('WAAD_ADMIN', 'provider_accounts.read'),
    ('WAAD_ADMIN', 'documents.read'),

    -- MEDICAL_REVIEWER: pre_auth + documents (roleAccessMap.js).
    ('MEDICAL_REVIEWER', 'preauth.read'),
    ('MEDICAL_REVIEWER', 'documents.read'),

    -- ACCOUNTANT: documents + provider_accounts (roleAccessMap.js).
    ('ACCOUNTANT', 'documents.read'),
    ('ACCOUNTANT', 'provider_accounts.read'),

    -- EMPLOYER_ADMIN: benefit_policies + documents (roleAccessMap.js).
    ('EMPLOYER_ADMIN', 'benefit_policies.read'),
    ('EMPLOYER_ADMIN', 'documents.read'),

    -- DATA_ENTRY: medical_catalog + documents (roleAccessMap.js).
    ('DATA_ENTRY', 'medical_catalog.read'),
    ('DATA_ENTRY', 'documents.read')
ON CONFLICT DO NOTHING;
