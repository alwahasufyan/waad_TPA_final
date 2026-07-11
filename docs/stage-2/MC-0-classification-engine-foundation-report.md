# Medical Classification Engine · MC-0 — Foundation — Completion Report

**Increment:** MC-0 (schema + JSON entry point + CLI engine client)
**Plan:** [provider-price-list-classification-module-plan.md](../plans/provider-price-list-classification-module-plan.md) v1.2 (approved with A10/A11)
**Status:** ✅ Implemented & verified · ⏸ **STOP — awaiting your review & approval before MC-1 (import & staging).**
**Lifecycle:** Analyze → Implement → Build → Self-Review → Verify → Report → **STOP**.

---

## What was built

### 1. Schema — `V70__medical_classification_module.sql` (additive only, idempotent)

| Object | Purpose |
|---|---|
| `medical_services` + `ent_service_aliases` (+seq) | ⚠️ **Discovery:** these entities existed in code (`MedicalService.java`, `ServiceAlias.java`) but their tables were **never created by any migration** (verified against the dev DB). Created now, matching the entities exactly; aliases carry the A2 extension columns (`source`, `weight`, nullable — entity mapping comes in MC-6). |
| `catalog_classification_history` | Classification/mapping audit (per decision: old/new category, source, confidence, who). |
| `price_list_imports` | One row per uploaded provider file (status machine UPLOADED→…→PUBLISHED/FAILED). |
| `price_list_import_lines` | Staging: raw payload + engine result + queue state (`PENDING_BULK` / `NEEDS_REVIEW` / `APPROVED` / `REJECTED`) + reviewer decision incl. `approval_mode = INDIVIDUAL | BULK_REMAINING` (A5 audit). Unknown services live **only** here (A6). |
| `provider_price_list_versions` | Version backbone; `UNIQUE(contract_id, version_no)` + **partial unique index: one ACTIVE per contract**. |
| `price_list_validation_findings` | Financial Validation Engine output (A10): 8 finding types, BLOCKER/WARNING/INFO, OPEN/RESOLVED/WAIVED with audited waivers. |
| `price_list_correction_requests` | Provider-portal correction requests (read-only portal). |
| `classification_settings` (+9 seeds) | Engine paths/timeout, queue threshold (85), A10 validation thresholds (30/100/×5/25), `review.auto_approval.enabled=false`. |
| `provider_contract_pricing_items.version_id` | **The only existing-table change in the whole module** — nullable FK + index (NOT VALID→VALIDATE pattern, zero-downtime). Backfill to "Version 1" happens in MC-1. |

Not touched: claims, claim_lines, benefit_policy_*, Benefit Engine paths (A3), the script's files (A9).

### 2. Python JSON entry point — `classify_json.py` (new file beside the script)

- **A9 honored by construction:** it does **not** reimplement or modify any logic — it calls the authoritative `tpa_service_mapper.process()` into a temp Excel, then converts that output (sheet «الخدمات Services» + «ملخص Summary») to JSON. **Parity with the script is guaranteed structurally**, not by re-testing heuristics.
- Contract: `python -X utf8 classify_json.py --request request.json --out result.json` → `{ok, engine_version, fuzz_engine, summary, total_lines, needs_review_count, lines[]}` with per-line: raw name (bilingual split), code, price, main/sub category, status, needs_review, reason, confidence, match method, reference match, source sheet.
- Errors are also written as JSON (`ok:false, error`) + exit code 1.
- `git status` confirms: the script folder has **zero modified files** — only the new `classify_json.py` added.

### 3. Java engine core — `modules/medicalclassification` (A7 shape)

```
medicalclassification/
├── engine/dto/      ClassificationRequest (channel-tagged: PRICE_LIST/OCR/CLAIM_TEXT/PHARMACY/API)
│                    ClassificationResult, ClassificationLineResult
├── engine/service/  ClassificationEngineClient   ← transport-agnostic interface (A1)
│                    CliClassificationEngineClient ← Phase-1 ProcessBuilder impl
│                    ClassificationEngineException, ClassificationSettingsService
├── entity/          ClassificationSetting
└── repository/      ClassificationSettingRepository
```

