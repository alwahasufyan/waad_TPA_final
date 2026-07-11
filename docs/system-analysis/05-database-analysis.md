# 05 — Database Analysis

> Source: `backend/src/main/resources/db/migration/` — 65 files, `V1__sequences.sql` through `V67__add_kinship_verified.sql` (some numbers, e.g. V46/V64/V65, are not present as files; comments within later migrations reference an even longer pre-repo history, e.g. "removed in V228," implying these 67 files are themselves a flattened/reset snapshot of a longer evolution). PostgreSQL 16. All analysis reconstructs the **final** schema state, since later migrations frequently `ALTER`/fix earlier ones.

---

## 1. Tables (grouped by domain)

| Domain | Tables |
|---|---|
| Sequences | `user_seq, employer_seq, provider_seq, medical_category_seq, member_seq(unused), provider_contract_seq, benefit_policy_seq, claim_seq, preauth_seq, member_barcode_seq` |
| Employers | `employers` |
| Members | `members, member_attributes, member_deductibles, member_policy_assignments, member_import_logs, member_import_errors` |
| Providers | `providers, provider_allowed_employers, provider_admin_documents, provider_services, medical_reviewer_providers, provider_service_price_import_logs` |
| Contracts / Pricing | `provider_contracts, provider_contract_pricing_items, network_providers, legacy_provider_contracts` |
| Medical Taxonomy | `medical_categories, medical_category_roots` |
| Benefit Policies | `benefit_policies, benefit_policy_rules, benefit_policy_templates, benefit_policy_template_rules` |
| Eligibility | `eligibility_checks` |
| Visits | `visits, visit_attachments` |
| Pre-Authorization | `preauthorization_requests, pre_authorizations, pre_authorization_attachments, pre_authorization_audit, pre_auth_email_requests, pre_auth_email_attachments` |
| Claims | `claim_batches, claims, claim_lines, claim_attachments, claim_history, claim_audit_logs, claim_rejection_reasons, claim_drafts, claim_coverage_rules, claim_rule_execution_audit, medical_audit_logs` |
| Financial / Settlement | `provider_accounts, account_transactions, payment_records, payment_audit_logs` |
| Email / System Config | `email_settings, pre_auth_email_requests, pre_auth_email_attachments, system_settings, feature_flags, module_access, audit_logs, pdf_company_settings` |
| Users / RBAC | `users, email_verification_tokens, password_reset_tokens, user_login_attempts, user_audit_log` |

**~60 tables total.** The two hottest, largest tables by design (row volume and index count) are `claims` and `claim_lines` — both received the heaviest iterative refinement across the migration history.

## 2. Key Table Notes

- **`members`** — self-referencing `parent_id` (family hierarchy), rich CHECK-enforced enums (`relationship`, `gender`, `marital_status`, `status`, `card_status`), unique `card_number`/`barcode`, `kinship_verified` added in the most recent migration (V67).
- **`providers`** — went through the most severe entity/DDL reconciliation of any table (see §9); final shape after V34 drops 22 orphaned columns.
- **`provider_contracts`** — `employer_id` FK was **removed entirely in V34** — contracts are no longer employer-scoped at the DB level, a business-model-relevant change (see `02-business-workflows.md` §1 pain points). Enforces "one active contract per provider" via a genuine partial unique index.
- **`claims`** — 12-value CHECK-enforced `status` enum, 17 targeted indexes (heaviest index investment in the schema — real evidence of production performance tuning), extensive accreted financial columns.
- **`claim_lines`** — heavy use of `*_snapshot` columns (deliberate immutability-at-claim-time design), but comparatively thin indexing (3 indexes) relative to its 40+ columns and central role in financial reconciliation.
- **`eligibility_checks`** — deliberately denormalized (snapshots member/policy/employer names) for audit immutability — a *correct* design choice, not a defect.
- **`medical_audit_logs`** — DB-trigger-enforced immutability (`prevent_medical_audit_logs_mutation()` blocks UPDATE unconditionally; DELETE was blocked until V54 relaxed it for SUPER_ADMIN purges).
- **`account_transactions`** — genuine double-entry-style CHECK constraints (`balance_after = balance_before ± amount` depending on transaction type) — real ledger integrity enforced at the database layer, not just in application code.

