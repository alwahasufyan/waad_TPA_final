-- V94: CLAIM-NUMBERING-1 — official, sequential, per-provider claim reference
--
-- Problem: claims.claim_number was always assigned as literally "CLM-" + database id
-- (ClaimService.java), which is not a stable business reference (exposes internal
-- sequential PK, not searchable/meaningful per provider, and collides in meaning
-- across unrelated screens that separately reconstruct "CLM-" + id client-side).
--
-- This migration:
--   1. creates provider_claim_sequences, a per-provider atomic counter table used
--      with row-level locking (SELECT ... FOR UPDATE) to hand out the next
--      sequence value safely under concurrent claim creation;
--   2. backfills every existing claim with a deterministic reference in the new
--      format CLM-P{providerId:3 digits}-{sequence:6 digits}, ordered by
--      created_at then id per provider (stable, reproducible ordering);
--   3. initializes each provider's next_value to continue right after the
--      highest sequence just assigned during backfill.
--
-- claims.claim_number itself is untouched structurally (already nullable + UNIQUE
-- since V19/V37) — only the VALUES change going forward.

CREATE TABLE provider_claim_sequences (
    provider_id BIGINT PRIMARY KEY,
    next_value  BIGINT NOT NULL DEFAULT 1,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Backfill: assign deterministic, per-provider sequential references to every
-- existing claim that has a provider_id, ordered by created_at then id.
WITH numbered AS (
    SELECT id, provider_id,
           ROW_NUMBER() OVER (PARTITION BY provider_id ORDER BY created_at, id) AS seq
    FROM claims
    WHERE provider_id IS NOT NULL
)
UPDATE claims c
SET claim_number = 'CLM-P' || LPAD(numbered.provider_id::text, 3, '0') || '-' || LPAD(numbered.seq::text, 6, '0')
FROM numbered
WHERE c.id = numbered.id;

-- Initialize each provider's sequence to continue right after the last backfilled value.
INSERT INTO provider_claim_sequences (provider_id, next_value, updated_at)
SELECT provider_id, COUNT(*) + 1, CURRENT_TIMESTAMP
FROM claims
WHERE provider_id IS NOT NULL
GROUP BY provider_id
ON CONFLICT (provider_id) DO UPDATE SET next_value = EXCLUDED.next_value;
