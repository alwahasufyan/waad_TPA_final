# Medical Classification Engine — Architecture & Implementation Plan

**Version:** 1.2 — **APPROVED for implementation** (2026-07-09) with two owner additions (A10, A11). Supersedes v1.1.
**Status:** ✅ APPROVED — implementation begins at M0. The 8 amendments (v1.1) + 2 additions (v1.2) below are **binding decisions**, incorporated throughout this document.
**Scope:** Integrate the existing Excel/PDF price-list classification script into the WAAD TPA system as part of the Provider Contract lifecycle — delivered as a general **Medical Classification Engine** whose first consumer is provider price lists.
**Framework:** ATEF (Constitution + standards), Clean Architecture, DDD, existing WAAD business model.
**Approved business decision honored:** Providers NEVER classify, edit, or approve classifications. Classification is 100% WAAD-owned. The Medical Catalog is the single source of truth.

---

## 0. Binding Amendments (owner decisions, v1.1)

| # | Decision | Effect on the plan |
|---|---|---|
| A1 | **No FastAPI/sidecar in Phase 1.** Spring Boot → `ProcessBuilder` → Python script → JSON → Spring Boot. Sidecar service is a *later* evolution, only after the system stabilizes. | §5.1 rewritten: CLI invocation is the Phase-1 architecture; the JSON contract is designed so the transport can later change to HTTP without touching callers. |
| A2 | **No new Medical Catalog.** Extend the existing `MedicalService` / `MedicalCategory` / `ServiceAlias` only. | Already the design; §10 states it explicitly — zero parallel catalog structures. |
| A3 | **Benefit Engine is 100% out of scope — including lookup.** Nothing in `CoverageEngineService`, `ClaimMapper`, or any pricing-resolution path is touched. | §5.4 reduced to a compatibility statement; the previously mentioned "date-aware version lookup" enhancement is **deleted from scope entirely**. |
| A4 | **No auto-approval, at any confidence — including 100%.** Every line follows Imported → Needs Review → Approved. Auto-approval may be *considered* months after go-live, as a separate future decision. | §6 rewritten: confidence bands control **queue visibility and ordering only**, never approval. |
| A5 | **Reviewer sees only the critical minority** (Unknown / Duplicate / Low-confidence). The high-confidence majority is hidden by default, and is approved via an explicit **"Approve Remaining" button** that unlocks after the critical queue is cleared. | §6 + §7 rewritten around this flow. Approval of the majority is one deliberate human action, not thousands of clicks — and not silent automation. |
| A6 | **No DRAFT `MedicalService` creation at import time.** Unmatched services live **only inside the import staging tables**. A `MedicalService` is created *only when the reviewer approves* that line. | §5.2 catalog rules rewritten; keeps the catalog free of imported noise. |
| A7 | **Module name and shape: `Medical Classification Engine`** (not "Price List Import"). Price lists are only the first input channel; the engine must be reusable for OCR, PDF, claims text, pharmacy, and external APIs. | §3/§5 restructured: `modules/medicalclassification` (engine, generic) + price-list workflow as its first consumer. Engine API takes a generic "classification request" with a channel tag. |
| A8 | **Classification Dashboard** required: totals, new vs known services, low-confidence counts, top-20 unknown services, best-quality providers, classification rate. | New §7.6. |
| A9 (final condition) | **The existing script must not be deleted, modified (beyond the approved minimal JSON-output addition), or disabled until the new module is verified 100% against real provider files.** The script remains the authoritative reference during the transition. | §12 gains a hard verification gate: shadow-mode regression against the script's own outputs on the real files in `جاهز/` is a **mandatory pass condition** before the module is trusted. |
| A10 (v1.2) | **Financial Validation Engine** — before ANY version is published, an automated financial validation pass detects abnormal **price** changes (not just classification issues): spikes vs previous version, outliers vs catalog cost / category norms, zero/suspicious prices, total-value swings. Blocking findings prevent publish until resolved/acknowledged. | New §6.5; `price_list_validation_findings` table (§4.1); `FinancialValidationService` (§5.2); publish flow gated (§8, §9). Part of the M0/M1 scope. |
| A11 (v1.2) | **Version Comparison Dashboard** — the approver approves a version based on a rich statistical comparison report (added/removed/repriced/reclassified, price-change distribution, top increases/decreases, financial impact summary, validation findings), not a raw list of services. | §7.4 upgraded from a simple diff view to a full comparison dashboard; the approve/publish actions live ON this report. Part of the M0/M1 scope. |

**Positioning (owner statement):** with A10 + A11 this module is not a price-list importer — it is a **governance system** for provider price-list management, consistent with WAAD/ATEF as a professional, scalable TPA platform.

---

## 1. Architecture Analysis (current state)

### 1.1 What exists today in WAAD