- `CliClassificationEngineClient`: temp work dir per run, `-X utf8` + `PYTHONIOENCODING`, configurable timeout with `destroyForcibly()`, single-flight lock, stderr/stdout captured into failure messages, venv auto-resolution (`.venv/Scripts/python.exe` → `.venv/bin/python` → `python`), `healthProblem()` probe.
- `ClassificationSettingsService.isAutoApprovalEnabled()` **returns `false` unconditionally** — A4 is enforced in code, not just config.
- No controllers/endpoints yet (MC-1) — nothing is reachable from the UI or API in this increment.

---

## Build / Verify

| Check | Result |
|---|---|
| Backend compile (`mvn compile`) | ✅ exit 0 |
| Flyway V70 on dev DB | ✅ "Successfully applied 1 migration … now at version v70" |
| All 9 new tables exist | ✅ verified via psql |
| `provider_contract_pricing_items.version_id` + FK + index | ✅ verified |
| 9 settings seeded (auto-approval=false) | ✅ verified |
| **Engine end-to-end on a REAL provider file** (`مختبر الحياة_منظم.xlsx`) | ✅ exit 0 — 152 lines, 140 ready (129 contract-matched + 11 trusted category), **12 needs-review**, JSON structure correct (matched line + review line inspected) |
| Script folder integrity (A9) | ✅ zero modified files; only `classify_json.py` added |
| Backend starts & healthy after migration | ✅ actuator 200 (port 8081) |
| Dev convenience | `engine.script.dir` set to the script folder in dev DB |

**Honest limits of this increment:** the Java `CliClassificationEngineClient` compiles and its Python side is proven end-to-end, but the full Java→Python round-trip will first execute inside MC-1's import job (there is no endpoint yet to drive it). The M3 regression gate (all real files, line-by-line vs the script's Excel outputs) remains the binding verification before the module is trusted (A9).

## Self-Review (ATEF)

- **A1–A11 compliance:** ProcessBuilder not FastAPI (A1) ✅ · no new catalog (A2) ✅ · Benefit Engine untouched — no file under `modules/claim` changed (A3) ✅ · auto-approval hard-disabled in code (A4) ✅ · queue statuses model the hidden-majority + Approve-Remaining flow (A5) ✅ · no service creation at import time — staging only (A6) ✅ · module named/shaped `medicalclassification` with channel-tagged engine core (A7) ✅ · dashboard tables/fields in place, screen comes in MC-4 (A8) ✅ · script untouched (A9) ✅ · findings table + thresholds seeded (A10) ✅ · version/diff data model supports the comparison report (A11) ✅.
- **Hotfix rule:** no Business-Integrity/Financial/Medical/Security/Data bug encountered. The `medical_services` missing-table discovery is a dormant-code gap, not a live defect (nothing references the table at runtime yet) — resolved by V70 rather than flagged as hotfix.
- **Data note for later review:** in the real-file test, a reticulocyte lab test inherited an inpatient category (CAT003) from the *contract reference* — a reference-data quality issue the review screen + catalog governance are designed to catch. Kept as evidence for MC-2/M3.

## Files

**Added (backend):** `db/migration/V70__medical_classification_module.sql`; `modules/medicalclassification/…` (8 Java files, listed above).
**Added (script folder):** `classify_json.py` only.
**Modified:** `docs/plans/provider-price-list-classification-module-plan.md` (v1.2 — A10/A11 integrated).
**No frontend change. No Benefit Engine change. No existing script file change.**

## Next (after your approval)

**MC-1 — Import & Staging:** upload endpoint + file storage, async import job calling the engine client, staging persistence with queue banding, Version-1 backfill of existing contract pricing items, imports-list screen. Then STOP again.

---

*STOP. Awaiting your approval of MC-0 before starting MC-1.*