## 3. Relationships (ERD, list form)

```
employers 1─* members
employers 1─* provider_allowed_employers *─1 providers
employers 1─* provider_contracts            [FK REMOVED in V34 — now decoupled]
employers 1─* network_providers *─1 providers
employers 1─* benefit_policies
employers 1─* users (optional scope)
employers 1─* claim_batches

providers 1─* provider_services, provider_admin_documents
providers 1─* provider_contracts 1─* provider_contract_pricing_items *─1 medical_categories
providers 1─* medical_reviewer_providers *─1 users (as reviewer)
providers 1─1 provider_accounts 1─* account_transactions
providers ⇢ (unenforced) visits, pre_authorizations, payment_records, legacy_provider_contracts

medical_categories self-ref parent_id; 1─* medical_category_roots (M:N closure table)
medical_categories 1─* benefit_policy_rules, benefit_policy_template_rules, provider_contract_pricing_items

members self-ref parent_id (dependents)
members *─1 employers, *─1 benefit_policies
members 1─* member_attributes, member_deductibles, member_policy_assignments
members 1─* visits, eligibility_checks, preauthorization_requests, claims
members ⇢ (unenforced) pre_authorizations, pre_auth_email_requests

benefit_policies *─1 employers; 1─* benefit_policy_rules

visits *─1 members, *─1 employers; ⇢ (unenforced) providers, medical_categories
visits 1─* visit_attachments, claims

claims *─1 members, providers, visits (all RESTRICT)
claims *─1 preauthorization_requests           [see §7 — likely FK-target mismatch]
claims *─1 claim_batches (SET NULL)
claims 1─* claim_lines, claim_attachments, claim_history, claim_audit_logs
claims 1─* account_transactions (polymorphic reference_id/reference_type — no FK by design, appropriate here)

pre_authorizations ⇢ (unenforced) members, providers, visits, email_requests

users *─1 employers, *─1 providers (scope)
users 1─* email_verification_tokens, password_reset_tokens (CASCADE)
users 1─* claim_drafts *─1 claim_batches

payment_records ⇢ (unenforced) employers, providers; 1─* payment_audit_logs (unenforced FK)
```

## 4. Indexes

**Strong coverage:** `claims` (17 targeted composite/partial indexes — the clear investment priority of the schema), `visits`, `members`, `eligibility_checks`, `account_transactions` all have FK-supporting and query-pattern-matched indexes.

**Gaps:**
- `claim_lines` — a genuinely hot child table with 40+ columns and heavy reporting/reconciliation use — has only **3 indexes** (`claim_id`, `service_code`, composite `service_category_id`/`total_price`). No index on `rejected`, `pricing_item_id`, or `applied_category_id`, all of which are plausible filter/join columns.
- `pre_authorizations` — 3 basic indexes (`member_id`, `provider_id`, `status`) — reasonable given the table also lacks FK constraints, but thin for its transactional importance.
- `payment_records`/`payment_audit_logs` — 2–3 indexes each; acceptable given lower expected volume.
- `user_login_attempts.user_id` — has a supporting index despite **lacking an FK constraint** — an inconsistent design (indexed but not integrity-constrained).

## 5. Constraints

**Business-rule CHECK constraints** (a genuine strength — this schema uses the database as a real integrity backstop, not just an application-trusted store):
- `providers.provider_type IN ('HOSPITAL','CLINIC','LAB','PHARMACY','RADIOLOGY')`, `network_status IN ('IN_NETWORK','OUT_OF_NETWORK','PREFERRED')`.
- `members`: enums for `status`/`card_status`/`gender`/`marital_status`/`relationship`.
- `visits.chk_visit_date_reasonable` / `claims.chk_claim_date`: service dates within a 10-year window and not in the future — **the same rule duplicated independently across two tables** rather than centralized (e.g. a shared domain function).
- `benefit_policy_rules.chk_bpr_coverage_percent` (0–100) — added explicitly (per its migration comment) because "JPA `@Min`/`@Max` enforce this at the application layer, but without a DB constraint a direct INSERT/UPDATE could bypass it" — a deliberate, well-reasoned defense-in-depth decision worth holding up as a model for the rest of the schema.
- `account_transactions.chk_balance_credit`/`chk_balance_debit` — real double-entry ledger integrity; `provider_accounts.chk_balance_non_negative`.
- A whole family of `>= 0` non-negativity checks added in one late pass (V44) across `claims`/`claim_lines` financial columns, and `quantity > 0`.
- Date-range validity (`end_date >= start_date`) is **repeated independently in ~5 tables** (`benefit_policies`, `provider_contracts`, `member_policy_assignments`, `member_deductibles`) rather than enforced via one shared pattern — a normalization-of-rules opportunity, not a normalization-of-data one.

