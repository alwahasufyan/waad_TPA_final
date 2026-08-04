-- WAAD-PREAUTH-MULTI-LINE-1 (Phase 1)
--
-- PreAuthorization was strictly one-service-per-record: a provider
-- submitting one request covering two services had no way to represent that
-- as one record, so the frontend looped and created two unrelated
-- pre-authorization numbers from a single user action. This migration adds
-- pre_authorization_lines (mirrors claim_lines/ClaimLine exactly) so one
-- PreAuthorization header can hold N service lines.
--
-- Phase 1 is purely additive: no existing column on pre_authorizations is
-- dropped, renamed, or reinterpreted. Every existing row is backfilled into
-- exactly one line (line_number = 1) below, so pre_authorization_lines has
-- row-count parity with pre_authorizations immediately after this runs.
-- Reading/writing the new table is not yet wired into any service-layer
-- logic (Phase 2) — this migration only creates the capability.

CREATE TABLE pre_authorization_lines (
    id                              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pre_authorization_id            BIGINT NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    line_number                     INTEGER NOT NULL DEFAULT 1,

    -- No FK on pricing_item_id — intentional snapshot decoupling, same
    -- convention as claim_lines.pricing_item_id (see
    -- V68__add_safe_referential_integrity_fks.sql).
    pricing_item_id                 BIGINT,
    service_code                    VARCHAR(50)  NOT NULL,
    service_name                    VARCHAR(200),
    service_type                    VARCHAR(100) NOT NULL DEFAULT 'MEDICAL',
    service_category_id             BIGINT NOT NULL,
    service_category_name           VARCHAR(200),

    -- No quantity/unit-price split: PreAuthorization.contractPrice has always
    -- been the full requested amount for one service (no quantity field ever
    -- existed on the header), so each line's contract_price is a line total.
    contract_price                  NUMERIC(10,2) NOT NULL,
    requires_pa                     BOOLEAN NOT NULL DEFAULT TRUE,

    coverage_percent_snapshot       INTEGER,
    patient_copay_percent_snapshot  INTEGER,

    approved_amount                 NUMERIC(10,2),
    copay_amount                    NUMERIC(10,2) DEFAULT 0,
    copay_percentage                NUMERIC(5,2)  DEFAULT 0,
    insurance_covered_amount        NUMERIC(10,2),
    reserved_amount                 NUMERIC(15,2) DEFAULT 0,

    -- Schema-ready for Phase 2's per-line approve/reject/clarify workflow —
    -- not written by any service-layer code yet.
    reviewer_decision               VARCHAR(30),
    rejection_reason                VARCHAR(500),

    created_at                      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMP,

    CONSTRAINT fk_preauth_line_header FOREIGN KEY (pre_authorization_id)
        REFERENCES pre_authorizations(id) ON DELETE CASCADE
);

CREATE INDEX idx_preauth_line_header ON pre_authorization_lines(pre_authorization_id);
CREATE INDEX idx_preauth_line_category ON pre_authorization_lines(service_category_id);

-- Backfill: every existing header row becomes exactly one line.
INSERT INTO pre_authorization_lines
  (pre_authorization_id, line_number, pricing_item_id, service_code, service_name,
   service_type, service_category_id, service_category_name, contract_price,
   requires_pa, coverage_percent_snapshot, patient_copay_percent_snapshot,
   approved_amount, copay_amount, copay_percentage, insurance_covered_amount,
   reserved_amount, created_at)
SELECT
   id, 1, pricing_item_id, service_code, service_name, service_type, service_category_id,
   service_category_name, contract_price, requires_pa,
   coverage_percent_snapshot, patient_copay_percent_snapshot, approved_amount, copay_amount,
   copay_percentage, insurance_covered_amount, reserved_amount, COALESCE(created_at, now())
FROM pre_authorizations;
