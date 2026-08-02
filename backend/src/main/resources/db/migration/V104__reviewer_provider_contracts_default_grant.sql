-- WAAD-RBAC-REVIEWER-PROVIDER-SCOPING-2
--
-- MEDICAL_REVIEWER already had providers.read (V101) but not contracts.read,
-- and the backend previously didn't even let MEDICAL_REVIEWER call the
-- provider-contracts endpoints at all (@PreAuthorize excluded the role).
-- Now that ProviderController properly scopes a reviewer's provider/contract
-- access to their assigned providers (ReviewerProviderIsolationService), it's
-- safe to grant contracts.read by default so a reviewer can see their
-- assigned provider(s)' contracts out of the box, without an admin having to
-- discover and toggle it in the Roles & Permissions matrix first.

INSERT INTO role_permissions (role, permission_code) VALUES
    ('MEDICAL_REVIEWER', 'contracts.read')
ON CONFLICT DO NOTHING;
