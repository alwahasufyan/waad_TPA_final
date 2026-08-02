-- WAAD-RBAC-EMPLOYER-PERMISSIONS-MIGRATION-1
--
-- AuthorizationService.canEmployerViewMembers()/canEmployerViewBenefitPolicies()
-- used to gate EMPLOYER_ADMIN visibility on the standalone
-- users.can_view_members / users.can_view_benefit_policies boolean columns,
-- edited via a dedicated toggle box on the user-edit page. That box is
-- removed (per-user permission management now lives exclusively in the
-- "الصلاحيات الخاصة للمستخدمين" admin tab), and AuthorizationService now
-- checks the effective-permission codes "beneficiaries.read" /
-- "benefit_policies.read" instead (both already granted to EMPLOYER_ADMIN by
-- default via V101/V103's role_permissions seed).
--
-- This is a one-time backfill: any EMPLOYER_ADMIN who currently has either
-- toggle set to FALSE gets an equivalent active REVOKE override inserted, so
-- their existing restriction survives the cutover instead of silently
-- reverting to "allowed" (the role's default grant). Users who never had the
-- toggle turned off need no row — they simply keep inheriting the role's
-- default GRANT, exactly as before.
--
-- The audited user_permission_overrides.granted_by column is NOT NULL, so a
-- real user id is required as the "actor" for this system-generated
-- override; the oldest SUPER_ADMIN account is used (falling back to the
-- oldest account of any type if none exists, which should never happen in a
-- bootstrapped WAAD_ADMIN/SUPER_ADMIN installation).

DO $$
DECLARE
    system_actor_id BIGINT;
BEGIN
    SELECT id INTO system_actor_id FROM users WHERE user_type = 'SUPER_ADMIN' ORDER BY id LIMIT 1;
    IF system_actor_id IS NULL THEN
        SELECT id INTO system_actor_id FROM users ORDER BY id LIMIT 1;
    END IF;

    IF system_actor_id IS NOT NULL THEN
        INSERT INTO user_permission_overrides (user_id, permission_code, effect, reason, granted_by, created_at)
        SELECT u.id, 'beneficiaries.read', 'REVOKE',
               'ترحيل تلقائي عند إلغاء صندوق «صلاحيات مخصصة لمستخدم الشريك»: كانت can_view_members=false لهذا المستخدم (WAAD-RBAC-EMPLOYER-PERMISSIONS-MIGRATION-1)',
               system_actor_id, CURRENT_TIMESTAMP
        FROM users u
        WHERE u.user_type = 'EMPLOYER_ADMIN' AND u.can_view_members = FALSE;

        INSERT INTO user_permission_overrides (user_id, permission_code, effect, reason, granted_by, created_at)
        SELECT u.id, 'benefit_policies.read', 'REVOKE',
               'ترحيل تلقائي عند إلغاء صندوق «صلاحيات مخصصة لمستخدم الشريك»: كانت can_view_benefit_policies=false لهذا المستخدم (WAAD-RBAC-EMPLOYER-PERMISSIONS-MIGRATION-1)',
               system_actor_id, CURRENT_TIMESTAMP
        FROM users u
        WHERE u.user_type = 'EMPLOYER_ADMIN' AND u.can_view_benefit_policies = FALSE;
    END IF;
END $$;
