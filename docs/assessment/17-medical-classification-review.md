# 17 — Medical Service Classification Review

**Score: 30/100 (F+)** · Reviewed as requested: current provider price import process, evaluated for taxonomy/classification/mapping/import quality/confidence/learning, with a transformation recommendation into an Enterprise Medical Classification Engine.

---

## Current State: Exact-Match Only, No Fuzzy Matching, No Confidence Scoring

### How a raw provider Excel price list maps to the system's taxonomy today

`ProviderContractPricingExcelService`'s import logic performs a **two-step exact lookup**: first by category **code**, then by exact case-insensitive category **name**. If neither matches, the category assignment is left `null` and the raw Excel text is stored only as a free-text fallback (`category_name`, not FK-linked) — the pricing item is imported "uncategorized," requiring manual follow-up.

There is **no fuzzy string matching, no similarity/Levenshtein scoring, and no confidence threshold** anywhere in the application's runtime import path (confirmed via exhaustive search for `fuzzy|similarity|confidence|levenshtein` across the Java codebase — zero hits).

### The one "fuzzy" reconciliation that exists is a historical, one-time SQL migration — not application capability

Migration `V43__map_pricing_items_categories.sql`'s own header comment states its purpose explicitly: *"Unifies text-based category names into foreign key relations to avoid performance impact from runtime fuzzy matching."* This confirms the team **deliberately chose not to build runtime fuzzy matching** into the application (a reasonable performance-driven decision at the time) and instead patched historical bad data once via a two-pass SQL migration (exact match, then a regex strip of parenthetical suffixes like `(IP)`/`(OP)` followed by re-match). This is a one-time data-fix, not a reusable, ongoing capability.

### Repeatability gap: real classification work happens outside the application

The clearest evidence in this entire assessment that classification is **not currently a repeatable, in-app process**: a collection of ad-hoc Python scripts and manually-prepared Excel workbooks live in the repository root and a `draft/` folder, with names that directly describe manual classification work:
- `draft/classify_health_facilities.py`
- `draft/build_dar_shifa_import_ready.py`
- `draft/final_16_rules_export.py`
- `draft/دار_الشفاء_جاهز_للاستيراد.xlsx` ("Dar Al-Shifa ready for import")
- `draft/قائمة اسعار خدمات دار الشفاء مصنفة.xlsx` ("Dar Al-Shifa services price list — classified")
- `draft/نتيجة_classify_contextual.xlsx`, `draft/نتيجة_classify_hospital.xlsx`
- Root-level: `analyze_excel.py`, `organize_operations.py`, `tmp_pricingcheck.py`, and several other scratch scripts

These file names describe exactly the workflow that should exist inside the application: take a hospital's raw price list, classify it against the medical taxonomy, produce an "import-ready" result. **Today, this work is done by a developer, offline, per provider, using one-off Python scripts and hand-edited Excel files, then fed into the application's exact-match importer.** This means onboarding a new provider's price list currently requires developer time proportional to how messy that provider's original spreadsheet is — a real operational bottleneck and a scalability ceiling on how many providers the TPA can onboard per unit of engineering time.

## Evaluation Against Mission Criteria

| Criterion | Assessment |
|---|---|
| Medical taxonomy | ✓ Well-designed — self-referencing hierarchy, multi-root closure table, dual-language fields (see `06`/`15` reviews) |
| Classification | ✗ Exact-match only; no runtime fuzzy/semantic matching |
| Categories / Subcategories | ✓ Structurally sound in the schema; the taxonomy itself is not the problem |
| Provider mappings | ⚠ Functional once manually classified, but the classification step itself is the bottleneck |
| Import quality | ⚠ Reasonable audit trail (`provider_service_price_import_logs`, error logging) for what *does* get imported; silent "uncategorized" fallback for what doesn't |
| Excel workflow | ✓ Present and functional as a mechanical import path; not the issue |
| Confidence | ✗ Does not exist — every match is binary (exact hit or unlinked text), no partial-confidence scoring or human-review-queue concept |
| Learning | ✗ Does not exist — the system does not improve its own matching over time; every new provider's messy spreadsheet starts from zero, re-solved by a human each time |

## Business Impact

