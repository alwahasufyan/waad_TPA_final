# CLASSIFICATION-PRICE-LIST-FULL-STABILIZATION-1 — Full Stabilization Report

**Status: READY FOR REVIEW.** All in-scope fixes applied and verified locally. Nothing committed. Not pushed.

## 1. Executive summary

The classification/price-list engine did **not** need a rebuild — every defect found across this and the three prior tickets (`CLASSIFICATION-PRICE-LIST-WIP-REVIEW-1`, `CLASSIFICATION-KNOWLEDGE-TAXONOMY-VALIDATION-1`, `CLASSIFICATION-KNOWLEDGE-GENERATOR-FIX-1`) was a targeted, fixable bug in WIP code, not a structural problem with the engine itself. This ticket:

- Fixed the Add-Service payload regression in `ContractPriceEditDialogs.jsx` (it now sends exactly what the backend accepts, and the file is byte-identical to `HEAD` again — the bug was purely an uncommitted working-tree regression, already reverted).
- Confirmed and documented the `ContractPriceListTab.jsx` Add-button enablement change (`!active` → `!contractId`) is backend-safe, so it is kept, not reverted.
- Re-verified `review/index.jsx` and `classification.service.js` against all 10 required safety criteria — no new issues found, no changes needed.
- Confirmed the `build_product_kb.py` generator fix from the prior ticket remains intact.
- Added one new focused backend unit test (`ContractPriceEditServiceAddServiceTest`, 4 cases) that pins the exact request contract the frontend now depends on, so a future silent DTO/field-name drift fails a test instead of breaking Add-Service in production again.
- Left every generated knowledge/taxonomy artifact unstaged, per explicit scope.

## 2. Was the engine repaired or rebuilt?

**Repaired.** No file was deleted or rewritten from scratch. Every change in this ticket is a small, targeted diff against existing, otherwise-sound code:
- `ContractPriceEditDialogs.jsx`: a 6-line payload object reverted to match the backend DTO (net effect: file is now identical to the last committed version).
- `build_product_kb.py`: already fixed in the prior ticket; re-verified, not re-touched.
- One new test file added; no production logic files were rewritten.

## 3. Files inspected

- `frontend/src/components/classification/ContractPriceEditDialogs.jsx`
- `frontend/src/components/classification/ContractPriceListTab.jsx`
- `frontend/src/pages/classification/review/index.jsx`
- `frontend/src/services/api/classification.service.js`
- `frontend/src/services/api/provider-contracts.service.js` (`addPriceListService` — read-only, comment already accurate)
- `backend/src/main/java/com/waad/tba/modules/providercontract/dto/ContractPriceEditDtos.java`
- `backend/src/main/java/com/waad/tba/modules/providercontract/controller/ContractPriceEditController.java`
- `backend/src/main/java/com/waad/tba/modules/providercontract/service/ContractPriceEditService.java`
- `tools/classification-engine/build_product_kb.py`
- `backend/src/main/java/com/waad/tba/modules/medicalclassification/pricelist/controller/PriceListReviewController.java`
- `backend/src/main/java/com/waad/tba/modules/medicalclassification/pricelist/entity/PriceListImportLine.java`

## 4. Add-Service payload — root cause and fix

Unchanged root cause from `CLASSIFICATION-PRICE-LIST-WIP-REVIEW-1`, re-confirmed against the current backend source: `AddServiceRequest` (`ContractPriceEditDtos.java:24-30`) is

```java
public record AddServiceRequest(
        String serviceCode,
        @NotBlank String serviceName,
        @NotNull Long categoryId,
        Long medicalServiceId,
        @NotNull BigDecimal price,
        @NotBlank String reason) {}
```

`ContractPriceEditService.addService()` checks, in order: `req.price() == null` → `req.serviceName()` blank → `req.categoryId() == null` → `requireReason(req.reason())`. The WIP payload (`medicalCategoryId`/`basePrice`/`contractPrice`/`currency`/`notes`) left every one of these `null`, guaranteeing a `ValidationException` on the very first check for every submission.

**Fix applied** in `ContractPriceEditDialogs.jsx`'s `AddServiceDialog.save()`:

```js
await addPriceListService(contractId, {
  serviceCode: form.serviceCode.trim() || null,
  serviceName: form.serviceName.trim(),
  categoryId: form.category.id,
  medicalServiceId: form.linked?.id || null,
  price: Number(form.price),
  reason: form.reason.trim()
});
```