| Concern | Where it lives today | Key facts |
|---|---|---|
| Medical reference data | `modules/medicaltaxonomy` — `MedicalService`, `MedicalCategory` (hierarchical, multi-root, `context` OP/IP/ANY), `ServiceAlias` (V83, `ent_service_aliases`), `MedicalSpecialty` | `MedicalService` supports DRAFT status, bilingual `nameAr/nameEn`, `isMaster` flag, soft-delete, catalog `cost`. Category is mandatory for ACTIVE services (enforced in `@PrePersist`). |
| Provider contracts | `modules/providercontract` — `ProviderContract` (DRAFT→ACTIVE→SUSPENDED/EXPIRED/TERMINATED, one active per provider), `ProviderContractPricingItem` | Pricing item = contract_id + service_name/service_code + category_name/sub_category_name (free text!) + optional FK `medical_category_id` + base/contract price + effective dates + `active` flag. Unique per (contract, service). |
| Excel import (current) | `ProviderContractPricingExcelService` + `PriceListExcelTemplateService` | Imports a *clean* 6-column Excel directly into pricing items. **No classification, no confidence, no staging, no versioning** — updates rows in place (overwrite risk). This is the gap the script currently fills *outside* the system. |
| Benefit Engine | `modules/claim` — `CoverageEngineService` | Consumes `ClaimLineInput.contractPrice`; guard = `min(enteredUnitPrice, contractPrice)`. Coverage % resolved from category. **Out of scope in its entirety (A3).** |
| Claims linkage | `ClaimMapper` → `ClaimLine` | `ClaimLine.pricingItemId` stores the **ProviderContractPricingItem id** plus denormalized `serviceCode`/`serviceName`. ⚠️ Historical claims therefore depend on pricing-item rows never being deleted or repriced in place. |
| Provider Portal | `ProviderPortalController` | Read-only endpoints already exist: `/my-services` (paginated pricing items of the active contract), `/my-services/{code}/price`. Guarded by `ProviderContextGuard`. |
| Reusable UI framework (Stage 2) | `UnifiedMedicalTable`, `EnterpriseFilters`, `WorkspaceSidebar`, preview-first reporting | The Review Screen and Dashboard are built from these — no new table/filter frameworks. |

### 1.2 Architectural pain points the new module must fix

1. **Classification lives outside the system** (Python script + Excel round-trips). No audit trail, no RBAC, no learning loop inside WAAD.
2. **`ProviderContractPricingItem.categoryName/subCategoryName` are free text** — the FK `medical_category_id` is optional and often absent. The module must make the catalog FK authoritative on published rows.
3. **No versioning**: re-import mutates pricing rows in place; `ClaimLine.pricingItemId` makes this a latent financial-integrity risk.
4. **Knowledge is file-bound**: `medical_synonyms.json`, `odoo_knowledge.json`, `قائمة التصنيفات المعتمدة.xlsx` — should become catalog data (DB) with history.

---

## 2. Current Script Analysis (`للمرافق معالجة اكسيل  سكربت`)

### 2.1 Components

| File | Role | Verdict |
|---|---|---|
| `ingest.py` (~520 lines) | Multi-format reader: Excel (all sheets), CSV, PDF (pdfplumber + **automatic reversed-Arabic repair**), PPTX (tables + positional textbox pairing). Column detection works **even without headers** (ratio heuristics for name/price/code, row-number column exclusion). | **Reuse as-is.** This is the hardest-won logic; rewriting it in Java would be high-risk, zero-gain. |
| `tpa_service_mapper.py` (~1000 lines) | Normalization (diacritics/tatweel/unicode), synonym expansion, matching pipeline: **exact → synonym → fuzzy** (rapidfuzz `token_set_ratio` or internal inverted-index Jaccard/containment/difflib), `CategoryClassifier` (medical keyword rules with Arabic stemming), **IgG/IgM/IgA/IgE antibody guard** (caps score at 60 → forces review), legacy CAT-code remap, confidence score 0–100, reason strings for reviewers. | **Reuse the engine; add a JSON output mode** (see §5.1). Per A9, this addition is made as a *new entry point* — the existing CLI/Excel behavior is left byte-for-byte intact. |
| Classification priority | `hint (facility type) → contract reference → Odoo KB (~9,900 names) → medical keyword rules → default (CAT023)` — sources `hint/ref/odoo` are "trusted", `rule/default` need review. | Maps onto our queue bands (§6) — as *ordering/visibility* signals only (A4). |
| `build_odoo_kb.py`, `medical_synonyms.json`, `قائمة التصنيفات المعتمدة.xlsx` | Knowledge base + synonyms + approved categories. | **Migrate content into WAAD DB** (aliases + category mapping). Files stay in place untouched (A9); the DB copies become the live source for the integrated engine. |
| `process_all.py`, `process_old_root.py`, `collect_approved.py` | Batch folder processing and post-review collection — Excel-workflow plumbing. | Not needed in the integrated workflow. **Kept fully functional (A9)** — the offline workflow remains the authoritative fallback until final verification. |

### 2.2 Real-world performance (from the script's own test log)

Match-to-contract rates were 9–49% on real files — *not* a tool defect: the reference was an outpatient catalog while inputs were full hospital catalogs. Implications:

- The **match rate depends on catalog completeness** → the integrated engine must match against the **full WAAD Medical Catalog**, which is far richer than one contract's list.
- A large "needs review" tail is normal on a provider's *first* import → the Review Screen is built for **bulk, keyboard-fast triage of the critical minority only** (A5), and every reviewer decision **feeds the learning loop** so provider #50 imports far better than provider #1.

### 2.3 Reuse decision

**Wrap, don't rewrite — via CLI (A1).** The script's linguistic layer (Arabic normalization, stemming, reversed-PDF repair, medical keyword rules, antibody guard) represents months of tuning. The only code addition: a new module-level function/entry point `classify_records(...) -> JSON` (e.g. a new `classify_json.py` next to the script that imports it), leaving every existing file and behavior untouched per A9.

---

## 3. Target Architecture

