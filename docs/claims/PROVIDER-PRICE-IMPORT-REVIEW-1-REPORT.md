# PROVIDER-PRICE-IMPORT-REVIEW-1 — Block Ambiguous Provider Price List Rows from Financial Use

**Status: READY FOR REVIEW.** Local only. Nothing pushed. Nothing committed (per standing rule).

## 1. Problem (from DOCTOR-NOTES-BENEFIT-CAPS-REGRESSION-1, §4/§9)

`ProviderContractPricingExcelService` silently proceeded when a row's category code/name couldn't be matched (`assignedCategory == null`), inserting/updating the pricing item anyway with no category, no flag, no rejection. The same gap existed in the manual (non-Excel) create/update API — despite a comment claiming "medical category required since V229," the code never enforced it. A pricing item with no category has no `BenefitPolicyRule` for `CoverageEngineService` to resolve at claim time — an unpredictable financial outcome with zero review gate.

## 2. What was implemented

**Design inspiration only** from `OmarAfoshAhmad/waad_sofyan_final`'s `ClassificationStatus`/`ConfidenceLevel`/`requiresReview` fields (inspected read-only last session, nothing copied) — simplified to exactly what this ticket needs: a boolean `requiresReview` + a human-readable `reviewReason`, not the reference branch's full confidence-level/classification-source/approver workflow (explicitly out of scope).

### Backend

- **`ProviderContractPricingItem.java`** — new `requiresReview` (boolean, default false) and `reviewReason` (varchar 500) fields.
- **`V99__provider_pricing_item_review_flag.sql`** (new) — adds the two columns, a partial index on `requires_review = TRUE`, and backfills `requiresReview = TRUE` for any pre-existing active row with `medical_category_id IS NULL` (retroactive — old unresolved rows don't get a free pass).
- **`ProviderContractPricingExcelService.importRules()`** — a row whose category code/name couldn't be resolved now sets `requiresReview = true` with a specific Arabic reason (distinguishes "code/name given but unmatched" vs "no category info given at all"). The import summary/message now reports a `pendingReview` count and a clear warning line when non-zero.
- **`ProviderContractPricingItemService.create()`/`update()`** — the same gate applies to manual creation (no category → flagged); `update()` clears the flag automatically the moment a real category is assigned.
- **`ClaimMapper.processEngineCalculations()`** — the single enforcement point: resolving a claim line's pricing item now throws `BusinessRuleException` immediately if `requiresReview = true`, **before** the coverage engine is ever consulted (verified via `verifyNoInteractions(coverageEngineService)` in the new test) — a flagged item can never reach financial approval, matching the ticket's core requirement.
- **`ExcelImportResultDto.ImportSummary`** — new `pendingReview` counter field (additive, other importers using this shared DTO are unaffected).
- **`ProviderContractPricingItemResponseDto`** — exposes `requiresReview`/`reviewReason` so any consumer (API client, future UI) can see it.
- New endpoint **`GET /api/v1/provider-contracts/{contractId}/pricing/pending-review`** — lists a contract's unresolved pricing items (the "review queue"), backing repository queries added to `ProviderContractPricingItemRepository`.

### Explicitly not touched (per the ticket's scope limits)

- No benefit ledger (`BENEFIT-CAP-LEDGER-1`, separate ticket).
- No employer-scoped contracts (`PROVIDER-CONTRACT-EMPLOYER-SCOPE-1`, separate ticket).
- No dental service-level coverage (`DENTAL-SERVICE-LEVEL-COVERAGE-1`, separate ticket, pending business confirmation).
- No `NOT NULL` constraint added to `medical_category_id` — that would be a breaking schema change; the review flag is the additive, backward-compatible gate instead.

### Frontend / import UI — a finding, not a fix

Grepped the entire frontend for the existing upload function (`uploadContractPricingExcel`) and its template-download sibling: **neither is called from any page.** The provider price-list Excel import feature has no wired UI today — it's reachable only via direct API calls. This means "the import UI must show unmatched rows clearly" is satisfied at the **API level** (the import response's `summary.pendingReview` count + warning message, plus the new pending-review list endpoint) since there is no existing UI screen to enhance. Building an actual import screen is a separate, larger frontend ticket (not opened here — flag if wanted).