This is the same field set the file used before the WIP regression — confirmed by `git diff` showing **zero remaining diff** against `HEAD` for this file after the fix.

Requirement checklist:
1. ✅ Sends the DTO the backend currently accepts (`categoryId`/`price`/`reason`).
2. ✅ No renaming to hypothetical future DTO fields.
3. ✅ No `basePrice`/`contractPrice` sent — backend's `price` field only.
4. ✅ No `notes` sent — backend's `reason` field only.
5. ✅ Arabic labels (`التصنيف / الفئة`, `السعر`, `السبب`, etc.) untouched.
6. ✅ Local validation already present and unchanged: blocks submit unless `serviceName` non-blank, `category` selected, `price > 0`, `reason` non-blank (`save()` guard clauses, lines 159-162) — so `req.price()`/`req.categoryId()`/`req.reason()` can never be null for a form that passes local validation.
7. ✅ Backend rejection surface unchanged: `catch (err) { setError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل إضافة الخدمة'); }` already shows the backend's real error message (Arabic first, English fallback) rather than a generic message.
8. ✅ Confirmed via the new backend test (§9) that a request built the way the dialog now builds it does not trigger `req.price() == null`.

## 5. `ContractPriceListTab.jsx` decision

**Decision: keep the WIP's `!contractId` behavior (do not revert to `!active`).** Verified directly against backend code, not guessed:

- `ContractPriceEditService.addService()` (`ContractPriceEditService.java:78-144`) never checks for an active price-list version before allowing an add. `activeVersionId(contractId)` (line 223-227) is a plain `Optional...orElse(null)` — it returns `null`, and the service happily persists the new `ProviderContractPricingItem` with `versionId(null)` if there's no active version. No exception path exists for "no active version."
- Therefore requiring `active` (the on-screen "has an active version" indicator, `summary?.activeVersion`) to be truthy before allowing Add Service would be **stricter than the backend actually requires** — a UI-only restriction with no backend justification, and the original `!active` gate was arguably an overly-conservative leftover, not a documented product requirement.

No code change needed here (the WIP diff already reflects the backend-correct behavior); this section documents why it is being kept rather than reverted, per the ticket's explicit decision rule ("use actual backend capability, not guess").

## 6. Classification review UI fixes

Re-verified `review/index.jsx` and `classification.service.js` against Phase 4's 10-point checklist. **No changes were required** — every item was already correct (confirmed independently in `CLASSIFICATION-PRICE-LIST-WIP-REVIEW-1` and re-confirmed here against the current, unchanged file state):

1. ✅ `TRUSTED → PENDING_BULK`, `UNRESOLVED → NEEDS_REVIEW` mapping matches `PriceListImportLine.ReviewStatus { PENDING_BULK, NEEDS_REVIEW, APPROVED, REJECTED }` exactly (`review/index.jsx:102-106`).
2. ✅ No UI-only `DecisionLevel` label (`TRUSTED`/`REVIEW`/`UNRESOLVED`) is ever sent to the backend — the translation table intercepts it at the boundary.
3. ✅ Same as #1.
4. ✅ Duplicates tab calls `classificationService.getReviewQueue(importId, 'DUPLICATE', ...)` → `GET /{importId}/review/queue/DUPLICATE`; `DUPLICATE` is one of the four queues `PriceListReviewController` accepts (`Set.of("UNKNOWN", "LOW_CONFIDENCE", "DUPLICATE", "GUARD")`, `PriceListReviewController.java:43-44`) — no 400 risk.
5. ✅ `submitDecision()` builds `{ action, categoryId, serviceId, note, expectedVersion }`, matching `decideLine`/`decideBulk`'s documented payload shape and the optimistic-lock `rowVersion` field.
6. ✅ `if (saving) return;` guards `submitDecision()` before `setSaving(true)`.
7. ✅ `row.reviewStatus !== 'APPROVED' && row.reviewStatus !== 'REJECTED'` (plus the existing `userFacingStatus` checks) gate both the inline price editor and the approve button — reads the real persisted `reviewStatus` field (`PriceListImportLineDto.java:31,58`).
8. ✅ `if (action === 'APPROVE' && tab === 'NEEDS_REVIEW') setTab('APPROVED')`, followed by `await refresh()`.
9. ✅ Neither file references `odoo_knowledge.json`, `official_taxonomy.json`, or any TAX-1 code — they operate entirely through `/lines`, `/review/summary`, `/review/queue/{queue}`, `/review/lines/{id}/decide` against the DB-backed `ReviewStatus` enum, independent of the Python knowledge-generation pipeline.
10. ✅ Status values sent are always either `undefined` (ALL tab) or a valid `ReviewStatus` enum name — no invalid value can reach the backend.

