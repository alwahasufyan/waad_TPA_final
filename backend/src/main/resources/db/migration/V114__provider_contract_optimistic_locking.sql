-- WAAD-PROVIDER-CONTRACT-OPTIMISTIC-LOCK-1
--
-- ProviderContract had no @Version column, so two admins editing/activating/
-- suspending the same contract concurrently could silently clobber one
-- another's change (last write wins, no conflict detection). Adds a
-- Hibernate-managed optimistic-lock version column, defaulting existing rows
-- to 0 so no historical data is affected.
ALTER TABLE provider_contracts ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
