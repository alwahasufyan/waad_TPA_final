# CLASSIFICATION-PRICE-LIST-WIP-REVIEW-1 — Provider Price-List / Classification Fixes Cluster Review

**Status: BLOCKED.** Not staged. Not committed. Not pushed.

## 0. Maven cache restoration (prerequisite, done first)

```
cd backend
mvn -U -DskipTests compile   → BUILD SUCCESS
mvn -o compile                → BUILD SUCCESS
```

The `spring-boot-starter-parent:3.5.11` gap the handoff report described was a local repository cache miss, not a source problem. `mvn -U` re-resolved it online; a subsequent fully-offline `mvn -o compile` now succeeds. Backend compile is unblocked for the rest of this session.

## 1. Files reviewed

| File | Status | Verdict |
|---|---|---|
| `frontend/src/components/classification/ContractPriceEditDialogs.jsx` | Modified | **BROKEN** — payload field mismatch (§2) |
| `frontend/src/components/classification/ContractPriceListTab.jsx` | Modified | Minor scope change, needs a policy decision (§3) |
| `frontend/src/pages/classification/review/index.jsx` | Modified | Correct, verified against backend enums (§4) |
| `frontend/src/services/api/classification.service.js` | Modified | Correct — adds the function `review/index.jsx` needs (§4) |
| `tools/classification-engine/odoo_knowledge.json` | Modified | **BROKEN** — 100% of entries use a category taxonomy that does not exist in the database (§5) |
| `tools/classification-engine/official_taxonomy.json` | Untracked | Source of the new, unimplemented taxonomy (§5) — do not commit |
| `tools/classification-engine/build_product_kb.py` | Untracked | Generator for the new taxonomy — do not commit yet (§5) |
| `tools/classification-engine/odoo_knowledge.legacy.json` | Untracked | Confirmed byte-identical backup of the currently-committed, working `odoo_knowledge.json` (§5) — never commit |
| `tools/classification-engine/__pycache__/` | Untracked | Unsafe, never commit |

## 2. Add-Service payload: confirmed broken end-to-end

The diff changes the `AddServiceDialog` submit payload from:

```js
{ categoryId, medicalServiceId, price, reason }
```

to:

```js
{ medicalCategoryId, medicalServiceId, basePrice, contractPrice, currency: 'LYD', notes }
```

`addPriceListService(contractId, payload)` (`frontend/src/services/api/provider-contracts.service.js:348`) posts this straight to `POST /api/v1/provider-contracts/{contractId}/pricing/items`, handled by `ContractPriceEditController.addService` (`backend/.../providercontract/controller/ContractPriceEditController.java:44-52`), which binds to:

```java
public record AddServiceRequest(
        String serviceCode,
        @NotBlank String serviceName,
        @NotNull Long categoryId,
        Long medicalServiceId,
        @NotNull BigDecimal price,
        @NotBlank String reason) {}
```

(`backend/.../providercontract/dto/ContractPriceEditDtos.java:24-30`)

`ContractPriceEditService.addService` (lines 78-90) explicitly validates, in order:
```java
if (req.price() == null || req.price().compareTo(BigDecimal.ZERO) <= 0) throw ...;
...
if (req.categoryId() == null) throw ...;
requireReason(req.reason());
```

Since the new frontend payload sends `medicalCategoryId`/`basePrice`/`contractPrice`/`notes` and never `categoryId`/`price`/`reason`, every field the backend requires (`categoryId`, `price`, `reason`) arrives as `null`. **The very first check (`req.price() == null`) throws `ValidationException("السعر يجب أن يكون أكبر من صفر")` on every single "Add Service" submission.** This is not an edge case — it is a guaranteed failure of the entire feature as currently modified.

The ticket's own described goal ("payload uses correct backend DTO fields: medicalCategoryId, basePrice, contractPrice, currency, notes") matches the **`ProviderContractPricingItem` entity's** column names, not the **`AddServiceRequest` record** the controller actually binds to. The two are different shapes by design — the DTO takes one `price` and the service internally sets both `basePrice` and `contractPrice` to it (`ContractPriceEditService.java:119-121`), plus a hardcoded `currency("LYD")` and a hardcoded `notes("MC-4C add-service by " + user)` (`req.reason()` only feeds the audit trail, not the `notes` column). The Codex change appears to have been made by pattern-matching the entity fields instead of the actual request contract.

**Fix required before this file can be committed**: revert the payload back to `{ serviceCode, serviceName, categoryId: form.category.id, medicalServiceId, price: Number(form.price), reason: form.reason.trim() }`, i.e. keep the pre-existing field names. Do not rename to the entity's column names.