**Conclusion: these two files are safe, independent of the TAX-1/generated-file work, and require no further changes in this ticket.**

## 7. Generator bug — root cause and fix

Unchanged from `CLASSIFICATION-KNOWLEDGE-GENERATOR-FIX-1`; re-verified intact in the current working tree (`build_product_kb.py:64-79`):

1. **Root cause**: `classify(name, category)` searched `normalize(f"{name} {category}")` — the raw Odoo folder text was concatenated into the same match string as the service name, so any product filed under a folder containing a `CAT-ROOM` keyword (`"room"`, `"ward"`, `"إقامة"`, `"اقامة"`, `"غرفة"`, `"جناح"`) was mislabeled regardless of what the product actually was. Measured impact: 4,417 of 11,415 entries (38.7%) mislabeled `CAT-ROOM`, including drugs, stents, and surgeries.
2. **Fix**: keyword rules now search `normalize(name)` only; the Odoo category is consulted solely through the existing `CATEGORY_FALLBACKS` exact-match dict, never as free text.
3. **Second bug found and fixed in the same pass**: raw substring matching let the two-letter keyword `"ct"` match inside unrelated words (`Bactec`, `conjunctival`, `Procalcitonin`). Fixed with space-padded word-boundary comparison.
4. **Metadata preserved**: each entry now also carries `"odooCategory": category` for provenance, with zero influence on the classification decision.

Regenerated-candidate validation (from the prior ticket, using the real raw Odoo export `متغير المنتج (product.product).xlsx`, never committed):
- `CAT-ROOM` entries: 4,417 (38.7%, contaminated with drugs/surgeries) → 52 (2.3%, genuine room/admission items only).
- 30-item samples across drugs, surgeries, imaging (CT/MRI), X-ray, labs, and room/admission all reviewed clean, with one flagged genuine source-data ambiguity (`BT/CT` — "Clotting Time" vs. "CT scan," an inherent two-letter abbreviation collision in the source data itself, not a matching-logic defect).

**No further generator changes were required or made in this ticket** — re-validated the fix is intact and sufficient.

## 8. Generated knowledge file decision

**Unchanged: still not safe to commit, and not touched in this ticket.**