```
┌───────────────────────────────────────────────────────────────────────────┐
│ WAAD Backend (Spring Boot) — new module: modules/medicalclassification    │
│                                                                           │
│  ── ENGINE CORE (generic, channel-agnostic) ──                            │
│  ClassificationEngineService                                              │
│     └─► ClassificationEngineClient (Phase 1: ProcessBuilder → Python      │
│          script → JSON file/stdout → parsed result. Later: same JSON      │
│          contract over HTTP if/when a sidecar is approved)                │
│  CatalogSnapshotService (exports services+aliases+categories for engine)  │
│  CatalogKnowledgeService (reviewer decisions → aliases + history)         │
│  ClassificationDashboardService (A8 metrics)                              │
│                                                                           │
│  ── FIRST CONSUMER: PRICE LIST WORKFLOW ──                                │
│  PriceListImportController ─► ImportOrchestrationService (async)          │
│  PriceListReviewController ─► ReviewService (critical queue,              │
│                                "Approve Remaining", decisions)            │
│  PriceListVersionController ─► PriceListVersionService (publish/diff)     │
│  CorrectionRequestController (portal feedback)                            │
│                                                                           │
│  ── FUTURE CONSUMERS (same engine core, new workflows only) ──            │
│  OCR / PDF documents · Claims free-text services · Pharmacy lists · APIs  │
└───────────────────────────────────────────────────────────────────────────┘
         ▲ read-only                                   ▲ UNTOUCHED (A3)
   Provider Portal                               Benefit Engine / Claims
   (approved list + correction requests)         (no code, no lookup change)
```

**DDD boundaries:**
- **Medical Classification Engine** (new, generic): takes a `ClassificationRequest` (channel = PRICE_LIST / OCR / CLAIM_TEXT / PHARMACY / API, records[], provider hint, config) and returns scored, source-tagged suggestions. Knows nothing about contracts or prices.
- **Price List Import workflow** (first consumer): import jobs, staging lines, review decisions, versions. Aggregate roots = `PriceListImport`, `PriceListVersion`.
- **Medical Catalog** (existing `medicaltaxonomy`, extended only — A2): single source of truth. Import data *proposes*; only reviewer-approved decisions *dispose*.
- **Provider Contract** (existing): gains a reference to the active `PriceListVersion`. Lifecycle rules unchanged.
- **Benefit Engine / Claims** (existing): **out of scope entirely (A3)**.

---

## 4. Database Design Proposal (no migrations yet)

### 4.1 New tables

**`price_list_imports`** — one row per uploaded provider file (aggregate root)
- `id`, `provider_id` FK, `contract_id` FK nullable, `file_name`, `file_hash` (dedupe), `file_storage_path`
- `status`: `UPLOADED → PROCESSING → CLASSIFIED → IN_REVIEW → REVIEW_COMPLETE → PUBLISHED` | `FAILED` | `CANCELLED`
- `engine_version`, `threshold_config` (JSON snapshot of queue-band config used — auditability)
- counters: `total_lines`, `known_services`, `unknown_services`, `low_confidence`, `duplicates`, `approved`, `rejected`
- `uploaded_by`, `uploaded_at`, `processed_at`, `error_message`, audit columns