## 6. Naming Conventions

- **Consistent**: `snake_case` throughout, no camelCase leakage.
- **Inconsistent PK generation strategy**: some tables use `BIGINT DEFAULT nextval('xxx_seq')` (employers, providers, medical_categories, benefit_policies, provider_contracts, claims — the earliest, "core master" entities), most others use plain `BIGSERIAL` (visits, claim_lines, preauthorization_requests, payment_records, claim_drafts, etc.). Two ID strategies coexist with no migration path between them (see §10).
- **Inconsistent audit columns**: `created_at`/`updated_at`/`created_by`/`updated_by` used widely but not universally; timestamp typing itself is mixed (`TIMESTAMP` vs `TIMESTAMPTZ` for `medical_audit_logs`/`claim_rejection_reasons`) — a mixed timezone-awareness strategy that could produce subtle bugs if the application ever needs to reason about absolute time across tables consistently.
- **Inconsistent FK naming**: mostly `fk_<child>_<parent>`, but abbreviation style varies (`fk_mrp_reviewer` vs. fully spelled names); `claim_drafts` uses inline unnamed `REFERENCES` instead of the project's usual named `CONSTRAINT fk_...` style.
- **Inconsistent boolean/soft-delete naming**: `active`, `is_active`, `enabled`, `is_default`, `is_smart_card`, `is_vip`, `is_urgent` for "on/off" flags; `deleted` vs. `is_deleted` vs. `deleted_at`-only for "soft delete" — **five different soft-delete patterns coexist** system-wide (full detail below).

## 7. Duplicated Data / Columns (schema-churn scar tissue)

These are **not** deliberate denormalization — they are leftover duplicate-purpose columns from iterative fixes, mostly cleaned up in V34/V40 but some still present:

| Table | Duplicate Pair | Status |
|---|---|---|
| `claims` | `patient_share` vs `patient_copay`; `refused_amount` vs `manual_refused_amount` vs `price_excess_refused`+`limit_refused` | Still present — itemized breakdown is intentional, but naming overlap invites confusion |
| `claim_lines` | `total_amount` vs `total_price`; `unit_price` vs `requested_unit_price`/`approved_unit_price` | Still present |
| `benefit_policy_rules` | `coverage_percentage` vs `coverage_percent`; `max_sessions_per_year` vs `times_limit`; `requires_preauth` vs `requires_pre_approval` | **Resolved** — one of each pair dropped in V34 |
| `employers` | `cr_number` vs `commercial_registration_number` | **Resolved** — latter dropped V34 |
| `email_verification_tokens` / `password_reset_tokens` | `expiry_date` vs `expires_at` | Still present, unclear which is authoritative |
| `user_login_attempts` | `attempt_result` vs `success`; `created_at` vs `attempted_at`; `failure_reason` vs `failed_reason` | **Still present, never reconciled** — two separate implementations merged without cleanup |
| `members` | `civil_id` (deprecated, kept for legacy data) vs `national_number` | Deliberately deprecated-but-kept |

