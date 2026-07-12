# MC-7 Lite — Regression & Trust Gate Report

**Status: CLOSED**
**Date: 2026-07-12**
**Scope: lightweight trust gate using real price-list files — not a testing framework.**

## Method

Three real, unmodified provider price-list files were imported through the running Docker stack (backend container `/app/tools/classification-engine`, `/opt/engine-venv/bin/python`) via the standard MC-1 import flow. Source files were only **read**, never modified or deleted, per instruction. Results were pulled directly from `price_list_imports` (Postgres, `waad-postgres-dev`).

## Results

| # | File | Provider/category hint | Total lines | Known | Unknown | Low-confidence | Duplicates | Engine version | Fuzz engine | Exec time | Errors |
|---|------|------------------------|--------------|-------|---------|------------------|------------|-----------------|-------------|-----------|--------|
| 16 | fresh2.xlsx | Al-Amal — dental | 102 | 102 | 0 | 0 | 9 | waad-mce-cli/1.0.0 | rapidfuzz | 2021 ms | none |
| 17 | razi.xlsx | Razi — lab | 480 | 318 | 0 | 162 | 52 | waad-mce-cli/1.0.0 | rapidfuzz | 959 ms | none |
| 18 | akeed.xlsx | Akeed — lab | 511 | 430 | 0 | 81 | 0 | waad-mce-cli/1.0.0 | rapidfuzz | 931 ms | none |

Final statuses: #16 `CLASSIFIED`, #17 `IN_REVIEW` (routed for manual review of the 162 low-confidence lines, as designed), #18 `CLASSIFIED`.

## Comparison against prior/reference results

| File | This run (known/unknown/low-conf) | Reference (old script / prior run) | Verdict |
|------|-------------------------------------|--------------------------------------|---------|
| fresh2.xlsx (Al-Amal) | 102 / 0 / 0 | 102 / 0 / 0 | **Exact match** |
| razi.xlsx (Razi) | 318 / 0 / 162 | 318 / 0 / 162 | **Exact match** |
| akeed.xlsx (Akeed) | 430 / 0 / 81 | 428 / 0 / 83 | **+2 known / −2 low-confidence** |

The Akeed discrepancy is not a regression: it is explained by the MC-6 Lite learning loop. Two service names that were low-confidence/unmatched in the earlier reference run had since been learned (via reviewer approval in an intervening session) as aliases in `ent_service_aliases`, so this run correctly auto-classified them as `KNOWLEDGE_BASE` hits instead of flagging them low-confidence. This is the intended behavior of the learning loop improving results over time, not drift or corruption.

Import #18 specifically showed **12 lines** classified via `classificationSource=KNOWLEDGE_BASE`, confirming learned aliases are actively used and improving — not just preserving — classification quality across independent import runs.

## No engine/runtime failures

- No Python-missing errors (backend container: `Python 3.10.12` confirmed via `waad.ps1 doctor`).
- No engine path errors (all 3 imports resolved `/app/tools/classification-engine` correctly).
- No backend HTTP 500s across all 3 imports.
- `error_message` column empty for all 3 rows.

## Financial safety / MC-4C non-interference

A live MC-4C regression check (price correction) was run against this same rebuilt image immediately after the 3 imports (see `MCE-final-closure-report.md` §7 for the full trace): contract price list version count unchanged (5→5), audit count incremented by exactly 1 (20→21), correction applied and reflected immediately. No financial price data was touched by the classification/import runs — imports and MC-4C edits operate on independent version/row paths as designed.

## Acceptance criteria — verified

- [x] At least 2 real Excel files processed successfully inside Docker (3 processed: fresh2.xlsx, razi.xlsx, akeed.xlsx).
- [x] No engine path errors.
- [x] No Python-missing errors.
- [x] No backend 500s.
- [x] Results stable and documented (table above).
- [x] Learned aliases improve or preserve classification quality (Akeed +2 known, explained).
- [x] Unknown/low-confidence rows are reasonable and routed to review (#17 → `IN_REVIEW`, 162 lines held for manual review; 0 unknown across all 3 files).
- [x] No financial price data corrupted (verified via MC-4C regression check, same image).
- [x] Contract price workflow remains unaffected (same check).
- [x] Final regression/trust report created (this document).

**MC-7 Lite: CLOSED.**
