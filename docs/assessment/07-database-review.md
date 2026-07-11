# 07 — Database Review

**Score: 65/100 (C)** · Reviewed against `.claude/reviews/database-review.md.txt` and `.claude/standards/database-standards.md.txt`. Source: 65 Flyway migrations (`V1`–`V67`), PostgreSQL 16, ~60 tables.

---

## Data Model

| Check | Status | Evidence |
|---|---|---|
| Tables represent the Domain Model | ✓ | Clear domain grouping (employers, members, providers, contracts, taxonomy, benefit policies, eligibility, visits, pre-auth, claims, settlement, RBAC) |
| No duplicated entities | ⚠ | `legacy_provider_contracts` alongside `provider_contracts`; two pre-authorization tables (`preauthorization_requests`, `pre_authorizations`) whose relationship is itself an open question (CF-4) |
| Proper normalization | ⚠ | Mostly sound; some unresolved duplicate-purpose columns remain (below) |
| No unnecessary denormalization | ✓ (mostly intentional) | `eligibility_checks` and `claim_lines.*_snapshot` denormalization is explained and correct (audit immutability) |
| Correct naming conventions | ⚠ | `snake_case` consistent; PK-generation strategy is not (see Keys) |

## Keys

| Check | Status | Evidence |
|---|---|---|
| Primary Keys | ✓ | All tables have PKs |
| Foreign Keys | ⚠ | **Significant gaps** — see Relationships below |
| Unique Constraints | ✓ | Good coverage (`employers.code`, `members.card_number`/`barcode`, `provider_contracts` one-active-per-provider partial index) |
| Composite Keys | ✓ | Used appropriately for junction tables (`provider_allowed_employers`, `benefit_policy_template_rules`) |
| Natural Keys only when justified | ✓ | `employers.code`, `medical_categories.code` are reasonable business identifiers |

**PK generation strategy is inconsistent**: some core tables use Hibernate-style sequences with increment-by-50 batching (`employers, providers, medical_categories, provider_contracts, benefit_policies, claims`), most others use plain `BIGSERIAL`. `member_seq` is defined but never actually used — `members` uses `BIGSERIAL` directly, a dead schema object.

## Relationships

**This is the database's weakest checklist area.** Confirmed missing FK constraints on clearly relational columns:
- **Entire `pre_authorizations` operational table** — `member_id`, `provider_id`, `visit_id`, `email_request_id`, `service_category_id` all unconstrained.
- `visits.provider_id`, `.medical_category_id`, `.medical_service_id`, `.eligibility_check_id`
- `claim_lines.pricing_item_id`, `.applied_category_id`, `.service_category_id`
- `claims.reviewer_id`
- `payment_records.employer_id`, `.provider_id`; `payment_audit_logs.payment_id`
- `users.company_id` (appears orphaned — no corresponding relationship used elsewhere)
- `user_login_attempts.user_id`

**Per the Constitution's Database Principles ("Every schema change must preserve Referential Integrity")**, this is a genuine gap — referential integrity for these relationships currently relies entirely on application-layer discipline, not the database. Cascade rules are correctly used where they do exist (`CASCADE` for genuinely child-owned data like attachments, `RESTRICT` for master-data references, `SET NULL` for optional links).

## Constraints

| Check | Status | Evidence |
|---|---|---|
| NOT NULL | ✓ (after V34 reconciliation) | Now aligned with entity `@Column(nullable=false)` |
| CHECK Constraints | ✓ Strong | Genuine business-rule enforcement: non-negativity family (V44), enum validity, coverage-percent 0–100, date-range validity, double-entry ledger balance checks on `account_transactions` |
| UNIQUE Constraints | ✓ | Well applied |
| FK Constraints | ⚠ | See Relationships above |
| Business Constraints | ✓ | The `benefit_policy_rules.chk_bpr_coverage_percent` migration comment explicitly reasons about defense-in-depth ("JPA annotations enforce this at the app layer, but without a DB constraint a direct INSERT/UPDATE could bypass it") — this is exactly the right instinct and should be the template applied to close the FK gaps above.

## Audit

| Check | Status | Evidence |
|---|---|---|
| Created At / By | ✓ (mostly) | Present on most tables |
| Updated At / By | ⚠ | Not universal — e.g. `claim_lines` has `created_by` but no `updated_by`/`updated_at` |
| Deleted At / By | ⚠ | Present on some tables, absent on others — see Soft Delete |

Timestamp typing is also inconsistent (`TIMESTAMP` vs `TIMESTAMPTZ` mixed across tables) — a latent risk if the application ever needs to reason about absolute time consistently across tables.

## Soft Delete

Per the Constitution ("*Never physically delete Claims, Financial Records, Settlements, Audit Logs*"), soft-delete discipline on these specific tables is **critical**, and it is **inconsistently implemented**:

| Pattern | Tables |
|---|---|
| Full triad (`deleted`+`deleted_at`+`deleted_by`) | `medical_categories` |
| `deleted` only, decoupled from `active` | `benefit_policy_rules` |
| `deleted_at`/`deleted_by` only, alongside an *independent* `active` boolean with no CHECK tying them together | **`claims`** — the highest-stakes instance, since this is exactly the table the Constitution names explicitly |
| `is_deleted` only | `payment_records` |
| `active`-only, no soft-delete concept | employers, providers, provider_contracts, members, **visits** |

