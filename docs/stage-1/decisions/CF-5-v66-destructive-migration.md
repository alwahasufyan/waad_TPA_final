# Decision Required — CF-5: Destructive V66 Payment-Table Migration (Data-Loss Audit)

**Status:** 🔵 **APPROVED as operational-investigation-only (Stage 1 close-out).** No code change (none is possible/appropriate). Action sits with operations: confirm whether V66 ran against populated production tables and whether a pre-migration backup exists. Recommendation D (ban destructive DDL on populated prod tables) stands as a going-forward rule.
**Severity:** 🔴 Critical (possible historical financial-audit data loss).
**Constitution anchors:** *"Never delete production data through migrations. Never recommend dropping production tables without explicit approval. Never recommend deleting historical records."*

---

## Current Implementation (confirmed by direct inspection)

Migration `V66__recreate_payment_tables.sql` performs a destructive recreate:
```sql
DROP TABLE IF EXISTS payment_audit_logs;
DROP TABLE IF EXISTS payment_records;
CREATE TABLE payment_records ( ... );
CREATE TABLE payment_audit_logs ( ... );
```
This followed `V62__create_payment_records.sql` and `V63__fix_payment_audit_logs_columns.sql` — i.e. the tables were created (V62), a column fix was attempted (V63), and then the pair was **dropped and recreated from scratch** (V66), apparently because the earlier rename/fix did not apply consistently across environments.

## Runtime Behavior

- On any database where V66 ran, `payment_records` and `payment_audit_logs` were dropped and recreated **empty**.
- If those tables were **empty at the time V66 ran** (e.g. the payments feature had not yet been used in that environment), there is **no data loss** — the recreate is harmless.
- If those tables **contained real payment or payment-audit rows** when V66 ran in production, **those rows were permanently destroyed** by the `DROP TABLE`.
- Which of these is true is a historical/operational fact that engineering cannot determine from the code alone — it depends on the state of the production database at the moment V66 was applied.

## Risk

- **Irreversible loss of financial-audit history** (payment records and their audit trail) if V66 ran against populated production tables without a backup.
- **Compliance exposure:** a healthcare TPA is expected to retain a payment audit trail; a gap would be material.

## Business Impact

- **Financial/audit:** potential permanent gap in payment history and payment-change audit trail.
- **Regulatory:** inability to reconstruct historical provider payments for the affected period, if data was lost.
- **Reputational/legal:** payment disputes covering the affected window may be unresolvable from system data.

## Available Options (operational, not code)

| Option | Description | When appropriate |
|---|---|---|
| **A. Confirm no loss** | Verify V66 ran only against empty payment tables (feature unused at that time) | If payments were not yet in production use when V66 was deployed |
| **B. Restore from backup** | Recover pre-V66 `payment_records`/`payment_audit_logs` from a database backup and reconcile | If V66 destroyed real data AND a pre-migration backup exists |
| **C. Accept + document the gap** | Formally record the data-loss window and its bounds; institute compensating controls | If data was lost and no backup exists (last resort) |
| **D. Harden process going forward** | Ban `DROP TABLE` on populated production tables in migrations; require additive-only + explicit approval for any destructive DDL | Regardless of A/B/C — prevents recurrence |

## Recommended Option

1. **Immediately** perform Option A/B fact-finding: determine whether V66 executed against populated production tables and whether a pre-migration backup exists.
2. Adopt **Option D** as a standing rule regardless of outcome — this exact failure pattern (drop/recreate under migration pressure) also occurred earlier in the V26–V45 chain and should be structurally prevented.

## Why This Requires Business/Operational Input

- Engineering **cannot fix or assess this from code** — it is purely a question of what the production database contained when V66 ran, and whether a backup exists. Only the operators/business have that information.
- Any recovery action (restoring from backup, reconciling) touches live financial data and requires explicit authorization.
- Per the Constitution, destructive migration outcomes and any remediation must be handled with explicit approval and full audit — never silently by engineering.