**`price_list_import_lines`** — staging rows (the script's output lands here instead of Excel)
- `id`, `import_id` FK, `row_no`, `source_sheet`
- Raw payload: `raw_name`, `raw_name_alt` (bilingual), `raw_code`, `raw_price`, `raw_category_text`
- Engine result: `normalized_name`, `matched_service_id` FK→medical_services nullable, `matched_service_code`, `suggested_category_id` FK→medical_categories, `confidence_score` (0–100), `match_method` (EXACT/SYNONYM/FUZZY/NONE), `classification_source` (HINT/REFERENCE/KNOWLEDGE_BASE/RULE/DEFAULT), `engine_reason` (the script's human-readable reason string — kept, reviewers rely on it), `flags` (ANTIBODY_GUARD / DUPLICATE_IN_FILE / DUPLICATE_VS_VERSION / PRICE_OUTLIER / EMPTY_NAME)
- Queue state (A4/A5): `review_status` — `PENDING_BULK` (high-confidence majority, hidden by default) / `NEEDS_REVIEW` (unknown, duplicate, low-confidence, guard-flagged) / `APPROVED` / `REJECTED`
- Decision: `final_service_id`, `final_category_id`, `final_price`, `approved_by`, `approved_at`, `approval_mode` (`INDIVIDUAL` / `BULK_REMAINING`), `reviewer_note`
- **Unknown services live here and only here (A6)** — no catalog rows are created until approval.
- Indexes on (`import_id`, `review_status`).

**`provider_price_list_versions`** — the versioning backbone
- `id`, `provider_id` FK, `contract_id` FK, `version_no` (unique per contract), `status` (`DRAFT` / `ACTIVE` / `SUPERSEDED` / `ARCHIVED`)
- `source_import_id` FK (provenance), `effective_from`, `effective_to`
- `approved_by`, `approved_at`, `published_by`, `published_at`, `notes`
- Rule: at most one `ACTIVE` per contract; **immutable after activation** (service-layer enforced, like contract lifecycle).

**`price_list_correction_requests`** — Provider Portal feedback (no editing)
- `id`, `provider_id`, `pricing_item_id` FK, `request_type` (WRONG_PRICE/WRONG_NAME/MISSING_SERVICE/OTHER), `message`, `status` (OPEN/IN_REVIEW/RESOLVED/REJECTED), `resolved_by`, `resolution_note`, timestamps.

**Catalog knowledge & history (extending the existing catalog — A2):**
- **`ent_service_aliases` extended** (preferred over a new table): add `source` (ODOO_MIGRATION / SYNONYM_FILE / REVIEWER_DECISION / MANUAL), `weight`, and provenance columns. Populated from the one-time Odoo/synonyms migration and continuously from reviewer decisions.
- **`catalog_classification_history`** (new): every classification/mapping event — `medical_service_id`, `category_id_old/new`, `changed_by`, `change_source` (IMPORT_REVIEW/ADMIN/MIGRATION), `import_line_id` nullable, `confidence_at_decision`, timestamp. Serves the Confidence/Classification/Mapping History requirements and feeds the Dashboard.

**`price_list_validation_findings`** (A10) — output of the Financial Validation Engine, per version-candidate
- `id`, `version_id` FK (or `import_id` while still pre-version), `line_ref` (pricing line / staging line id, nullable for aggregate findings)
- `finding_type`: `PRICE_SPIKE_VS_PREVIOUS` / `PRICE_DROP_VS_PREVIOUS` / `OUTLIER_VS_CATALOG_COST` / `OUTLIER_VS_CATEGORY_NORM` / `ZERO_OR_NEGATIVE_PRICE` / `SUSPICIOUS_ROUNDING` / `TOTAL_VALUE_SWING` / `DUPLICATE_PRICE_CONFLICT`
- `severity`: `BLOCKER` (publish impossible until the line is corrected or the finding explicitly waived) / `WARNING` (must be acknowledged) / `INFO`
- evidence: `old_price`, `new_price`, `change_percent`, `reference_value` (catalog cost / category median), `message`
- resolution: `status` (OPEN/RESOLVED/WAIVED), `resolved_by`, `resolved_at`, `waiver_note` — waivers are audited financial acts
- Thresholds (e.g. spike > ±X%, outlier > ×N catalog cost, total swing > Y%) come from `classification_settings`, snapshotted per run.

**`classification_settings`** — configurable queue thresholds (§6), engine path/timeout, price-outlier factor, financial-validation thresholds (A10). (Auto-approval flag exists in the schema but is **hard-disabled in Phase 1** — A4.)

### 4.2 Change to an existing table (the only one)

**`provider_contract_pricing_items` + `version_id`** (nullable FK → `provider_price_list_versions`).

Keep `ProviderContractPricingItem` as the one operational pricing table (Benefit Engine, ClaimMapper, Provider Portal already read it — and continue reading it unchanged, A3). Each row belongs to a version:
- Publishing version N **inserts new rows** tagged `version_id = N` and flips `active=false` on version N-1 rows — never updates, never deletes.
- `ClaimLine.pricingItemId` keeps pointing at the exact row (exact price) in force when the claim was made → historical claims permanently safe, zero claim-side changes.
- Backfill: all existing pricing items become "Version 1" of their contract (§12).
- Rows published through this module always carry `medical_category_id` (free-text `category_name`/`sub_category_name` become derived display copies).

### 4.3 Explicitly NOT changed
`claims`, `claim_lines`, `benefit_policy_*`, `visits`, `settlements`, `members` — untouched (A3). `medical_services` / `medical_categories` — no schema change. The Python script folder — untouched (A9).

---

## 5. Backend Design

### 5.1 Engine integration — Phase 1: ProcessBuilder CLI (A1)

```
Spring Boot (ClassificationEngineClient)
   │  writes: request.json  (records extracted from the uploaded file
   │          OR the raw file path + catalog snapshot path + config)
   ▼
ProcessBuilder → python classify_json.py --request request.json --out result.json
   ▼
reads result.json → parsed ClassificationResult → staging rows
```

Design rules that keep this clean and future-proof:
- **The JSON contract is the architecture**, the transport is a detail. `ClassificationEngineClient` is an interface; Phase 1 implementation = `CliClassificationEngineClient` (ProcessBuilder). A later `HttpClassificationEngineClient` (sidecar) is a drop-in swap requiring zero changes to callers. The sidecar remains a documented *future option*, not part of this delivery.
- **New entry point, untouched script (A9):** a new `classify_json.py` beside the existing files imports `ingest` + `tpa_service_mapper` functions and emits JSON. Existing CLI, Excel outputs, and batch scripts stay byte-identical and remain the authoritative reference workflow.
- **Catalog as reference:** the request includes a catalog-snapshot file (services + aliases + approved categories) exported by `CatalogSnapshotService` (cached, regenerated on catalog change). The engine prefers it over the bundled xlsx/json files when supplied — the files themselves are not modified.
- Operational hardening: dedicated venv path from `classification_settings`, `-X utf8`, process timeout + kill, non-zero exit → import `FAILED` with stderr captured, one classification process per import at a time (queued), temp files in a managed work directory.
- Deployment: dev = local venv (already exists in the script folder); Docker = Python + deps added to the backend image (or a shared volume) — **no new service/container** (A1).

**JSON contract (engine API, transport-agnostic):**
- Request: `{ channel: "PRICE_LIST", file_ref | records[], provider_type_hint, catalog_snapshot_ref, config { thresholds, code_prefix } }`
- Response: `{ engine_version, lines: [{ row_no, raw_*, normalized, match { service_code, score, method, reason }, suggestion { category_code, source }, flags [] }] }`
- `channel` is carried from day one so OCR/claims/pharmacy consumers (A7) reuse the identical contract later.

### 5.2 New Spring module `modules/medicalclassification` (A7)

```
medicalclassification/
├── engine/                                  ← generic core (channel-agnostic)
│   ├── service/  ClassificationEngineService, ClassificationEngineClient (if)
│   │             CliClassificationEngineClient, CatalogSnapshotService,
│   │             CatalogKnowledgeService, ClassificationDashboardService
│   └── dto/      ClassificationRequest/Result/LineResult (channel-tagged)
├── pricelist/                               ← first consumer workflow
│   ├── controller/  PriceListImportController, PriceListReviewController,
│   │                PriceListVersionController, CorrectionRequestController
│   ├── dto/         ImportCreate/Status, ReviewLineDto, ReviewDecisionDto,
│   │                ApproveRemainingRequestDto, VersionSummaryDto,
│   │                VersionDiffDto, CorrectionRequestDto
│   ├── entity/      PriceListImport, PriceListImportLine, PriceListVersion,
│   │                PriceListCorrectionRequest
│   ├── repository/  (queue queries by review_status)
│   └── service/     ImportOrchestrationService, ReviewService,
│                    PriceListVersionService,
│                    FinancialValidationService (A10 — pre-publish gate),
│                    VersionComparisonService (A11 — stats for the report)
└── (future consumers: ocr/, claimtext/, pharmacy/ — engine core reused)
```

**Processing model:** upload returns immediately; classification runs async (`@Async` + status polling). For very large files: chunked engine invocations, batched staging inserts.

**Catalog interaction rules (A2 + A6 — DDD guardrails):**
- Matched line approved → link to the existing `MedicalService`; write alias + classification history. No new catalog rows.
- **Unmatched line: stays in staging only.** No `MedicalService` (not even DRAFT) is created at import or classification time. Only when the **reviewer approves** an unknown line does the flow create the `MedicalService` (using the reviewer-confirmed name + category — created ACTIVE with its category, or DRAFT if the reviewer defers categorization; either way it is a deliberate human act, one service at a time or via explicit bulk selection).
- Rejected lines never touch the catalog and are excluded from the version.
- Every approval decision writes `ent_service_aliases` (raw provider wording → chosen service) — the **learning loop** that replaces `medical_synonyms.json` maintenance, so identical wording auto-matches at 100% on the next provider.

### 5.3 RBAC (new permissions)

| Permission | Grants |
|---|---|
| `classification.import` | Upload files, view own imports |
| `classification.review` | Work the critical queue, decide lines, use "Approve Remaining" |
| `classification.approve` | Approve a review-complete import into a DRAFT version |
| `classification.publish` | Activate a version onto a contract (financially effective act) |
| `classification.view` | Read-only (imports, versions, diffs, dashboard, history) |

Segregation of duties: `publish` grantable separately from `review` (reviewer ≠ publisher for financial control). Provider Portal role gets nothing beyond existing read endpoints + `correction_requests.create`.

### 5.4 Benefit Engine — OUT OF SCOPE (A3)

No change of any kind: no code, no queries, no lookup semantics, no date-aware resolution (deleted from the plan). The engine continues to receive `contractPrice`/category exactly as today from exactly the same table and paths. The only effect it will ever *observe* is better data quality: published pricing rows now always carry a real `medical_category_id`. That is a data improvement, not a behavioral change — the "improvement before services reach the Benefit Engine" required by the brief.

---

## 6. Confidence Strategy — review-first, no auto-approval (A4 + A5)

Confidence bands **never approve anything**. They decide *what the reviewer must look at* versus *what waits hidden for one deliberate bulk action*. Stored in `classification_settings`, snapshotted per import.

| Band | Condition | Queue state | Reviewer experience |
|---|---|---|---|
| **High confidence** | score ≥ 85 **and** trusted/normal source **and** no flags | `PENDING_BULK` | **Hidden by default.** Counted in the summary ("4,812 خدمة معروفة بثقة عالية"). Approved together via **"Approve Remaining"**. |
| **Low confidence** | score < 85, or source RULE/DEFAULT | `NEEDS_REVIEW` | Shown in the critical queue. |
| **Unknown** | no catalog match at all | `NEEDS_REVIEW` | Shown — decision creates the service on approval (A6). |
| **Duplicate** | duplicate within file or vs existing version | `NEEDS_REVIEW` (tab: مكررة) | Shown with both rows side by side. |
| **Hard guards** | antibody-class mismatch, price outlier (> ×N of catalog `cost` / previous version), empty/garbage name | `NEEDS_REVIEW` regardless of score | Shown with the flag reason. |

**"Approve Remaining" (A5):** enabled only when the critical queue for the import is empty (all `NEEDS_REVIEW` lines decided). One click → confirmation dialog showing exactly what will be approved (count, sample, total value) → all `PENDING_BULK` lines become `APPROVED` with `approval_mode=BULK_REMAINING`, `approved_by`, timestamp. Fully audited: it is *explicit human approval in bulk*, not automation.

Reviewers can always open the hidden majority (audit tab, read-only browse or spot-check) — hidden means *not required*, not *inaccessible*.

**Future (explicitly deferred):** after months of production history, the Dashboard's precision data (per-band error rates) becomes the evidence for a separate proposal to enable auto-approval for the top band. Not part of this delivery.

### 6.5 Financial Validation Engine (A10) — the pre-publish gate

Classification review (§6) protects *what* a service is; the Financial Validation Engine protects *what it costs*. It runs automatically at two points: when review completes (early feedback) and again at publish time (authoritative gate — prices may have been edited during review).

Checks (thresholds configurable in `classification_settings`, snapshotted per run):

| Check | Finding | Default severity |
|---|---|---|
| Price change vs the same service in the previous ACTIVE version beyond ±X% (default 30%) | `PRICE_SPIKE_VS_PREVIOUS` / `PRICE_DROP_VS_PREVIOUS` | WARNING; > ±100% → BLOCKER |
| Price > ×N catalog `cost` (default ×5) or absurdly below (< ÷10) | `OUTLIER_VS_CATALOG_COST` | WARNING |
| Price far outside the category's own distribution in this version (e.g. > ×10 category median) | `OUTLIER_VS_CATEGORY_NORM` | WARNING |
| Zero or negative price | `ZERO_OR_NEGATIVE_PRICE` | BLOCKER |
| Same service appearing with two different prices | `DUPLICATE_PRICE_CONFLICT` | BLOCKER |
| Version total value swing vs previous version beyond Y% (default 25%) | `TOTAL_VALUE_SWING` | WARNING (aggregate finding) |

Gate semantics: **publish is impossible while any BLOCKER is OPEN** (fix the line or it stays unpublished — blockers cannot be waived); WARNINGS must each be resolved or **explicitly waived with a note** (`WAIVED`, audited: who/when/why). INFO findings are informational. All findings and waivers appear on the Version Comparison report (§7.4) — the approver sees exactly what was flagged and who waived what.

---

## 7. Frontend Design (reuses Stage 2 frameworks — no new table/filter/print systems)

All under `pages/classification/` (admin side), RTL/Arabic-first, MUI 7, built from `UnifiedMedicalTable` + `WorkspaceSidebar` + `EnterpriseFilters`:

1. **Imports list** — provider, status, counts per band, date. Row → import detail.
2. **Import wizard** — upload (provider + optional contract + facility-type hint + file), async progress, then the **classification summary** (the script's Summary sheet as a screen: total / known / unknown / low-confidence / duplicates).
3. **Review workspace** (the core screen — A5):
   - Opens on the **critical queue only**: tabs غير معروفة / منخفضة الثقة / مكررة / محظورات (guards). The high-confidence majority is not in the grid.
   - Per line: raw name (both languages), price, engine suggestion + confidence + reason string, flags; actions = confirm suggestion / pick another catalog service (alias-aware search) / define as new service (A6 — creates it on approval) / reject.
   - Bulk selection within the critical tabs for same-decision groups.
   - Progress header: "المتبقي للمراجعة: 37 من 5,120" + a disabled **"اعتماد المتبقي (4,812)"** button that activates when the critical queue hits zero, with the confirmation dialog described in §6.
   - Audit tab (read-only) to browse/spot-check the hidden majority before pressing Approve Remaining.
4. **Version Comparison Dashboard (A11)** — the approval artifact. Version list per contract + version detail (read-only after activation), and for a DRAFT version a **rich comparison report vs the previous ACTIVE version** on which the approve/publish buttons live:
   - Headline stats: total services, added / removed / repriced / reclassified counts, version total value vs previous (± %).
   - **Price-change distribution** (histogram of % changes), top-20 increases and top-20 decreases with old→new prices.
   - Reclassification list (service, old category → new category).
   - **Financial validation panel (A10):** all findings grouped by severity; blockers highlighted; waivers with who/why; publish button disabled while gate is closed.
   - Import provenance (file, hash, who reviewed, Approve-Remaining audit).
   - The approver approves **this report**, not a raw list of thousands of services; the report itself is printable via the Enterprise Reporting Framework as the formal version-approval document.
5. **Provider Contract integration** — "قائمة الأسعار" tab inside `ProviderContractView`: active version, history, publish action; contract activation warns if no ACTIVE version.
6. **Classification Dashboard (A8)** — `pages/classification/dashboard`:
   - إجمالي الخدمات المستوردة / المعروفة / الجديدة / منخفضة الثقة (with trend over imports)
   - نسبة التصنيف التلقائي (known-with-high-confidence ÷ total) — the learning-loop health metric, per import and over time
   - **أفضل 20 خدمة غير معروفة** (most frequent unmatched names across providers — the synonym-work goldmine)
   - جودة المرافق: ranking of providers by first-pass classification rate
   - مصادر التصنيف (reference / knowledge base / rules) وتوزيع درجات الثقة
   - Served by `ClassificationDashboardService` over the staging + history tables; charts per the dataviz standard.
7. **Provider Portal** (existing pages, read-only) — `my-services` gains main/sub category columns (from catalog) + a **"طلب تصحيح"** action per row → correction request form. No editing anywhere. Admin sees correction requests in a small queue screen.
8. **Printing** — version documents print through the Enterprise Reporting Framework (preview-first, backend-rendered), not from the grid.

---

## 8. Workflow Diagram

```
WAAD Admin (classification.import)
   │ 1. Upload provider file (xlsx/pdf/pptx/csv) + provider + hint
   ▼
price_list_imports (UPLOADED) ── async ──► ProcessBuilder → Python script (A1)
   │                                        • ingest (multi-format, Arabic repair)
   │                                        • match vs WAAD Medical Catalog snapshot
   │                                        • confidence + source + guard flags → JSON
   ▼
price_list_import_lines (CLASSIFIED)
   ├─ high confidence, no flags ─────────► PENDING_BULK   (hidden from reviewer)
   └─ unknown / duplicate / low-conf /
      guard-flagged ────────────────────► NEEDS_REVIEW ──► Review Screen
                                                            (critical queue only)
                                            │ confirm / remap / new-service-on-approval / reject
                                            ▼
                        critical queue empty → "Approve Remaining" (explicit, audited)
                                            │  all PENDING_BULK → APPROVED
                                            ▼
                          REVIEW_COMPLETE ── approvals feed aliases + history
                                            │  (learning loop; A6: unknown services
                                            │   become MedicalService ONLY here)
                                            ▼ (classification.approve)
                   provider_price_list_versions: DRAFT Version N
                                            │
                                            ├─► Financial Validation Engine (A10)
                                            │     BLOCKERs → fix lines (no waiver)
                                            │     WARNINGs → resolve or waive (audited)
                                            ▼
                   Version Comparison Dashboard (A11) — approve ON the report
                                            ▼ (classification.publish — gate must be green)
    Version N ACTIVE ── inserts pricing items (version_id=N, medical_category_id set)
    Version N-1 SUPERSEDED ── old rows active=false, NEVER deleted (claims history safe)
                                            │
                                            ▼
    Provider Contract operational ──► Benefit Engine (UNTOUCHED — A3)
                                            │
                                            ▼
    Provider Portal: read-only approved list (name, price, main/sub category)
                      └── "طلب تصحيح" → correction request queue → WAAD resolves
```

---

## 9. Versioning Strategy

1. **Version = immutable snapshot.** Every publish creates Version N as new `provider_contract_pricing_items` rows (`version_id = N`); activation supersedes N-1 (rows kept, `active=false`). No update-in-place of published prices, ever. The current Excel import service's overwrite path is retired/redirected to this flow (after the A9 verification gate).
2. **Claims reference rows, not versions** — `ClaimLine.pricingItemId` already pins the exact historical row+price; immutability makes that guarantee real. Zero claim-side migration (A3).
3. **Effective dates**: version carries `effective_from/to`; activating N sets N-1's `effective_to`. Price lookup for new claims stays exactly as today ("active contract's active rows") — no lookup change of any kind (A3).
4. **Re-import** for the same contract → always a *new* version (even for one price fix): "what did we pay against? → version N, published by X on date Y, from import Z, file hash H."
5. **Draft versions** freely editable/discardable; only activation is controlled and logged.

---

## 10. Medical Catalog Design (extension only — A2)

No new catalog. The existing `medicaltaxonomy` module is the Medical Catalog; this plan only enriches it:

| Requirement | Design |
|---|---|
| Main/Sub category | Existing `MedicalCategory` hierarchy (parent + multi-root + OP/IP context). One-time mapping sheet aligns the script's approved-categories codes (CAT0xx) with catalog codes — reviewed by you before load. |
| Medical Service, Arabic/English names | Existing `MedicalService` (`nameAr`/`nameEn`, `isMaster`). New services enter **only via reviewer approval** (A6). |
| Synonyms & keywords | Existing `ent_service_aliases`, extended with `source`/`weight`. Populated by one-time migration (Odoo KB ~9,900 names + `medical_synonyms.json`) and continuously by reviewer decisions. |
| Confidence / classification / mapping history | New `catalog_classification_history` (every mapping decision, its confidence, who, from which import line). Powers the Dashboard and future threshold evidence. |
| Single source of truth | Enforced by flow: provider data lands in staging; only human-approved decisions touch pricing items or create services; ACTIVE services always have a category (already DB-enforced). |

---

## 11. Provider Contract Integration

```
Provider 1─* ProviderContract 1─* PriceListVersion (one ACTIVE)
                                      1─* ProviderContractPricingItem (version_id)
                                              *─1 MedicalService (via code/id)
                                              *─1 MedicalCategory (authoritative FK)
Claims: ClaimLine *─1 ProviderContractPricingItem (pricingItemId — historical pin)
Benefit Engine: consumes contractPrice + category id — UNTOUCHED (A3)
```

Contract lifecycle touchpoints:
- Contract **activation** check (warning, later hard rule): active contract should have an ACTIVE price list version.
- Contract **suspension/termination** does not archive the version (claims in flight still resolve).
- One provider ↔ many contracts over time; versions belong to contracts. A renewal contract starts at Version 1 with a "copy from previous contract's last version" convenience action.

---

## 12. Migration Strategy — with the A9 verification gate

**Phase M0 — schema (Flyway, additive only):** new tables (§4.1, **including `price_list_validation_findings` — A10**) + `version_id` column. No destructive change.

**Phase M1 — backfill + governance services:** every contract with pricing items gets `PriceListVersion(version_no=1, status=ACTIVE, notes='backfill')`; items tagged. Idempotent, verified by counts. **Includes the Financial Validation Engine (A10) and Version Comparison computation (A11)** so that the very first real version published through the module is already governed.

**Phase M2 — knowledge migration (one-time jobs):** copy `odoo_knowledge.json` + `medical_synonyms.json` content into service aliases (source-tagged); load the reviewed CAT-code mapping sheet. **Source files are read, never modified (A9).**

**Phase M3 — shadow mode + regression gate (MANDATORY, A9):**
- Engine wrapper + import + review screens live; publishing allowed to DRAFT versions only.
- **Regression harness:** run the real provider files already processed by the script (`جاهز/` outputs: بيروت، ابن سينا، الاستشاري، الصفوة، الحكمة + the batch folders) through the integrated pipeline and compare line-by-line against the script's own Excel outputs (same match, same category, same confidence tier). Differences are defects to explain or fix.
- **Pass condition:** 100% functional equivalence on those files (allowing only differences caused by the richer WAAD catalog, each one reviewed and justified). Until this gate passes, the folder script remains the authoritative production workflow and **must not be deleted, modified, or disabled**.

**Phase M4 — pilot provider:** one real provider end-to-end (import → review → Approve Remaining → publish → portal display → a test claim priced against the new version).

**Phase M5 — general availability:** the integrated module becomes the primary workflow. The folder script is retained as the documented fallback (still untouched); its retirement is a separate future decision by you.

**Rollback:** each phase independently reversible — versions can be deactivated back to backfilled Version 1; the old Excel import path and the folder script remain functional throughout.

---

## 13. Risks

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | **Misclassification with financial effect** (wrong category → wrong coverage %) | 🔴 High | Review-first policy (A4): every line human-approved; critical cases individually; hard guards; classification history; catalog FK visible in review. |
| R2 | **Price overwrite breaking historical claims** | 🔴 High (pre-existing today!) | Versioning immutability; `ClaimLine.pricingItemId` pins rows; publish inserts, never updates. |
| R3 | ProcessBuilder fragility (paths, encoding, Python env, zombie processes) | 🟠 Medium | Pinned venv, `-X utf8`, timeouts + kill, stderr capture → FAILED status, single-flight per import, health self-test on startup ("engine check" runs a 3-line sample). The JSON-contract abstraction keeps the sidecar escape hatch open (A1). |
| R4 | Catalog pollution from imported noise | 🟠 Medium | **A6:** nothing enters the catalog without reviewer approval; unknown lines live in staging only; rejected lines never touch the catalog. |
| R5 | Normalization drift (Java vs Python treating Arabic differently) | 🟠 Medium | Normalization lives **only** in the Python engine; Java stores what the engine returns. One implementation. |
| R6 | Large files (5k–100k lines) blocking requests | 🟠 Medium | Async jobs, chunked engine invocations, batched inserts, progress polling. |
| R7 | Concurrent imports for the same contract | 🟡 Low | One in-flight import per contract (status constraint); publish serialized on the contract row. |
| R8 | Upload security (malicious xlsx/pdf) | 🟡 Low | Size/type limits, hash, parsing confined to the engine process, existing auth + new RBAC. |
| R9 | "Approve Remaining" pressed carelessly | 🟡 Low | Enabled only after critical queue is empty; confirmation dialog with counts/sample/total value; fully audited (`BULK_REMAINING`, who, when); audit tab encourages spot-checks; Dashboard exposes per-provider quality. |
| R10 | Regression vs the authoritative script | 🟡 Low | A9 gate (M3): mandatory line-by-line equivalence on real files before the module is trusted; script untouched as reference. |

**Stage-2 Hotfix note:** R2 documents a *pre-existing* exposure (current Excel re-import updates `contract_price` in place while `ClaimLine.pricingItemId` references the row). Recommended fix is this module's versioning rather than a point patch — flagged for your decision on whether an interim guard is wanted (e.g. block re-import price updates on contracts that already have claims).

---

## 14. Suggested Improvements (beyond the request)

1. **Learning loop as a first-class feature** (included): reviewer decisions → aliases → measurably rising first-pass classification rate, visible on the Dashboard (A8).
2. **Price intelligence during review:** show catalog `cost` and the provider's previous-version price next to each line; outlier guard included.
3. **Version diff as the approval artifact:** approvers approve a *diff*, not thousands of rows.
4. **Correction requests SLA view** for the provider-relations team.
5. *(Deferred, separate future decisions)* auto-approval for the top band once Dashboard precision data justifies it (A4); FastAPI sidecar once scale demands it (A1); semantic/embedding matching as a secondary scorer; admin UI for the medical keyword rules.

---

## 15. Final Recommendation

**Proceed with the amended wrap-and-integrate design:**
- **Keep the Python engine untouched (A9)**; add only a JSON entry point beside it; invoke via **ProcessBuilder** (A1) behind a transport-agnostic client interface.
- **Build `modules/medicalclassification` (A7)** — a generic classification engine core with the price-list workflow as its first consumer; staging + critical-queue review + explicit **"Approve Remaining"** (A4/A5) + versioning, with the single schema touch being `version_id` on pricing items.
- **Extend the existing Medical Catalog only (A2)**; services are created solely by reviewer approval (A6).
- **Benefit Engine: zero contact (A3).** Provider Portal stays read-only, gaining category display + correction requests.
- **Ship the Classification Dashboard (A8)** as part of v1 — it is also the evidence base for every future relaxation (auto-approval, thresholds).
- **Gate everything on M3 regression equivalence against the script's real outputs (A9)** — the script remains the authoritative reference until the module proves 100% parity on real provider files.

- **Governance, not just import (A10 + A11):** every published version passes an automated financial validation gate, and every approval is made on a statistical comparison report — making the module a price-list **governance system**, not an importer.

Build order (approved): **MC-0** schema + JSON entry point + CLI client → **MC-1** import & staging → **MC-2** review workspace (critical queue + Approve Remaining) → **MC-3** versioning + Financial Validation Engine + Version Comparison Dashboard + publish gate → **MC-4** classification dashboard → **MC-5** contract/portal touchpoints → **MC-6** knowledge migration → **MC-7** M3 shadow-mode regression gate. Each increment follows the ATEF sub-stage lifecycle (analyze → implement → build → self-review → verify → report → STOP).

---

*PLAN v1.2 — APPROVED. Implementation starts at MC-0. The v1.1 amendments + A10/A11 are binding. The folder script remains untouched and authoritative until the M3 gate passes (A9).*
