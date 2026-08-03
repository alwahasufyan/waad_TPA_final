-- WAAD-RBAC-REPORT-PERMISSIONS-GRANULARITY-1
--
-- Follow-up to V101 (flagged in the WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1
-- report §11 as a known future need): the two bundled report permissions
-- (`reports.medical`, `reports.financial`) do not let an admin grant a role
-- access to one specific report domain (e.g. MEDICAL_REVIEWER should be
-- assignable "claims reports" without also getting "audit reports").
--
-- This migration replaces those two bundled permissions with one permission
-- per real report domain, matching REPORT_DOMAINS in
-- frontend/src/reporting/reportRegistry.js 1:1 (10 domains). Report-level
-- (not domain-level) granularity is intentionally out of scope for now —
-- reportRegistry.js has several `status: 'planned'` (unbuilt) reports, and a
-- per-report permission would be premature until each report actually ships.

-- ===================== 1) seed the 10 granular report permissions =====================
INSERT INTO permissions (code, group_name, label_ar, label_en, sensitive, critical_security) VALUES
    ('reports.claims',                'reports', 'تقارير المطالبات',           'Claims reports',              FALSE, FALSE),
    ('reports.members',               'reports', 'تقارير المستفيدين',          'Members reports',             FALSE, FALSE),
    ('reports.employers',             'reports', 'تقارير جهات العمل',          'Employers reports',           FALSE, FALSE),
    ('reports.providers',             'reports', 'تقارير مقدمي الخدمة',        'Providers reports',           FALSE, FALSE),
    ('reports.contracts',             'reports', 'تقارير العقود',              'Contracts reports',           FALSE, FALSE),
    ('reports.price_lists',           'reports', 'تقارير قوائم الأسعار',       'Price lists reports',         FALSE, FALSE),
    ('reports.benefit_policies',      'reports', 'تقارير وثائق المنافع',       'Benefit policies reports',    FALSE, FALSE),
    ('reports.financial_settlements', 'reports', 'تقارير التسويات المالية',    'Financial settlements reports', FALSE, FALSE),
    ('reports.audit',                 'reports', 'تقارير التدقيق',             'Audit reports',               FALSE, FALSE),
    ('reports.system_analytics',      'reports', 'تقارير إحصائيات النظام',     'System analytics reports',    FALSE, FALSE)
ON CONFLICT (code) DO NOTHING;

-- ===================== 2) re-map roles that had the old bundled permissions =====================
-- WAAD_ADMIN: full operational access, gets every report domain.
INSERT INTO role_permissions (role, permission_code) VALUES
    ('WAAD_ADMIN', 'reports.claims'),
    ('WAAD_ADMIN', 'reports.members'),
    ('WAAD_ADMIN', 'reports.employers'),
    ('WAAD_ADMIN', 'reports.providers'),
    ('WAAD_ADMIN', 'reports.contracts'),
    ('WAAD_ADMIN', 'reports.price_lists'),
    ('WAAD_ADMIN', 'reports.benefit_policies'),
    ('WAAD_ADMIN', 'reports.financial_settlements'),
    ('WAAD_ADMIN', 'reports.audit'),
    ('WAAD_ADMIN', 'reports.system_analytics'),

    -- MEDICAL_REVIEWER previously had the bundled 'reports.medical'; the
    -- closest granular equivalent to "medical reports" is claims + medical
    -- audit reports (matches its existing claims.review / audit surface).
    ('MEDICAL_REVIEWER', 'reports.claims'),
    ('MEDICAL_REVIEWER', 'reports.audit'),

    -- ACCOUNTANT previously had the bundled 'reports.financial'; closest
    -- granular equivalent is settlements + pricing reports (matches its
    -- existing settlements.read/approve + contracts.read surface).
    ('ACCOUNTANT', 'reports.financial_settlements'),
    ('ACCOUNTANT', 'reports.price_lists'),

    -- FINANCE_VIEWER previously had the bundled 'reports.financial'; it is a
    -- read-only role, so it keeps only settlements reports (no pricing).
    ('FINANCE_VIEWER', 'reports.financial_settlements')
ON CONFLICT DO NOTHING;

-- ===================== 3) remove the old bundled permissions =====================
DELETE FROM role_permissions WHERE permission_code IN ('reports.medical', 'reports.financial');
DELETE FROM permissions WHERE code IN ('reports.medical', 'reports.financial');