**Constitution violation risk:** `VisitService.delete()` performs a genuine hard delete (see CF-2), and `visits` has no soft-delete columns at all to fall back on — this table needed the Constitution's "never physically delete Financial Records" protection (since Visits cascade-own Claims) and does not have it structurally.

## Indexes

| Check | Status | Evidence |
|---|---|---|
| FK-supporting indexes | ⚠ | Strong on `claims` (17 targeted indexes — genuine production tuning evidence), thinner on `claim_lines` (only 3, despite 40+ columns and heavy reporting use — no index on `rejected`, `pricing_item_id`, `applied_category_id`) |
| Search-field indexes | ✓ | `members` has a well-designed composite partial index (`employer_id, civil_id, full_name WHERE active=true`) |
| Status/date indexes | ✓ | Present on hot tables |
| Unnecessary indexes | Not observed | No evidence of index bloat |

## Performance

Covered in depth in `19-performance-review.md`. Headline: the DB-side SUM aggregation for benefit-limit calculation (replacing an earlier documented O(n) in-memory approach) is real, evidenced performance engineering. `claim_lines`' thin indexing relative to its reporting/reconciliation load is the standout gap.

## Migrations

| Check | Status | Evidence |
|---|---|---|
| Incremental | ✓ | Sequential V1–V67 |
| Repeatable | ✓ | Standard Flyway versioned migrations |
| Reversible | ⚠ | No explicit down-migrations found (common for Flyway, but worth confirming a rollback strategy exists for the team, per Constitution: "*every migration must support rollback planning*") |
| Safe for production | ⚠ | **Confirmed exception: V66** (see CF-5) — a `DROP TABLE`/`CREATE TABLE` recreate of `payment_records`/`payment_audit_logs`, directly contradicting the Constitution's "*never recommend dropping production tables... never recommend deleting historical records*" principle |
| Old migration files not modified | ✓ (assumed, standard Flyway discipline — not independently verified via version control history in this pass) | |

**The V26–V45 migration chain** (20+ migrations) exists almost entirely to reconcile JPA entity definitions against hand-written early DDL that had drifted apart — missing columns, mismatched CHECK constraint values, orphaned columns. This is now **stabilized** (V34 was the capstone reconciliation), but it is direct historical evidence that entity/schema consistency was not being verified before migrations shipped for an extended period. The bilingual, explanatory comments throughout this chain show real diagnostic discipline once problems surfaced — the lesson is to catch this class of bug *before* production, not to criticize the recovery.

## Data Integrity

| Check | Status | Evidence |
|---|---|---|
| No duplicate records | ⚠ | `MemberDuplicateController`/`KinshipMismatchController` exist specifically because duplicate member records are a known, ongoing operational problem — not fully solved at the schema level |
| Referential integrity maintained | ⚠ | See Relationships — application-layer only for several key tables |
| Transactions used correctly | ✓ | `@Transactional`, `@TransactionalEventListener(phase=AFTER_COMMIT)` used correctly for financial event handling |
| Consistent data types | ✓ (mostly) | Timestamp-type mixing noted above is the one confirmed inconsistency |

## Naming Standards

`snake_case` is consistent throughout. FK constraint naming is mostly `fk_<child>_<parent>` but abbreviation style varies, and `claim_drafts` uses inline unnamed `REFERENCES` rather than the project's usual named-constraint style — a minor but real consistency gap.

## Security (Database)

| Check | Status | Evidence |
|---|---|---|
| Sensitive columns encrypted if needed | Not assessed | Outside this pass's scope — recommend explicit verification for `users` PII columns |
| Least privilege database access | Not assessed | Infrastructure-level |
| No secrets stored in tables | ✓ | Confirmed no plaintext secrets found in schema |

---

## Findings Requiring Action

1. **(Critical, CF-4)** Resolve the `preauthorization_requests` vs. `pre_authorizations` FK-target question.
2. **(Critical, CF-5)** Confirm V66 did not destroy production payment-audit history.
3. **(Critical, CF-2 root cause)** Add soft-delete columns to `visits` (currently has none) as part of fixing the hard-delete cascade.
4. **(High)** Add the missing FK constraints via `NOT VALID` + background `VALIDATE CONSTRAINT` — zero-downtime, zero application-code risk, closes a real Constitution-relevant integrity gap.
5. **(High)** Add a CHECK constraint (or consolidate to one flag) tying `claims.active` and `claims.deleted_at` together — the highest-stakes instance of the soft-delete inconsistency.
6. **(Medium)** Add indexes to `claim_lines` for `rejected`, `pricing_item_id`, `applied_category_id`.
7. **(Low)** Introduce a CI-level schema/entity consistency check (boot Hibernate's schema validator against a freshly-migrated test database) to prevent a recurrence of the V26–V45 pattern.

## Decision

**⚠ Changes Required.** The database demonstrates real constraint discipline where the team invested in it (financial CHECK constraints, the double-entry ledger, the well-reasoned coverage-percent defense-in-depth comment) — this is the standard the FK-gap remediation should be held to. The historical churn (V26–V45) is fully stabilized and should not be treated as an ongoing risk; the open items above are.

---

*Continue to [`08-backend-review.md`](./08-backend-review.md).*