**Also flagged:** `provider_contract_pricing_items.category_name`/`sub_category_name`/`specialty` as free text alongside the `medical_category_id` FK — this one is **intentional, explained denormalization** (V43's comment states the goal was to avoid runtime fuzzy-matching performance costs; the FK was back-filled from the text via exact+fuzzy matching as a one-time reconciliation pass).

## 8. Missing Foreign Keys

Columns that are clearly relational (named `*_id`, used in joins/filters throughout the codebase) but carry **no FK constraint**:

- `visits.provider_id`, `.medical_category_id`, `.medical_service_id`, `.eligibility_check_id`
- `pre_authorizations.member_id`, `.provider_id`, `.visit_id`, `.email_request_id`, `.service_category_id` — **the entire operational pre-auth table has zero FK constraints**
- `pre_authorization_audit.pre_authorization_id`
- `pre_auth_email_requests.provider_id`, `.member_id`, `.detected_service_id` (the last is explicitly commented as intentionally unconstrained because its target table was dropped upstream)
- `claim_lines.pricing_item_id`, `.applied_category_id`, `.service_category_id`
- `claims.reviewer_id`
- `users.company_id` — appears to be an **orphaned column** with no corresponding relationship used elsewhere in the schema
- `user_login_attempts.user_id`
- `payment_records.employer_id`, `.provider_id`
- `payment_audit_logs.payment_id` — notable because V63 explicitly *renamed* a column specifically for this relationship, yet the constraint was never added
- `legacy_provider_contracts.provider_id` — acceptable, given the table's explicit "legacy" status
- `account_transactions.reference_id` — **appropriately** unconstrained (polymorphic, paired with `reference_type`)

This is the single most actionable, low-risk category of database improvement available (see `09-improvement-roadmap.md`): each of these can be added via a new migration with a `NOT VALID` + background `VALIDATE CONSTRAINT` pattern to avoid locking production tables, without touching any application code.

## 9. The V26–V45 "Fix Chain" — Root Cause Analysis

A dominant pattern across ~20 of the 65 migrations: **JPA entity definitions and hand-written early DDL (V2–V24) diverged**, and because Flyway never re-runs applied migrations, every mismatch had to be patched forward. Concretely, in order of frequency:

1. **NOT NULL columns the entity never wrote to** → every INSERT failed. Example: `benefit_policies.policy_name` was NOT NULL while the entity only ever populated `name` — this took **two migration attempts** (V26, then V27) because V26 had already been recorded as "applied" in `flyway_schema_history` in some environments with different content, and `validate-on-migrate=false` silently ignored the checksum mismatch — V27's own header explicitly documents this as a Flyway operational lesson.
2. **Sequence-missing-on-old-databases**: `member_barcode_seq` was added mid-evolution of V1; environments provisioned before that point lacked it, breaking member import — defensively re-created with `IF NOT EXISTS` three separate times (V1, V26, V27).
3. **Column-name mismatches between JPA `@Column(name=...)` and actual DDL**: most severe on `providers` (entity expected `name/phone/email/tax_number/...`, DB had `provider_name/contact_phone/...` — a near-total naming mismatch), also `provider_admin_documents` and `provider_contracts` (~10 entirely missing columns for the modern contract entity).
4. **CHECK constraints out of sync with Java enums**: `provider_type` missing `'OTHER'` vs. the enum having it (later the enum was narrowed instead); `benefit_policies.status` CHECK allowed only 2 of 5 enum values; `account_transactions.reference_type` CHECK **omitted `CLAIM_REVERSAL`/`CLAIM_SETTLEMENT`**, which silently broke claim reversal/settlement in production until V45.
5. **Bidirectional NOT NULL mismatches** — sometimes the DB was too strict (blocking valid application inserts), sometimes too loose (allowing data the entity's `@Column(nullable=false)` should have prevented).
6. **V34 "production cleanup"** is the capstone: a large migration systematically dropping every column with no corresponding entity field across 6 tables (23 columns total, 22 from `providers` alone) — its own header states the guiding principle plainly: *"every column without an entity field is dropped."* This is the point at which the schema stabilizes; migrations after V34/V35 are genuine feature additions, not reconciliation.
7. A **smaller recurrence of the same disease** at V62→V63→V66 (payment tables): V63 attempted a column rename that didn't fully/consistently apply; V66 resorted to a **destructive `DROP TABLE`+`CREATE TABLE` recreate** of both `payment_records` and `payment_audit_logs` — a data-loss-risking pattern in a financial-audit table, and the most recent instance of this failure mode (see `08-technical-debt.md` for severity ranking).

**Lesson for ATEF**: this history is not a criticism of the team — the bilingual, explanatory comments throughout show real diagnostic discipline once problems surfaced in production. The lesson is structural: **schema and entity definitions must be validated against each other before merge**, not discovered via production 500 errors. See `09-improvement-roadmap.md` for a concrete recommendation (CI-level schema/entity consistency check).

## 10. Sequence / ID Strategy

Two competing strategies coexist with no unification path:
- **Hibernate-style sequences**, increment-by-50 (allocation batching): `users, employers, providers, medical_categories, provider_contracts, benefit_policies, claims` — the earliest "core master" entities.
- **Plain `BIGSERIAL`** (increment-by-1): the majority of tables added from V4 onward, including some conceptually "master" tables like `visits`, `preauthorization_requests`, `claim_batches`.
- **`members` is a special case**: `member_seq` is defined (increment-by-50) but the `members` table itself uses `BIGSERIAL`, never referencing the sequence — a dead, unused sequence object left in the schema.
- **`member_barcode_seq`** is a distinct-purpose sequence (increment-by-1, application-consumed directly for barcode generation, not a table PK default) — a legitimately different usage pattern, not a defect.

## 11. Soft-Delete Pattern Inconsistency

**Five different patterns** coexist for the same underlying concept ("this row is logically gone but retained"):

1. **Full triad** (`deleted` + `deleted_at` + `deleted_by`): `medical_categories`.
2. **`deleted` boolean only**, deliberately decoupled from `active`: `benefit_policy_rules` (added V47, with an explicit backfill comment noting pre-existing inactive rows were retroactively treated as deleted).
3. **`deleted_at`/`deleted_by` only, no boolean**, coexisting with an *independent* `active` boolean: `claims` — meaning a claim can be `active=false` without `deleted_at` being set, and vice versa, with **no CHECK constraint enforcing any relationship between the two flags**. This is the highest-stakes instance of the pattern given it's on the financial core table.
4. **`is_deleted` boolean only** (different naming convention): `payment_records`.
5. **`active` boolean only, no soft-delete concept**: employers, providers, provider_contracts, network_providers, members, visits, benefit_policies (uses `status` enum instead), users (`enabled`/`is_active`).
6. **Hard delete via CASCADE, appropriately**: attachment/history/child tables that should always disappear with their parent.

**Recommendation for ATEF**: standardize on pattern (1) — the full `deleted`/`deleted_at`/`deleted_by` triad — for any table holding data with legal/financial retention implications, and add a CHECK constraint on `claims` specifically to enforce consistency between `active` and `deleted_at` (or retire one of the two flags).

## 12. Potential Improvements (database-specific, non-disruptive)

1. Add the missing FK constraints enumerated in §8 via `NOT VALID` + background `VALIDATE CONSTRAINT` — zero application-code risk, closes real referential-integrity gaps.
2. Add supporting indexes to `claim_lines` (`rejected`, `pricing_item_id`, `applied_category_id`) given its reporting/reconciliation load.
3. Resolve the `claims.pre_authorization_id → preauthorization_requests` vs. `pre_authorizations` question (see `03-module-catalog.md` §preauthorization) — this may be a live bug, not just a documentation gap, and deserves priority investigation.
4. Reconcile or explicitly document the duplicate columns in §7 that remain unresolved (`user_login_attempts`, `expiry_date`/`expires_at`).
5. Add a CHECK constraint (or a generated column) tying `claims.active` and `claims.deleted_at` together consistently.
6. Consider a single shared SQL domain/function for date-range validity (`end_date >= start_date`) instead of five independent copies of the same CHECK expression.
7. Investigate whether the `member_seq` sequence object can be safely dropped (confirmed unused) as routine cleanup.
8. Introduce a lightweight CI check (schema-vs-entity diff, e.g. via a test that boots the Hibernate `SchemaValidator` against a real migrated database) to prevent a recurrence of the V26–V45 fix-chain pattern — this is the single highest-leverage process improvement suggested anywhere in this database analysis.

---

*Continue to [`06-ui-ux-audit.md`](./06-ui-ux-audit.md) for the frontend surface that consumes this schema via the API catalog above.*
