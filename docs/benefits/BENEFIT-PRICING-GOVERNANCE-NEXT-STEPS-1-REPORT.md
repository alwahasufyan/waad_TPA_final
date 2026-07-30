# BENEFIT-PRICING-GOVERNANCE-NEXT-STEPS-1 — Recommended Next Steps for Benefit Documents / Provider Price Lists / Caps / Classification

**Planning/recommendation only. No code changed. Nothing committed. Nothing pushed.**

## 1. Executive recommendation

The single highest-financial-risk gap (ambiguous price-list rows silently entering financial approval) is **closed** — `PROVIDER-PRICE-IMPORT-REVIEW-1` is implemented, tested, and live-verified. Nothing else found across the two prior reports rises to the same severity.

**Recommendation: this area is safe enough to ship as-is.** Exactly one follow-up is a genuine production blocker, and it's a data/config check, not a code change: confirm the review-gate doesn't retroactively lock out pricing items your team is *already* actively using (§3). Everything else below is real, worth doing, but not a gate on shipping what exists today.

## 2. Current status after PROVIDER-PRICE-IMPORT-REVIEW-1

| Capability | Status |
|---|---|
| Annual member limit enforcement | Real, works, only at approval (not submission) |
| Category caps (MRI/delivery/physio/dental ops) | Confirmed correct by regression tests |
| Cap usage accumulation across claims | Real (not a stub), two queries use different status filters |
| Admission days × quantity | Correct |
| Cap-exhausted flooring | Correct |
| Dental service-level % | Not supported (category-only, deliberate since "V228") |
| Ambiguous/unmatched price-list rows | **Now blocked from financial use** (this ticket) |
| Price-list import review queue | Backend exists (`GET .../pricing/pending-review`); **no frontend screen** |
| Benefit policy/rule bulk Excel import | **Does not exist at all** (confirmed again this pass — no backend or frontend files) |
| Inpatient surgery vs accommodation taxonomy | Unconfirmed — needs a live DB check, not code |
| Benefit cap consumption ledger (bucket-style) | Does not exist; reference branch has one |

## 3. Production blockers

**One**, and it's not a code gap — it's a verification step:

> The `V99` migration backfills `requiresReview = TRUE` for every existing active pricing item with `medical_category_id IS NULL`. If any of those rows are **currently in active use** by providers submitting real claims, they will suddenly be blocked the moment this migration runs in a shared/production environment. Before running V99 anywhere beyond local dev, run a read-only query (`SELECT COUNT(*) FROM provider_contract_pricing_items WHERE medical_category_id IS NULL AND active = TRUE`) against that environment and review the list. If the count is non-trivial, categorize those rows *before* the migration runs, or the review gate will cause real claim-submission failures on day one instead of preventing new ones.

Nothing else here blocks shipping.

## 4. Recommended next tickets, in priority order

### Must do before production
1. **`PRICING-REVIEW-BACKFILL-CHECK-1`** (see §3) — not a ticket so much as a pre-flight step: run the backfill query against staging/production data before V99 goes out, and pre-resolve any real in-use rows it would catch.

### Should do soon after
2. **`PROVIDER-PRICE-IMPORT-UI-1`**
   - **Why:** the review gate exists but nobody can *see or fix* a flagged row without a database console. Providers/admins have no way to resolve a blocked pricing item today except via raw API calls.
   - **Risk addressed:** operational — flagged rows pile up invisibly, providers get blocked and don't know why to fix it.
   - **Scope:** a review-queue table (backed by the existing `GET .../pricing/pending-review` endpoint) with an inline "assign category" action (backed by the existing `PUT .../pricing/{id}`); plus wiring the *already-built* upload/template-download functions (`uploadContractPricingExcel`, `downloadPricingTemplate`) into an actual import screen, since neither is called from anywhere today.
   - **Backend/Frontend:** frontend only — no backend changes needed, everything it needs already exists.
   - **Blocks production:** no — the backend gate protects the money either way; this is about making the fix *usable*, not making the system *safe*.

3. **`CLAIMS-USAGE-QUERY-CONSISTENCY-1`**
   - **Why:** `BenefitPolicyCoverageService.validateAmountLimits()` counts APPROVED/SETTLED/BATCHED only; `BenefitPolicyRuleService.checkUsageLimit()` excludes only REJECTED (so it also counts DRAFT/SUBMITTED/UNDER_REVIEW/APPROVAL_IN_PROGRESS). Two different definitions of "how much has this member already used."
   - **Risk addressed:** confusing/inconsistent cap-exceeded messages (a claim looks blocked by one check and not the other); not a silent-overpayment bug, but a real correctness gap.
   - **Scope:** small — pick one status set (recommend: match the stricter annual-limit filter, since counting pending/unapproved claims against a cap is the more conservative and arguably correct choice) and use it in both queries. No schema change.
   - **Backend/Frontend:** backend only.
   - **Blocks production:** no.