- `tools/classification-engine/odoo_knowledge.json` (Codex's buggy regeneration, working tree) — left exactly as found; not overwritten with a fixed candidate; not staged.
- `tools/classification-engine/official_taxonomy.json` — left untracked, not staged.
- `tools/classification-engine/odoo_knowledge.legacy.json` — left untracked (Codex's own pre-regeneration backup; not needed by any code path).
- Confirmed (prior ticket): `odoo_knowledge.json` is not read by any pipeline script — the live engine consults the already-committed `official_knowledge.json` instead. This remains true; nothing in this ticket changes what the live pipeline reads.

## 9. TAX-1/DB alignment status

No new audit work was performed in this ticket beyond re-confirming the prior findings still hold (files unchanged since the last audit):

- Live artifact used by the engine: `official_knowledge.json` (tracked, unmodified, 4,983 entries, already using TAX-1 `CAT-*` codes).
- `odoo_knowledge.json` is not read by the live pipeline (generator output only).
- 13 of TAX-1's 33 categories (39%) match a current `medical_categories` row by canonicalized name; the other 20 are legitimate finer splits of combined DB categories with no matching row yet.
- `official_knowledge.json` itself resolves at ~37.0% by the same name-based mechanism — this gap is pre-existing and already live in production, not introduced by any WIP file.
- Proposed mapping table (Option B, from `CLASSIFICATION-KNOWLEDGE-GENERATOR-FIX-1-REPORT.md` §6) remains the recommended next step — 25 of 33 TAX-1 codes mapped to a specific DB fallback with a stated confidence level, 4 (`CAT-ICU`, `CAT-CCU`, `CAT-ANESTHESIA`, `CAT-DAY-CARE`) explicitly left unmapped rather than guessed, since no existing DB category is a defensible semantic match for them.
- **No migration was added in this ticket.** Per the ticket's own instruction, this remains proposed for a dedicated follow-up: **`TAX1-TO-WAAD-CATEGORY-MAPPING-1`**.

## 10. `requiresReview`/`NEEDS_REVIEW` safety confirmation

Re-confirmed by reading the unchanged Java source (no backend financial/claim file was modified in this ticket):

- `ImportProcessingService.java:152-169`: any classified line whose category doesn't resolve gets `flags.add("CATEGORY_UNRESOLVED")`, which forces `band = NEEDS_REVIEW` regardless of confidence — unaffected by anything in this ticket.
- `ProviderContractPricingItem.requiresReview` (from `PROVIDER-PRICE-IMPORT-REVIEW-1`, `V99__provider_pricing_item_review_flag.sql`): any pricing item without a resolved `medical_category_id` is flagged `requiresReview = true`.
- `ClaimMapper.processEngineCalculations()` throws `BusinessRuleException` immediately when `requiresReview = true`, **before** `CoverageEngineService` is ever consulted (per that ticket's own test, `verifyNoInteractions(coverageEngineService)`).

None of these gates were touched, weakened, or bypassed by any change in this ticket. The Add-Service payload fix (§4) does not interact with `requiresReview` at all — a manually-added service via this dialog always supplies a real `categoryId` (form validation requires a selected category), so it is never subject to the unresolved-category path in the first place.

## 11. Tests/build results

```
cd backend
mvn -o compile                                            → BUILD SUCCESS

mvn -o test -DskipTests=false \
  -Dtest="*Classification*,*PriceList*,*ProviderContract*,*ProviderContractPricing*"
  → Tests run: 7, Failures: 0, Errors: 0 (ProviderContractPricingItemServiceTest — pre-existing)
    (no other test class in the repo currently matches this glob; see note below)

mvn -o test -DskipTests=false -Dtest="ContractPriceEditServiceAddServiceTest"
  → Tests run: 4, Failures: 0, Errors: 0 (new test, added this ticket)

cd ../frontend
npx vite build                                             → succeeded (chunk-size advisory only, pre-existing)

npx eslint src/pages/classification/review/index.jsx \
  src/services/api/classification.service.js \
  src/components/classification/ContractPriceEditDialogs.jsx \
  src/components/classification/ContractPriceListTab.jsx
  → 0 errors, 56 warnings (all CRLF/prettier line-ending noise, pre-existing repo-wide, no real lint issues)
```

**Test coverage note**: no pre-existing test class covers `ContractPriceEditService`/`ContractPriceEditController` (the classes actually touched by this ticket's root-cause fix) or the classification-review backend controllers under the `*Classification*,*PriceList*,*ProviderContract*` glob. This is a pre-existing gap, not something this ticket introduced. Per "add focused backend tests if feasible," one new test class (`ContractPriceEditServiceAddServiceTest`, 4 cases) was added specifically to pin the exact request-contract fix in §4/§9, so a future accidental field-name drift (the same class of bug this ticket fixed) fails a test immediately instead of silently breaking Add-Service again.

## 12. Browser/API smoke results

**Not performed — no browser or live-API automation is available in this environment** (consistent with this session's standing constraints). The 13-step manual/browser smoke checklist in the ticket (open review page, test tabs, approve/reject a controlled line, add a service, reload, confirm unresolved routing, confirm `requiresReview` blocking) was **not executable here**. In its place:

- Steps 2-8 (trusted/unresolved/duplicate filters, approve/reject, no-re-approve, tab switch) are covered by the static code-path verification in §6, cross-referencing the exact backend enum values and endpoint contracts — not a substitute for a live click-through, but a direct trace of the same code paths a browser session would exercise.
- Steps 9-11 (add service with valid category/price, backend receives `categoryId`/`price`/`reason`, reload shows the item) are covered by the new backend unit test (§9/§11), which exercises the real `ContractPriceEditService.addService()` method with the exact payload shape the fixed dialog now sends.
- Steps 12-13 (unresolved row → review, `requiresReview` blocks claims) are covered by re-reading the unchanged, already-tested `ImportProcessingService`/`ClaimMapper` code paths (§10) — not re-executed, since no financial code was touched.

If a live/manual smoke test is required before final sign-off, it should be run separately (dev server + browser), since it is genuinely outside what this environment can execute.

## 13. Remaining follow-up tickets

- **`TAX1-TO-WAAD-CATEGORY-MAPPING-1`** — implement the Option B mapping table (§9) as an explicit, reviewed second-chance lookup in `CategoryResolutionService`, closing the ~37% resolution gap that already affects the live `official_knowledge.json` today.
- A decision on whether/how to regenerate `odoo_knowledge.json`/`official_knowledge.json` using the fixed `build_product_kb.py`, once the mapping/migration question above is resolved.
- Backend test coverage for `ContractPriceEditController`/`ContractPriceEditService`'s other operations (`correctPrice`, `deactivateService`, `correctClassification`) — only `addService` was covered in this ticket, scoped to the specific regression being fixed.
- The previously-catalogued, unrelated Codex WIP clusters (Reports Engine v2, System Categories redesign, Settings module reorg, Visit provider-isolation, horizontal-nav cleanup, `waad.ps1`) remain untouched and are explicitly out of this ticket's scope.

## 14. Files safe to commit

- `frontend/src/components/classification/ContractPriceListTab.jsx` — the `!contractId` Add-button behavior, now backed by the explicit backend-verified rationale in §5.
- `backend/src/test/java/com/waad/tba/modules/providercontract/service/ContractPriceEditServiceAddServiceTest.java` — new, passing, focused test.
- `docs/classification/CLASSIFICATION-PRICE-LIST-FULL-STABILIZATION-1-REPORT.md` — this report.

Note: `ContractPriceEditDialogs.jsx` needs **no commit** — the fix restored it to be byte-identical to the currently-committed `HEAD`, confirmed via `git diff` showing zero remaining changes. `review/index.jsx` and `classification.service.js` were re-verified but not modified in this ticket; they remain whatever state the prior `PREAUTH`/`RBAC`-era commits left them in (already tracked as separate WIP from earlier tickets, not newly introduced here) — no new changes to stage from this ticket for those two files.

## 15. Files explicitly not safe to commit

- `tools/classification-engine/odoo_knowledge.json` — regenerated diff still not validated against DB taxonomy (§8, §9).
- `tools/classification-engine/official_taxonomy.json` — not explicitly approved.
- `tools/classification-engine/odoo_knowledge.legacy.json` — backup scratch file, not needed.
- `tools/classification-engine/build_product_kb.py` — the fix itself is correct and low-risk, but per this ticket's "commit only after final review/approval" work mode, left for explicit approval alongside the rest of the commit plan rather than staged unilaterally.

## 16. Unsafe files never to stage

- `tools/classification-engine/__pycache__/`
- `"للمرافق معالجة اكسيل  سكربت/"` directory (contains a `.venv/` and loose `.py`/`.pyc` scratch files)
- Any loose `.xlsx` in `tools/classification-engine/` (already correctly `.gitignore`d)
- `backend/src/main/java/com/waad/tba/modules/medicalclassification.rar` (from earlier ticket's findings, unchanged)

## 17. No push confirmation

Nothing was staged, committed, or pushed. `git status --short` confirms: `ContractPriceEditDialogs.jsx` now shows **zero diff against `HEAD`** (fix reverted it to the already-committed state); `ContractPriceListTab.jsx` shows its one-line WIP diff, now documented as intentional; `build_product_kb.py` remains untracked with the (already-existing) fix; one new untracked test file was added. No destructive operation, deletion, or revert was performed on any generated or unrelated file.

---

**CLASSIFICATION-PRICE-LIST-FULL-STABILIZATION-1 READY FOR REVIEW** — recommended commit split per §14/§15 once approved:

- **Commit 1** — `fix(classification): stabilize price-list add-service payload and review safety`
  - `frontend/src/components/classification/ContractPriceListTab.jsx`
  - `backend/src/test/java/com/waad/tba/modules/providercontract/service/ContractPriceEditServiceAddServiceTest.java`
  - `docs/classification/CLASSIFICATION-PRICE-LIST-FULL-STABILIZATION-1-REPORT.md`
  - (Note: `ContractPriceEditDialogs.jsx` needs no commit — already matches `HEAD`.)

- **Commit 2** (pending explicit approval per this ticket's scope) — `fix(classification-tools): correct Odoo knowledge generator matching`
  - `tools/classification-engine/build_product_kb.py`

Generated knowledge artifacts (`odoo_knowledge.json`, `official_taxonomy.json`, `odoo_knowledge.legacy.json`) remain explicitly excluded from any commit pending the `TAX1-TO-WAAD-CATEGORY-MAPPING-1` follow-up.
