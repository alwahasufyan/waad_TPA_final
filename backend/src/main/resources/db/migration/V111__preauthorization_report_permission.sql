-- WAAD-PREAUTH-REPORT-1
--
-- The new "Pre-Authorization Report" placeholder (REP-PAT-001 in
-- frontend/src/reporting/reportRegistry.js) lives inside the Claims report
-- domain page rather than as its own domain tile. V102's granular report
-- permissions are domain-level only (one permission per REPORT_DOMAINS
-- entry) — this report is intentionally NOT bundled into 'reports.claims'
-- so an admin can grant a MEDICAL_REVIEWER (or any role/user) visibility
-- into this specific report without also granting full claims-report
-- access, per explicit product request.
--
-- Seeding the permission code only (no role_permissions grant) — it becomes
-- available in the permission-management UI to assign per-role or per-user
-- as needed, matching V102's precedent of not auto-granting every domain to
-- every role.
INSERT INTO permissions (code, group_name, label_ar, label_en, sensitive, critical_security) VALUES
    ('reports.preauthorization', 'reports', 'تقرير الموافقات المسبقة', 'Pre-authorization report', FALSE, FALSE)
ON CONFLICT (code) DO NOTHING;