## 3. Regression tests added (all passing)

- `ClaimMapperPricingContractTest.pricingItemRequiringReview_isRejectedBeforeReachingCoverageEngine` — asserts the exact Arabic message and that `CoverageEngineService` is never invoked.
- `ClaimMapperPricingContractTest.pricingItemNotRequiringReview_stillWorksNormally` — regression guard that ordinary items are unaffected.
- `ProviderContractPricingItemServiceTest.create_withoutMedicalCategory_shouldFlagRequiresReview`
- `ProviderContractPricingItemServiceTest.create_withMedicalCategory_shouldNotRequireReview`
- `ProviderContractPricingItemServiceTest.update_assigningCategory_shouldClearRequiresReview`

`mvn -o test` (full suite) → all pass, exit 0. `mvn -o compile` clean.

## 4. Live smoke test (local Docker, rebuilt with these changes)

1. `POST /provider-contracts/1/pricing` with no `medicalCategoryId` → response: `"requiresReview":true,"reviewReason":"لم يتم تحديد تصنيف طبي عند الإنشاء اليدوي"`.
2. `GET /provider-contracts/1/pricing/pending-review` → the item appears.
3. `POST /claims` using that pricing item → **blocked**: `"تعذر استخدام هذه الخدمة لأنها بانتظار المراجعة (تصنيف طبي غير محدد). يرجى مراجعة قائمة الأسعار أولاً."` — confirmed via the exact intended message, not a generic error (the earlier session's `GlobalExceptionHandler` unwrapping fix correctly surfaces it).
4. `PUT /provider-contracts/pricing/4` with a real `medicalCategoryId` → `"requiresReview":false,"reviewReason":null`.
5. Retried the same claim creation → the review-gate error was gone (a different, unrelated, pre-existing "Requested amount must be greater than zero" error surfaced instead, tied to that specific test category/member policy combination — out of scope for this ticket, not caused by this change).
6. Smoke-test pricing item deleted (soft delete) to leave the dev DB clean.

## 5. Recorded follow-up tickets (not implemented)

1. `BENEFIT-CAP-LEDGER-1` — port a benefit-bucket-style consumption ledger to close the annual-limit timing gap and unify the two divergent usage-accumulation status filters.
2. `TAXONOMY-INPATIENT-SURGERY-1` — live-DB check: confirm whether a distinct inpatient-surgery category exists separate from bed accommodation.
3. `CLAIMS-USAGE-QUERY-CONSISTENCY-1` — reconcile `BenefitPolicyCoverageService.validateAmountLimits()`'s status filter (APPROVED/SETTLED/BATCHED only) against `BenefitPolicyRuleService.checkUsageLimit()`'s status filter (excludes only REJECTED) — two different definitions of "already used" today.
4. `ANNUAL-LIMIT-PRECHECK-1` — enforce the annual-limit check at claim submission/creation, not only at approval, to close the parallel-submission race window.
5. `DENTAL-SERVICE-LEVEL-COVERAGE-1` — only if the business confirms per-procedure dental coverage percentages are genuinely required (neither this codebase nor the reference branch supports it today; a new design, not a restoration).
6. New, found this ticket: `PROVIDER-PRICE-IMPORT-UI-1` — build an actual price-list-import screen; the backend feature (template download + Excel upload + now the review queue) currently has zero frontend UI wired to it.
7. Minor, found this ticket: `ProviderContractPricingItemService`'s comment "Resolve medical category (required since V229)" is stale/aspirational like the `findBestRuleForService()` docstring found in the previous ticket — the code has never actually enforced it. Worth a documentation pass across the codebase for other "since VNNN" comments that don't match real enforcement.

## 6. Confirmations

- Reference branch was used only as design inspiration (a simpler two-field gate, not the reference's full classification/confidence/approver model) — no code copied.
- Benefit ledger, employer-scoped contracts, and dental service-level coverage were explicitly NOT implemented in this ticket.
- No push was done.

---

**PROVIDER-PRICE-IMPORT-REVIEW-1 READY FOR REVIEW**
