-- WAAD-RBAC-USERS-ROLES-PERMISSIONS-COMPLETION-1
--
-- user_audit_log only stored user_id/performed_by as bare FKs, so the admin
-- "سجل تغييرات الصلاحيات" screen had to resolve usernames via a live JOIN
-- against `users` at READ time. That silently breaks the moment an account
-- is deleted (falls back to showing a bare numeric ID) — the wrong design
-- for an audit trail, which should show what the username WAS at the time
-- of the event, permanently, regardless of what happens to the account
-- afterward. Snapshotting the username at WRITE time fixes this for good.

ALTER TABLE user_audit_log ADD COLUMN IF NOT EXISTS username VARCHAR(150);
ALTER TABLE user_audit_log ADD COLUMN IF NOT EXISTS performed_by_username VARCHAR(150);

-- Backfill existing rows from the current users table (best-effort — rows
-- whose user has since been deleted stay NULL, same as today's behavior,
-- but every row going forward is snapshotted at write time).
UPDATE user_audit_log ual
SET username = u.username
FROM users u
WHERE ual.user_id = u.id AND ual.username IS NULL;

UPDATE user_audit_log ual
SET performed_by_username = u.username
FROM users u
WHERE ual.performed_by = u.id AND ual.performed_by_username IS NULL;