Per the Constitution's "Configuration Before Code" and "Reuse Before Creation" principles, the current state means every new provider onboarding partially **re-derives** classification logic ad hoc (new Python script, new manual Excel pass) rather than reusing or improving a shared, in-application capability — the opposite of the Constitution's intended pattern. This is not a data-quality defect in the existing taxonomy (which is sound) — it is a **missing capability** for turning raw, messy, real-world provider data into taxonomy-linked pricing data at the rate the business needs to grow.

---

## Recommendation: Transform Into an Enterprise Medical Classification Engine

This should be scoped as **Epic 6** in `21-enterprise-roadmap.md`. Per Evolution Policy discipline, this is framed as an **addition alongside** the existing exact-match importer (which should remain, since exact matches are cheap and should never be routed through a heavier process), not a replacement of it.

### Phase 1 — Fuzzy Matching with Confidence Scoring
- Add a string-similarity layer (e.g., normalized Levenshtein/Jaro-Winkler distance, or a trigram-based approach — PostgreSQL's `pg_trgm` extension is a strong, low-operational-cost fit given the platform is already PostgreSQL) that runs **only** when the existing exact-match lookup fails, preserving the V43 migration's original performance rationale.
- Every fuzzy match produces a **confidence score** (e.g., 0–100%), not a silent binary decision.
- Matches above a high-confidence threshold (e.g., >90%) can auto-assign with an audit flag noting it was fuzzy-matched, not exact; matches below that threshold are queued for human review rather than left as unlinked free text.

### Phase 2 — Human-Review Queue (turns manual work into structured work, not eliminated work)
- A dedicated review UI (extending the existing `MedicalCategoryExcelController`/pricing-import admin surface) showing: raw provider text, top 3–5 candidate category matches with confidence scores, and a one-click "confirm"/"reassign" action.
- This directly replaces the current "developer writes a Python script" workflow with "any authorized staff member resolves a queue in the application" — a genuine capability upgrade, not just an automation improvement, because it removes the developer-dependency bottleneck entirely.

### Phase 3 — Learning Loop
- Every human-confirmed match (from Phase 2, or historical corrections) becomes a labeled training example.
- Start simple: maintain a **synonym/alias table** (the `ServiceAlias` entity already exists in `medicaltaxonomy` — confirm whether it's actually being populated/used, or is itself an underused capability) that grows every time a human confirms "raw text X means category Y" — future imports of the same or similar raw text auto-resolve with high confidence immediately, without needing a fresh similarity computation.
- This is the most Constitution-aligned "learning" implementation available (Configuration Before Code: the alias table is data, not model weights) and avoids introducing ML-infrastructure complexity that would be disproportionate to the problem size.

### Phase 4 — Retire the Offline Scripts
- Once Phases 1–3 are live and proven on at least one real new-provider onboarding, formally retire the `draft/` folder's classification scripts and document the in-application workflow as the canonical path — closing the loop the Evolution Policy describes (Extend → Deprecate → Monitor → Remove).

### What This Explicitly Does Not Require
- No machine-learning model training/hosting infrastructure — string-similarity plus a growing alias table is sufficient for this problem's actual shape (a bounded, slowly-growing taxonomy, not an open-vocabulary NLP problem).
- No change to the existing exact-match fast path, which should remain the default for the (likely common) case of well-formatted provider spreadsheets.

---

## Findings Requiring Action

1. **(High)** Scope and build Phase 1 (fuzzy matching + confidence scoring) as the highest-value, lowest-risk first step — it directly reduces the number of "uncategorized" items without touching the existing exact-match path.
2. **(High)** Scope and build Phase 2 (review queue UI) — this is what actually removes the developer-dependency bottleneck, which is the core business problem identified.
3. **(Medium)** Confirm whether `ServiceAlias` is currently populated/used; if underused, it is the natural foundation for Phase 3's learning loop.
4. **(Low)** Once proven, formally retire the offline classification scripts per the Evolution Policy's deprecation sequence.

## Decision

**❌ Reject current state as a scalable process / ✅ Approve the phased transformation plan for Epic 6.** The underlying taxonomy is well-designed (score would be much higher if this review were purely about the data model); the score reflects the classification *process* gap specifically, which is real, well-evidenced, and directly addressable without disrupting the existing exact-match import path.

---

*Continue to [`18-mobile-review.md`](./18-mobile-review.md).*
