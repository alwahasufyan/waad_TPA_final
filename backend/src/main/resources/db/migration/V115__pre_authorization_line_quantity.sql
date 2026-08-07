-- WAAD-PREAUTH-LINE-QUANTITY-FIX-1
--
-- CRITICAL FINANCIAL BUG: pre_authorization_lines had no quantity column.
-- The provider-portal submission form (ProviderPreApprovalSubmission.jsx)
-- always displayed and let providers enter a quantity (e.g. "5 sessions")
-- and even showed a live "price x quantity" preview, but the quantity was
-- never sent as a real request field the backend understood — only
-- appended as free text into the notes field. PreAuthorizationService
-- always set contract_price = pricingItem.contractPrice directly (the
-- per-unit catalog price), silently treating every line as quantity=1
-- regardless of what the provider actually requested. A provider entering
-- quantity=2 for a 500 LYD service saw a "1000" preview but the system
-- persisted 500 with no error, no warning, and no trace of the requested
-- quantity anywhere.
--
-- This migration adds the missing quantity/unit_price columns so the line
-- total (contract_price) can be correctly computed as unit_price *
-- quantity, mirroring claim_lines' existing quantity/unit_price/total_price
-- pattern (see V20__claim_lines.sql).
--
-- Backfill: every existing row was always created with the old
-- (quantity-blind) code path, i.e. quantity was always effectively 1 and
-- contract_price already equals the correct per-unit price for that case.
-- So unit_price = contract_price and quantity = 1 for all existing rows —
-- this is not a guess, it is the only value consistent with how every row
-- currently in this table was actually created.
ALTER TABLE pre_authorization_lines ADD COLUMN IF NOT EXISTS quantity INTEGER NOT NULL DEFAULT 1;
ALTER TABLE pre_authorization_lines ADD COLUMN IF NOT EXISTS unit_price NUMERIC(10,2);

UPDATE pre_authorization_lines SET unit_price = contract_price WHERE unit_price IS NULL;

ALTER TABLE pre_authorization_lines ALTER COLUMN unit_price SET NOT NULL;