## 3. `ContractPriceListTab.jsx` — Add-Service button enablement

```diff
- disabled={!active}
+ disabled={!contractId}
```

where `active = summary?.activeVersion` (whether the contract currently has an active price-list version).

Previously the "Add Service" button was disabled unless an active version existed. The change enables it any time a `contractId` is present, regardless of whether an active version exists. `ContractPriceEditService.addService` does not require an active version — `activeVersionId(contractId)` is looked up and stored, and can legitimately be `null` — so this does not cause a server error either way.

This is a **product/UX policy change** (allow ad-hoc service additions before any price-list version exists), not a bug. It is small and independent of the broken payload in §2. Flagging for an explicit decision rather than blocking on it: keep the old `!active` gate, or accept the new `!contractId` gate. Either is safe to ship once §2 is fixed.

## 4. `review/index.jsx` + `classification.service.js` — verified correct

- **Status mapping** (`TRUSTED → PENDING_BULK`, `UNRESOLVED → NEEDS_REVIEW`) matches the backend enum exactly: `PriceListImportLine.ReviewStatus { PENDING_BULK, NEEDS_REVIEW, APPROVED, REJECTED }` (`backend/.../pricelist/entity/PriceListImportLine.java:29`). No UI decision-level label (`TRUSTED`/`UNRESOLVED`, from the separate `DecisionLevel` enum) is sent directly to the backend — the translation table catches it at the boundary as intended.
- **Duplicates tab** calls `classificationService.getReviewQueue(importId, 'DUPLICATE', ...)` → `GET /{importId}/review/queue/DUPLICATE`, and `DUPLICATE` is one of the four valid queues Spring accepts (`PriceListReviewController.java:43-44`, `Set.of("UNKNOWN", "LOW_CONFIDENCE", "DUPLICATE", "GUARD")`). No 400 risk.
- **Double-submit protection**: `submitDecision` now short-circuits with `if (saving) return;` before setting `saving = true` — correct guard.
- **Approved/rejected rows no longer show the approve action or the inline price editor**: the added `row.reviewStatus !== 'APPROVED' && row.reviewStatus !== 'REJECTED'` checks read the real persisted `reviewStatus` field, confirmed present on the DTO (`PriceListImportLineDto.java:31,58`), not just the looser `userFacingStatus` the old code relied on alone.
- **Post-approval tab redirect**: `if (action === 'APPROVE' && tab === 'NEEDS_REVIEW') setTab('APPROVED')` lands the user on the correct tab, per the ticket's requirement.
- `classification.service.js`'s only change is adding the `getUnifiedReviewLines` function itself — it is the dependency `review/index.jsx` needs, correctly aliased to the existing `/{id}/lines?reviewStatus=` endpoint rather than inventing a non-existent queue-specific route.

**These two files are ready to commit on their own merits.** They do not depend on §2's broken payload or §5's broken knowledge file.

## 5. `odoo_knowledge.json` regeneration — confirmed blocking data-integrity issue

Diff stat: `69,136` lines changed (`49,045 insertions(+), 20,142 deletions(-)`) — this is a full regeneration, not a targeted fix. Structural comparison (old committed file vs. new working-tree file, both parsed as JSON):

| | Old (committed) | New (working tree) |
|---|---|---|
| Entry count | 9,904 | 11,415 |
| Fields per entry | 2 (`cat`, `name`) | 4 (`cat`, `name`, `source`, `sourceRule`) |
| Entries whose `cat` code matches a real seeded `medical_categories.code` (V57 migration, `CAT001`–`CAT031`) | **9,904 / 9,904 (100%)** | **0 / 11,415 (0%)** |

Every single entry in the new file uses one of 19 new codes (`CAT-IMG-ADV`, `CAT-IMG-DIAG`, `CAT-LAB`, `CAT-ROOM`, `CAT-DENT-ROUTINE`, `CAT-SURGERY`, `CAT-DRUG`, `CAT-MAT-CS`, `CAT-DIALYSIS`, `CAT-PHYSIO`, `CAT-DENT-IMPLANT`, `CAT-DENT-PROSTHO`, `CAT-ICU`, `CAT-AMBULANCE`, `CAT-ANESTHESIA`, and 4 more) — **none of which exist anywhere in the backend's migrations.** `grep`-ing the full `backend/src/main/resources/db/migration/` tree for these codes returns nothing; the only hyphenated `CAT-*` codes that do exist (`CAT-IP`, `CAT-OP`, `CAT-OP-RAD`, etc., from `V25__seed_data.sql`) belong to a completely different, higher-level benefit-package taxonomy, not the medical-service classification taxonomy this file feeds.

