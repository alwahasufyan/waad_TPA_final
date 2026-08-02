-- WAAD-RBAC-PER-USER-LANDING-PAGE-1
--
-- Per-user configurable post-login landing page (admin-set only, this phase).
-- Stores the route path (e.g. "/dashboard") a user should land on after
-- login instead of the static role-based default. The permission code that
-- gates that route is NOT persisted here — it lives only in the frontend
-- menu definition (menu-items/components.jsx), the single source of truth
-- for "which permission does this page need". The backend only ever
-- validates a submitted (route, permission) pair against the user's real
-- effective permissions at save time (see UserService.applyDefaultLandingPage);
-- it never needs to know what a route "means".

ALTER TABLE users ADD COLUMN IF NOT EXISTS default_landing_page VARCHAR(255);
