# MC-6 Lite — WAAD Approved Service Dictionary / Learning Closure Report

**Status: CLOSED**
**Date: 2026-07-12**
**Scope: learning loop only — no dashboard, no new UI beyond 4 minimal REST endpoints.**

## 1. Where WAAD knowledge is stored

Two tables, both pre-existing from MC-2/MC-4B, extended in this pass:

- `ent_service_aliases` (entity `ServiceAlias`) — learned wording variants (Arabic/English/provider-specific) mapped to a canonical `MedicalService`. Columns: `alias_text`, `locale`, `source`, `weight`, and (new in **V75**) `active`.
- `catalog_classification_history` (entity `CatalogClassificationHistory`) — audit trail of every classification/category decision made against a `MedicalService`, constrained to `change_source IN ('IMPORT_REVIEW','ADMIN','MIGRATION')`.

Both are consulted through `CatalogKnowledgeService`, which builds and caches an in-memory canonical-text → `MedicalService.id` index (`AtomicReference<Map<String,Long>>`), invalidated on every write.

## 2. Review decisions write to the knowledge base

Already true before this pass (MC-2 design): `ReviewService.approveLine()` → `CatalogKnowledgeService.recordApproval()` writes:
- a `catalog_classification_history` row with `changeSource=IMPORT_REVIEW`
- a `ServiceAlias` (source `REVIEWER_DECISION`) if the provider's raw wording isn't already known

This path was traced end-to-end and confirmed unchanged and correct. No code change was needed here — only verification.

## 3. Direct "Add Service" from MC-4C now feeds the knowledge base when appropriate

**This was the actual gap MC-6 Lite closed.** Before this pass, `ContractPriceEditService.addService()` never touched the knowledge base at all, even when the admin explicitly linked the new pricing item to an existing catalog `MedicalService` via `medicalServiceId`.

Fix (`ContractPriceEditService.java`): a `linkedToCatalog` boolean is set `true` only when `req.medicalServiceId()` resolves to a real, existing `MedicalService`. Only in that case is `CatalogKnowledgeService.recordAdminLink(serviceId, categoryId, serviceName, user)` called, writing:
- a `catalog_classification_history` row with `changeSource=ADMIN`
- a `ServiceAlias` (source `ADD_SERVICE`) for the provider's exact wording

An **unlinked** ad-hoc add-service (no `medicalServiceId`, or one that doesn't resolve) never calls into the knowledge base — the provider's free-text service name is never learned as global knowledge. This is the "when appropriate" gate the task required.

## 4. Future imports use learned aliases

Unchanged, pre-existing, verified: `ImportProcessingService` consults `CatalogKnowledgeService.findServiceIdByText()` on every import line; a hit downgrades the line straight to `PENDING_BULK` with `classificationSource=KNOWLEDGE_BASE` and the Arabic reason `"✔ معروف من قرارات مراجعة سابقة (قاموس وعد الطبي)"`. Canonicalization (`ArabicTextCanonicalizer`) normalizes Arabic/English variants and whitespace/diacritics before lookup, so Arabic-English and normalized-spelling variants match the same entry.

## 5. Prevention of dangerous learning

Verified structurally, not just by convention:
- `recordApproval()` is only reachable from `approveLine()` (single explicit APPROVE decision) or `approveRemaining()` (bulk act restricted to already-high-confidence `PENDING_BULK` lines) — **never** from NEEDS_REVIEW or unreviewed lines.
- `recordAdminLink()` is only reachable when an admin explicitly selects an existing catalog service during add-service — never from raw provider text alone.
- New: `active` column (**V75**) lets a bad/typo alias be soft-disabled (`deactivateAlias`) without deleting audit history; `index()` now loads `findByActiveTrue()` only, so a deactivated alias stops matching immediately.
- `source` column now distinguishes provenance (`REVIEWER_DECISION`, `ADD_SERVICE`, `MANUAL`) for every alias — enough to distinguish reviewed vs. admin-linked vs. hand-entered knowledge without needing a separate global/provider-specific alias table for this Lite scope.

## 6. Minimal backend endpoints added

New controller: `CatalogKnowledgeController` at `/api/v1/medical-classification/knowledge`, `@PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")`:

| Endpoint | Purpose |
|---|---|
| `GET /services/{serviceId}` | View a service's knowledge: active aliases + classification-history count |
| `GET /inspect-match?rawName=&rawNameAlt=` | Dry-run: would this raw text auto-match, and to which service? |
| `POST /services/{serviceId}/aliases` | Manually add an alias (`source=MANUAL`), audited |
| `POST /aliases/{aliasId}/deactivate` | Soft-disable a bad alias, audited |

No list/browse-all-aliases UI was built (not needed for Lite scope — per-service inspection is sufficient for the audit/correction need).

## 7. Verification report

This document, plus the live Docker evidence below, serves as the verification report (no separate admin page was needed).

## Live evidence (against running Docker stack)

1. **Review-approval → alias write → match flip**: approved a review line for a known service; `inspect-match` on the provider's raw wording flipped from `matched=false` to `matched=true`, pointing at the correct `serviceId`.
2. **Manual alias add/deactivate → match flip and flip-back**: added a manual alias via `POST /services/{id}/aliases`; `inspect-match` immediately matched; `POST /aliases/{id}/deactivate` immediately un-matched it (index invalidation confirmed live, no restart needed).
3. **Cross-import auto-recognition**: a later import (`akeed.xlsx`, import #18) returned 12 lines auto-classified via `classificationSource=KNOWLEDGE_BASE`, including services (e.g. "Blood Culture", "CSF Culture") learned in **earlier sessions** — proving the knowledge persists across sessions/imports, not just within one review cycle.
4. **Negative-result safety check**: an add-service call with no `medicalServiceId` link produced no new alias and no classification-history row for any catalog service — confirmed unlinked additions do not pollute global knowledge.

## Acceptance criteria — verified

- [x] A reviewed/corrected service creates or updates WAAD knowledge (pre-existing, re-verified).
- [x] A manually added service with classification can become part of the approved dictionary when appropriate (new: `recordAdminLink`, gated on explicit catalog link).
- [x] A future import recognizes the learned service/alias (verified via import #18 KNOWLEDGE_BASE hits).
- [x] An already-approved service no longer returns UNKNOWN/NEEDS_REVIEW (verified via `inspect-match` flip and import PENDING_BULK routing).
- [x] Low-confidence/unreviewed items do not pollute the dictionary (structurally guaranteed — no write path exists for them).
- [x] Clear audit/history of learning decisions (`catalog_classification_history`, `changed_by`/`created_by` on aliases).
- [x] Behavior verified through the Docker import flow (import #18, live).

**MC-6 Lite: CLOSED.**