Root cause, confirmed by reading `tools/classification-engine/build_product_kb.py`'s own docstring:

> "The legacy exporter used CAT023/CAT004-style codes. The production engine accepts only TAX-1 codes (CAT-LAB, CAT-SURGERY, ...), so this importer uses service-name rules first and the Odoo category only as a conservative fallback."

This documents a **planned but not-yet-implemented** taxonomy migration ("TAX-1"): a new category coding scheme was designed and encoded into `official_taxonomy.json` and this generator script, but the corresponding database migration that would actually create these `CAT-LAB`/`CAT-IMG-ADV`/etc. rows in `medical_categories` was never written — there is no `V100__...` or later migration introducing them. `grep -rn "TAX-1"` across the backend source turns up exactly one unrelated hit (a comment in `PriceListImportLine.java` about delivery/coverage context, not medical categories).

Confirming this is genuinely mid-flight, not an oversight: `tools/classification-engine/odoo_knowledge.legacy.json` (currently untracked) is a **byte-for-byte structural match** of the currently-committed, working `odoo_knowledge.json` — i.e. Codex explicitly backed up the old file before regenerating it with the new, unwired taxonomy. That is the signature of an in-progress migration, not a completed one.

**Impact if committed as-is**: any consumer that resolves this file's `cat` code against the real `medical_categories` table (the classification engine's own downstream category-linking step, and by extension `ContractPriceEditService`/`CatalogKnowledgeService` flows that depend on a valid category match) would fail to resolve **100% of entries**, silently degrading every previously-working auto-classification into an unresolved/manual-review case. This is a regression, not an improvement, until the `V100`-style migration adding the new category codes (or a code-mapping layer) exists and is deployed.

**This file must not be committed.** Nor should `official_taxonomy.json` or `build_product_kb.py` (its generator) — they are the source of the same not-yet-wired taxonomy. `odoo_knowledge.legacy.json` should also stay untracked (it is Codex's own working backup, not a file the app needs).

## 6. Duplicate/no-commit files — unsafe file rules confirmed

- `tools/classification-engine/__pycache__/` — never commit (build artifact).
- `odoo_knowledge.legacy.json` — never commit (backup scratch file, per §5).
- `official_taxonomy.json`, `build_product_kb.py` — do not commit yet; they belong to the unimplemented TAX-1 migration, not this cluster's scope.

## 7. Validation run

```
cd backend
mvn -o compile          → BUILD SUCCESS (already confirmed in §0)

cd ../frontend
npx vite build           → succeeded (already run this session; unaffected by these 4 files)
npx eslint <4 changed files> → 0 errors, 66 warnings (all CRLF/prettier line-ending noise, pre-existing repo-wide, no real lint issues)
```

No API/browser smoke test was performed (no browser automation available in this environment). Given the confirmed backend-validation failure in §2 (guaranteed `ValidationException` on every Add-Service submit) and the confirmed 0%-resolvable category codes in §5, a browser smoke test would only reproduce failures already proven by direct code/data inspection — not needed to reach the BLOCKED verdict below.

## 8. Recommendation

Split this cluster before committing anything:

1. **Commit now, independently**: `review/index.jsx` + `classification.service.js` (§4) — verified correct, no dependency on the broken pieces.
2. **Fix before commit**: `ContractPriceEditDialogs.jsx` — revert the Add-Service payload to the original `{ categoryId, price, reason }` field names (§2). Do not use the entity's column names.
3. **Needs a decision, not a blocker**: `ContractPriceListTab.jsx`'s `!active` → `!contractId` button-enablement change (§3) — safe to ship once §2 is fixed, but flagging so the product owner explicitly picks the intended gating rule.
4. **Do not commit at all in this ticket**: `odoo_knowledge.json`, `official_taxonomy.json`, `build_product_kb.py`, `odoo_knowledge.legacy.json` (§5) — this is a separate, unfinished ticket (the TAX-1 category migration) that needs its own database migration and its own review before any of these files touch git.

---

**CLASSIFICATION-PRICE-LIST-WIP-REVIEW-1 BLOCKED — Add-Service payload sends fields the backend `AddServiceRequest` DTO does not accept (guaranteed validation failure on every submit), and `odoo_knowledge.json` was regenerated against a category taxonomy (`CAT-IMG-ADV`, `CAT-LAB`, etc.) that has zero matching rows in the actual `medical_categories` table — no migration for it exists. Recommend splitting the cluster per §8: commit the two verified-correct review-workspace files now, fix the payload field-name bug before touching the dialog file again, and treat the knowledge-file regeneration as its own future ticket blocked on a real database migration.**