4. **`ANNUAL-LIMIT-PRECHECK-1`**
   - **Why:** the annual limit is only enforced at final approval; a provider filling out a claim gets no warning until much later in the workflow.
   - **Risk addressed:** UX/workflow, not financial — the real approval-time check already prevents overpayment; this is about surfacing the problem earlier, not fixing a money bug.
   - **Scope:** add a read-only "would exceed annual limit" warning at claim submission/review time (reuse the existing `validateAmountLimits` query, called in warn-only mode — do **not** duplicate the throwing check, keep approval as the single atomic source of truth per the ticket's own instruction).
   - **Backend/Frontend:** both (a warning endpoint/field + a UI banner).
   - **Blocks production:** no.

5. **`TAXONOMY-INPATIENT-SURGERY-1`**
   - **Why:** doctor's notes suspect accommodation and surgery rules get conflated; code inspection found the engine itself keeps categories separate correctly, but the *standard seed taxonomy* has no distinct inpatient-surgery category — only `CAT-IP-GEN`/`CAT005` (accommodation) and `CAT015` (outpatient surgery).
   - **Risk addressed:** if real inpatient surgery claims exist and are mapped to the accommodation category for lack of an alternative, they silently share its cap/coverage rule.
   - **Scope:** **investigation, not code** — query the real `medical_categories` table in the actual environment, confirm with the business whether inpatient surgery needs its own cap/coverage, and only then decide whether to add a category + rule (a config change, not a code change) or confirm the shared rule is intentional.
   - **Backend/Frontend:** neither, if the answer is "shared rule is fine"; a config/data change if not.
   - **Blocks production:** no, but worth doing before any inpatient-surgery-heavy provider goes live, since the cost of finding out via a wrong payment is higher than a 30-minute DB check.

### Optional / future
6. **`BENEFIT-CAP-LEDGER-1`** — port a bucket-style consumption ledger. Real value (idempotent commit/reverse, closes the approval-timing race more robustly, unifies the accumulation query in one place) but a genuinely larger, riskier change (new tables, new migration sequence, touches the approval flow). Current system's cap enforcement already works correctly for every tested scenario; this is a robustness/architecture upgrade, not a bug fix. Revisit if #3 and #4 together aren't judged sufficient, or if a real double-approval race is ever actually observed in production.

7. **`DENTAL-SERVICE-LEVEL-COVERAGE-1`** — only if the business explicitly confirms per-procedure dental percentages (not per-category) are a real requirement. This is a new capability neither this codebase nor the reference branch has ever supported — do not build speculatively.

8. **`BENEFIT-POLICY-RULE-EXCEL-IMPORT-1`** — bulk Excel import for category-level coverage rules (coverage%, waiting period, pre-approval) doesn't exist in our system at all today; benefit rules can only be created one-by-one through the UI/API. The reference branch has a simple version of this (`BenefitPolicyRuleExcelService`). Worth building **only if** the business is manually creating/updating a large number of rules often enough that one-by-one entry is a real bottleneck — no evidence of that pain point in the doctor's notes or either prior report. Low urgency.

## 5. What not to do now

- Do not port the reference branch's full `BenefitLimitBucket`/`BenefitRuleBucket`/`BenefitBucketConsumption` system wholesale — it's real, working design in that branch, but it's a bigger change than anything actually proven necessary yet (see #6).
- Do not add employer-scoped provider contracts speculatively — no evidence in either report that today's one-price-list-per-provider model is the actual root cause of any specific reported bug (it was a hypothesis, not a confirmed finding).
- Do not build dental service-level coverage without an explicit business requirement — it doesn't exist in the reference branch either, so there's no "restore what was removed" shortcut; it would be new design work.
- Do not add a `NOT NULL` constraint on `provider_contract_pricing_items.medical_category_id` — the `requiresReview` flag is the correct additive, backward-compatible gate; a hard constraint would be a breaking schema change for no additional safety benefit.
- Do not build the benefit-rule bulk Excel importer speculatively (see #8) — build it when there's a real volume-driven need, not because the reference branch happens to have one.

## 6. Suggested final acceptance checklist for this area

- [ ] Pre-flight query run against the real target environment before `V99` deploys beyond local dev (§3).
- [ ] `PROVIDER-PRICE-IMPORT-UI-1` shipped, so flagged rows are actually visible/fixable by non-engineers.
- [ ] `CLAIMS-USAGE-QUERY-CONSISTENCY-1` shipped, so "how much has this member used" has one answer everywhere.
- [ ] `ANNUAL-LIMIT-PRECHECK-1` shipped, so a provider sees a warning before spending time on a claim that will fail at approval.
- [ ] `TAXONOMY-INPATIENT-SURGERY-1` investigated and closed one way or the other (either a dedicated category exists/was added, or the shared-rule behavior is confirmed intentional).
- [ ] Decision recorded (even if "not now") on the three optional tickets, so they don't silently fall off the radar.

## 7. No-code-change confirmation

No source files, migrations, or configuration were modified during this ticket — recommendation/planning only.

## 8. No-commit/no-push confirmation

Nothing was staged, committed, or pushed.

---

**BENEFIT-PRICING-GOVERNANCE-NEXT-STEPS-1 READY FOR DECISION**
