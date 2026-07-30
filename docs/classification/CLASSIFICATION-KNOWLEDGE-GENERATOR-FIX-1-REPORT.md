# CLASSIFICATION-KNOWLEDGE-GENERATOR-FIX-1 — Fix Odoo Knowledge Generator and Align TAX-1 Codes

**Status: READY FOR REVIEW.** Generator fixed locally. Candidate output regenerated for testing only. Nothing committed. Not pushed.

## 1. Root cause of `CAT-ROOM` overclassification

Confirmed in `build_product_kb.py`'s `classify(name, category)` (pre-fix):

```python
def classify(name, category):
    text = normalize(f"{name} {category}")          # <-- bug: name + raw Odoo folder, one string
    for code, needles in RULES:
        if any(normalize(n) in text for n in needles):
            return code, "name_rule"                 # <-- mislabels the match source
    ...
```

The function concatenated the service **name** and the raw Odoo **category/folder** text into a single string before running the keyword rules, directly contradicting its own docstring ("service-name rules first, Odoo category only as a conservative fallback"). Any product whose Odoo folder happened to contain a `CAT-ROOM` keyword (`"room"`, `"ward"`, `"إقامة"`, `"اقامة"`, `"غرفة"`, `"جناح"`) — regardless of what the product itself was — got tagged `CAT-ROOM` and falsely labeled `sourceRule: "name_rule"` (implying the *name* matched, when it never did). This affected **4,417 of 11,415 entries (38.7%)**, including confirmed drugs (`Ciprofloxacin 200mg/100ml`), surgical devices (`2 STENT`), and procedures (`Abdominoplasty`, `Wound stitch- A`).

**A second, related defect was found during this ticket's validation** (not previously reported): the `RULES` keyword list uses raw substring matching (`needle in text`), so the two-letter keyword `"ct"` (meant to catch the "CT scan" abbreviation) also matches inside unrelated words that merely contain the letters "c" and "t" adjacently — e.g. `conjun**ct**ival`, `Ba**ct**ec`, `pro**ct**algia`-style tokens. Sample validation (§7) caught real lab-test names (`Bactec` cultures, `H.Pylori` panels, `Procalcitonin`) and one eye-surgery name (`CONJUNCTIVAL TUMOR EXCISION`) being pulled into `CAT-IMG-ADV` purely by this substring collision. This bug is independent of the name/category-concatenation bug and pre-dates it (it was already present in the keyword matching itself), but it directly undermines the same accuracy goal, so it was fixed in the same pass.

## 2. Generator change

`tools/classification-engine/build_product_kb.py`, function `classify()`:

1. **Match scope fix**: keyword rules (`RULES`) now search only `normalize(name)`. The Odoo `category` is consulted **only** through the pre-existing `CATEGORY_FALLBACKS` dict — an exact-match lookup on the literal category string, never a free-text keyword search — exactly matching the "name first, category only as a conservative fallback" design intent.
2. **Word-boundary fix**: both the search text and each keyword are padded with a leading/trailing space (`f" {normalize(name)} "` / `f" {normalize(n)} "`) before the substring test, so short keywords like `"ct"` can only match a whole token/phrase, not a substring inside an unrelated word.
3. **Metadata preservation** (per this ticket's explicit requirement): each output entry now also carries `"odooCategory": category` — the raw Odoo folder path is preserved as metadata for provenance/debugging, but (per fix #1) no longer has any influence on the classification decision itself. All previously-existing fields (`cat`, `name`, `source`, `sourceRule`) are unchanged.

No other logic, no `RULES`/`CATEGORY_FALLBACKS` table content, and no CLI/argument behavior was changed.

## 3. Before/after metrics

A candidate file was regenerated **for testing only** (not written into the tracked `odoo_knowledge.json`) using the actual raw Odoo export found in the repo, `تحاليل...` → specifically `tools/classification-engine/متغير المنتج  (product.product).xlsx` (14,691 rows; correctly `.gitignore`d, never committed). Its Arabic headers (`الاسم`→name, `فئة المنتج`→categ_id) were renamed for the run; no other change to the input.

| | Current committed (`odoo_knowledge.json`, old CAT0xx codes, pre-Codex) | Codex's buggy regeneration (working tree, blocked by prior ticket) | **Candidate — fixed generator** |
|---|---|---|---|
| Total entries | 9,904 | 11,415 | **2,218** |
| `CAT-ROOM` entries | n/a (old taxonomy) | **4,417 (38.7%)** | **52 (2.3%)** |
| `CAT-ROOM` sample quality | n/a | drugs, stents, surgeries mixed in | genuine room/stay/admission items only (§7) |
| Cross-category contamination (e.g. lab tests in `CAT-IMG-ADV`) | n/a | present (`Bactec`, `H.Pylori`, `Procalcitonin` in imaging) | fixed — same items now correctly resolve to `CAT-LAB` or are excluded |
| Resolves to a real DB category by name (§4) | 100% (9,904/9,904) | 37.7% (4,304/11,415) | 25.0% (555/2,218) |

The resolution-rate drop between the buggy regeneration (37.7%) and the fixed candidate (25.0%) is **expected and correct, not a regression**: the buggy version's higher raw count was inflated by exactly the false matches this fix removes (e.g. `CAT-IMG-ADV` fell from 3,522 → 262 entries once "ct"-substring and category-leak false positives were removed — precisely because it had previously been erroneously catching lab tests and eye-surgery entries as if they were imaging). A smaller, precisely-classified knowledge base is the intended outcome: unmatched services correctly fall through to `NEEDS_REVIEW` (§8) rather than being wrongly captured under a confident-looking wrong label. This matches business rule #7 ("accuracy is more important than reducing PENDING_REVIEW count").

The DB-name resolution rate itself (25–52%, depending on candidate version) is governed entirely by the TAX-1/DB-category alignment gap (§4, §6), not by anything in this generator — see below.

## 4. Current taxonomy compatibility

Re-confirmed against `V57__import_standard_medical_categories.sql` (31 `CAT0xx` categories) using the same canonicalized-name resolution `CategoryResolutionService` actually performs at runtime (never by code — see that class's own documented rationale). Independent of this generator fix, **13 of TAX-1's 33 categories (39%)** have a name that exactly matches a currently-seeded DB category; the other 20 are legitimate finer-grained splits of DB categories that combine several concepts into one row (e.g. DB `CAT023` bundles doctor fees + psychiatric treatment + labs + X-ray + diagnostics into a single category; TAX-1 splits this into five). This part of the picture is unchanged from the prior validation ticket and is **not something a generator fix can address** — it requires either a DB migration or an explicit mapping layer (§6).

## 5. TAX-1 status — an important, previously-unreported discovery

While tracing exactly how `odoo_knowledge.json` fits into the classification pipeline (to scope this fix correctly), two things were confirmed that materially change the risk picture:

1. **`odoo_knowledge.json` is not currently read by any pipeline code.** Grepping every `.py` file in `tools/classification-engine/`, the only scripts that touch `odoo_knowledge.json` are its two generators (`build_odoo_kb.py`, `build_product_kb.py`) — nothing reads it. The live engine (`classify_json.py` → `tpa_service_mapper.process()`) loads a **different, already-committed file**: `tools/classification-engine/official_knowledge.json` (tracked in git, unmodified, 4,983 entries). This means the CAT-ROOM bug in the *working-tree* `odoo_knowledge.json` was never at risk of reaching production directly — it is currently dead output from an experiment, not a live artifact.
2. **The TAX-1/DB name-resolution gap already exists in the live, committed `official_knowledge.json` file — today, independent of this ticket.** Running the identical resolution check (§4) against `official_knowledge.json`'s 4,983 entries: **37.0% resolve** (1,843/4,983) — essentially the same shortfall found in Codex's candidate files. `official_knowledge.json`'s entries already carry `legacy_codes` provenance (mapping each TAX-1 code back to the old CAT0xx code it was migrated from) and `source: "FACILITY_READY"` (built from human-reviewed, approved facility price lists in the `جاهز/` folder — a much higher-quality source than raw Odoo exports), so its classifications themselves are trustworthy; what's missing is purely the DB-side connective tissue (§6). This is a **pre-existing, already-shipped gap**, not something introduced by this generator or by Codex's `odoo_knowledge.json` experiment. It is flagged here because it is directly relevant to any future decision about Option B/C, but fixing it is out of this ticket's scope (no backend changes were made — see §9/§10).

## 6. Recommended mapping/migration path

Per the ticket's preference (Option A or B; Option C requires separate approval), and per business rule #6 ("do not silently remap to wrong categories"), the following collapse-mapping is proposed as a **suggestion-only overlay** (would need a small, separate follow-up ticket to actually wire it into `CategoryResolutionService.java` as an additional lookup step before falling back to `CATEGORY_UNRESOLVED` — not implemented in this ticket, no backend files were touched):

| TAX-1 code | Proposed DB fallback | Confidence | Rationale |
|---|---|---|---|
| `CAT-PRACT-FEE`, `CAT-LAB`, `CAT-IMG-DIAG`, `CAT-PSYCH-DRUG`, `CAT-PSYCH-SESS` | `CAT023` | High | All five are literal sub-phrases already present in `CAT023`'s combined Arabic text |
| `CAT-DENT-PROSTHO`, `CAT-DENT-ORTHO`, `CAT-DENT-IMPLANT` | `CAT031` | High | `CAT031`'s text is literally "تركيب-تقويم-زراعة" (prosthetics-orthodontics-implants) — the exact three TAX-1 splits |
| `CAT-DME`, `CAT-MED-SUP`, `CAT-SURG-MAT` | `CAT026` | Medium-High | All three are device/equipment/supply concepts; `CAT026` is WAAD's only equipment bucket |
| `CAT-DRUG` | `CAT025` | High | `CAT025` = "routine treatments and medications per prescription" |
| `CAT-SURGERY` | `CAT015` | Medium | `CAT015` ("جراحة للمريض خارج المستشفى") is the only dedicated surgery bucket in the DB; note it is scoped to outpatient in its name, a residual scope mismatch worth a product decision |
| `CAT-DIAGNOSTIC` | `CAT012` | Medium | `CAT012` bundles imaging + sample analysis + diagnostic exams, closest conceptual match |
| `CAT-ONCOLOGY` | `CAT016` | High | Same concept, different wording only |
| `CAT-DENT-EMERG` | `CAT028` | Medium | `CAT028` covers routine exam/extraction; closest available dental bucket |
| `CAT-ICU`, `CAT-CCU`, `CAT-ANESTHESIA`, `CAT-DAY-CARE` | *(no confident mapping)* | — | No existing DB category is a defensible semantic match; recommend leaving these **unmapped** so they correctly fall to human review rather than guessing |

This table is a **proposal for the next ticket to review and decide on**, not something implemented here. If approved, the safe next step is a small, explicit JSON/DB mapping table consumed as a second-chance lookup in `CategoryResolutionService` (Option B) — never a code-based shortcut, and never silently applied without a human-reviewable, versioned mapping file. A full TAX-1 migration (Option C — actually splitting `medical_categories` rows to match TAX-1 1:1) remains available as a longer-term option but requires explicit product/medical approval per this ticket's scope, and was not attempted here.

## 7. Sample validation results

All samples drawn from the fixed candidate (`build_product_kb.py` after both fixes, run against the real Odoo export).

- **30 drugs (`CAT-DRUG`)**: all genuine medication/injection-administration entries (`Drug Allergens`, `IV Injection without drug`, `ادوية خارجية`, injection-route lines). No surgeries or room items present.
- **30 surgeries (`CAT-SURGERY`)**: all genuine surgical procedures (`استئصال اللوزتين` — tonsillectomy variants, `Aortic aneurysm surgery`, `Advance surgery by use laser surgery`). No drugs or room items present.
- **30 imaging (`CAT-IMG-ADV`)**: all genuine CT/MRI entries after the word-boundary fix (`CT Brain`, `CT Angio Carotid`, `Abdomen pelvis - MRI`). One residual ambiguous case remains: `BT/CT` / `BT BT-CT`, where "CT" is the lab abbreviation for "Clotting Time" in this specific source row, not "CT scan" — a genuine source-text ambiguity (same two-letter token, two valid medical meanings) that no keyword-boundary fix can resolve; flagged here rather than silently left in either bucket.
- **30 X-ray/diagnostic imaging (`CAT-IMG-DIAG`)**: all genuine X-ray view entries (`KNEE AP X-ray`, `CHEST PA & LAT x-ray`). Clean.
- **30 labs (`CAT-LAB`)**: all genuine lab test entries (`Blood Culture`, `H.Pylori` panel, `Alkaline Phosphatase`, `Procalcitonin` — correctly relocated here instead of the old false `CAT-IMG-ADV` placement). Clean.
- **30 room/admission (`CAT-ROOM`)**: all genuine accommodation entries (`إقامة بغرفة خاصة`, `إقامة بغرفة VIP`, `اقامه بجناح بسرير واحد`, NICU/ICU stay lines). Two edge cases use the anatomical sense of "غرفة" (the eye's *anterior chamber*, e.g. `عملية تنظيف الغرفة الامامية من النزيف`) rather than "room" — an Arabic-homonym false positive, much narrower in scope than the pre-fix drug/stent/surgery pollution, and worth a follow-up keyword refinement (e.g. excluding phrases containing "الغرفة الامامية") but not a blocker.

**Confirmed: `CAT-ROOM` is no longer assigned to drugs, stents, or surgeries because of Odoo folder text** — the specific defect named in this ticket's validation requirement #6 is resolved.

**Unresolved/ambiguous rows remain review-gated (requirement #7)**: nothing in this generator fix touches `ImportProcessingService`'s `CATEGORY_UNRESOLVED` flagging or the `PENDING_BULK`/`NEEDS_REVIEW` banding logic (`ImportProcessingService.java:152-169`, unchanged) — any row whose suggested category doesn't resolve still forces `NEEDS_REVIEW`, and any pricing item without a resolved category still gets `requiresReview = true` (`ProviderContractPricingItem`, from `PROVIDER-PRICE-IMPORT-REVIEW-1`) and is blocked from claims by `ClaimMapper.processEngineCalculations()` before the coverage engine is ever consulted. No backend file was touched to verify this — it was re-confirmed by reading the unchanged Java source, not by re-running those tests (out of scope, no Java changed).

**Sensitive data check (requirement #8)**: the candidate `odoo_knowledge.json`-shaped output contains only `{cat, name, source, sourceRule, odooCategory}` — generic service/product names and Odoo folder labels, no prices, no provider identifiers, no patient data. Scanned for suspicious patterns (long digit runs, `@`) — negligible (2 hits out of thousands, both harmless service codes). Consistent with the prior validation ticket's finding.

## 8. Are the generated files safe to commit?

**Not yet, and not requested in this ticket.** The generator fix (§2) is verified correct and safe to commit on its own — it is a pure bug fix, changes no I/O contract other than adding one new metadata field, and is covered by the before/after evidence in §3/§7. However:

- The **candidate regenerated knowledge file** itself was produced for **testing only**, from a `.gitignore`d raw Odoo export, and is not written into the tracked `tools/classification-engine/odoo_knowledge.json` — per this ticket's explicit "Do not commit generated artifacts... yet."
- The deeper TAX-1/DB-alignment gap (§4, §5, §6) is unresolved and applies equally to the already-committed, currently-live `official_knowledge.json` — fixing the generator does not, and cannot, fix that gap. Whether/how to close it (mapping layer vs. migration) needs a decision on a future ticket per §6.
- `official_taxonomy.json` remains unstaged, per this ticket's explicit scope.

## 9. Files changed

- `tools/classification-engine/build_product_kb.py` — the two fixes in §2 (word-boundary + name-only matching, `odooCategory` metadata field added). Still untracked (was untracked before this ticket too — this is a working-tree-only Python file, not previously committed).

No other file in the repository was modified. No database migration was added. No backend (`.java`) or frontend (`.jsx`/`.js`) file was touched.

## 10. Files intentionally not staged

- `tools/classification-engine/build_product_kb.py` — fix is ready, but per "do not commit until approved," left unstaged pending review of this report.
- `tools/classification-engine/odoo_knowledge.json` — still the Codex-regenerated, buggy version from the prior tickets; **not overwritten with the fixed candidate** (the candidate was written only to the session scratchpad, outside the repository, per "produce a regenerated candidate file for testing only").
- `tools/classification-engine/official_taxonomy.json` — unchanged, still untracked, per explicit scope.
- `tools/classification-engine/odoo_knowledge.legacy.json`, `tools/classification-engine/__pycache__/` — unchanged, still untracked/unsafe, per prior tickets' findings.

## 11. No push confirmation

Nothing was staged, committed, or pushed. `git status --short` for the classification-engine directory shows exactly one file's state changed from the prior ticket: `build_product_kb.py` remains untracked but now contains the fix (working-tree edit only). No destructive operation, deletion, or revert was performed on any generated file.

---

**CLASSIFICATION-KNOWLEDGE-GENERATOR-FIX-1 READY FOR REVIEW** — the `CAT-ROOM` overclassification bug (and a related keyword-substring bug found during validation) are fixed and verified via a real regenerated candidate (drugs/surgeries/stents no longer land in `CAT-ROOM`; sample-checked across all 6 required categories). The deeper TAX-1-vs-DB-category alignment gap is unchanged by this fix, is not new (it already affects the currently-live `official_knowledge.json` at a nearly identical 37% resolution rate), and is proposed for a follow-up ticket using the Option B mapping table in §6 — pending explicit approval, consistent with this ticket's scope of not implementing Option C.
