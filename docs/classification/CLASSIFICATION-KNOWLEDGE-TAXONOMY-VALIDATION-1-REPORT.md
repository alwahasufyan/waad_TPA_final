# CLASSIFICATION-KNOWLEDGE-TAXONOMY-VALIDATION-1 — Validate Updated Odoo/WAAD Knowledge Codes Before Commit

**Status: BLOCKED.** Not committed. Not pushed. Nothing deleted or reverted.

## 1. Executive summary

The regenerated `odoo_knowledge.json` is **not safe to commit**. Two independent, code-verified problems compound each other:

1. **A real generator bug** in `build_product_kb.py`: its `classify()` function concatenates the service **name** and the raw Odoo **category** label into one search string before matching keyword rules, contradicting its own docstring ("service-name rules first, Odoo category only as a conservative fallback"). As a direct result, **4,417 entries (38.7% of the entire file) — including drugs (`Ciprofloxacin 200mg/100ml`), surgical devices (`2 STENT`), and procedures (`Abdominoplasty`, `Wound stitch- A`)** — were mis-tagged `CAT-ROOM` ("private room accommodation") purely because their *Odoo category folder* happened to contain a room/ward/accommodation word, not because of anything in the service name itself. Every one of these is mislabeled `sourceRule: "name_rule"`, falsely implying the service name itself matched.
2. **No migration or mapping layer** connects the new "TAX-1" category codes (`CAT-LAB`, `CAT-IMG-ADV`, `CAT-ROOM`, ...) to the database's actual seeded `medical_categories` (`CAT001`–`CAT031`, from `V57__import_standard_medical_categories.sql`). Verified against the real runtime resolution mechanism (`CategoryResolutionService`, which matches by **canonicalized Arabic/English name**, never by code): only **13 of the 33 TAX-1 categories (39%)** have a name that exactly matches an existing DB category; the other 20 are new, finer-grained splits of existing combined DB categories that do not exist as DB rows yet.

Combined, these two problems mean that if this file were wired into the live classification engine today: **only 37.7% of the regenerated knowledge base's 11,415 entries would resolve to a real category** (down from **100% of the 9,904 entries in the currently-committed file**) — a severe regression, not the improvement the ticket intends. The remaining 62.3% (7,111 rows) would correctly fall back to `NEEDS_REVIEW` (the safety net works), but that is the **opposite** of the stated goal: "align old Odoo/legacy service codes with WAAD's newer medical dictionary codes so fewer valid price-list rows go to review unnecessarily."

**Recommended option: D — do not commit / keep local WIP**, with a follow-up path toward Option C (mapping layer) once the generator bug is fixed. See §13.

## 2. Why Codex changed the Odoo/WAAD codes

Confirmed by reading `build_product_kb.py`'s own docstring and `tpa_service_mapper.py`'s TAX-1 loader:

> "The legacy exporter used CAT023/CAT004-style codes. The production engine accepts only TAX-1 codes (CAT-LAB, CAT-SURGERY, ...), so this importer uses service-name rules first and the Odoo category only as a conservative fallback."

The old knowledge file used WAAD's 31 existing, broad `medical_categories` codes directly (`CAT001`–`CAT031`). Several of these are large catch-all buckets — most notably `CAT023`, which bundles specialist doctor fees, psychiatric treatment, lab tests, X-rays, *and* diagnostic imaging into one category. The intent of the regeneration is to introduce a finer-grained taxonomy ("TAX-1", `official_taxonomy.json`, 33 new `CAT-*` codes) so that, e.g., a lab test and an MRI are no longer both filed under the same generic bucket. This is a legitimate, well-motivated goal, and the design itself (see `official_taxonomy.json`'s 33-category list) is deliberate and internally consistent — not random.

The problem is execution, not intent: the new taxonomy was built and validated only within the Python tooling's own closed world (`tpa_service_mapper.load_approved_categories()` enforces "exactly 33 CAT-* entries" and rejects anything else), without the corresponding database migration or a mapping layer connecting it back to what the running system (and its `medical_categories` table) actually recognizes.

## 3. Current DB taxonomy codes from migrations

`medical_categories` is seeded across several migrations; the currently-authoritative full set comes from `V57__import_standard_medical_categories.sql` ("Import Medical Categories (33 Categories)" — 31 `CAT0xx` rows + 2 `SUB-INPAT-*`/`SUB-*` policy rows, though the migration's own header count of "33" doesn't quite match; there are 31 `CATxxx` + 6 `SUB-*` = 37 total rows including the earlier `V25` seed):

| Source | Codes |
|---|---|
| `V57__import_standard_medical_categories.sql` | `CAT001`–`CAT031` (31 rows), plus 6 `SUB-INPAT-*` sub-codes |
| `V25__seed_data.sql` | `CAT-IP`, `CAT-OP`, `CAT-IP-GEN`, `CAT-IP-NURSE`, `CAT-IP-PHYSIO`, `CAT-IP-WORK`, `CAT-IP-PSYCH`, `CAT-IP-MATER`, `CAT-IP-COMPL`, `CAT-OP-GEN`, `CAT-OP-RAD`, `CAT-OP-MRI`, `CAT-OP-DRUG`, `CAT-OP-EQUIP`, `CAT-OP-PHYSIO`, `CAT-OP-DENT-R`, `CAT-OP-DENT-C`, `CAT-OP-GLASS` — a **separate, higher-level benefit-package taxonomy**, unrelated to service classification |

The service-classification taxonomy that the classification engine actually targets is the `V57` set (`CAT001`–`CAT031`). No migration anywhere in `backend/src/main/resources/db/migration/` (`V1`–`V99`) creates any `CAT-LAB`, `CAT-IMG-ADV`, `CAT-SURGERY`, or any other TAX-1-style code as a `medical_categories` row.

## 4. Regenerated `odoo_knowledge.json` code coverage

| | Old (committed) | New (working tree) |
|---|---|---|
| Total entries | 9,904 | 11,415 |
| Fields per entry | 2 (`cat`, `name`) | 4 (`cat`, `name`, `source`, `sourceRule`) |
| Distinct `cat` codes used | 16 (all `CAT0xx`, all real DB codes) | 19 (all `CAT-*` TAX-1 codes, none are DB codes directly) |
| Entries whose `cat` **resolves to a real DB category via the actual runtime mechanism** (name-based, see §7) | **9,904 / 9,904 = 100%** | **4,304 / 11,415 = 37.7%** |

Top 5 most-used new codes by entry count: `CAT-ROOM` (4,417 — **all unresolved, see §1 bug**), `CAT-IMG-ADV` (3,522 — resolved), `CAT-IMG-DIAG` (1,109 — unresolved), `CAT-LAB` (801 — unresolved), `CAT-DENT-ROUTINE` (503 — resolved). Full top-50 breakdown, unresolved list, and 30-row samples are in the validation script output referenced in §16.

## 5. `odoo_knowledge.legacy.json` comparison

Confirmed **byte-for-byte structurally identical** (`json.dumps(..., sort_keys=True)` diff, both directions) to the currently-committed, working `odoo_knowledge.json`. This is Codex's own backup taken immediately before regenerating — clear evidence the regeneration is a deliberate, in-progress migration attempt, not an accidental corruption. It is not needed by any running code path; it exists purely as a manual rollback copy.

## 6. `official_taxonomy.json` status

Present, untracked. Contains `{"version": "TAX-1", "source": "...", "medical_categories": [33 entries], "special_financial_benefits": [...]}`. This file is the **sole source of truth** for the new taxonomy inside the Python tooling: `tpa_service_mapper.load_approved_categories()` loads it, enforces the `CAT-[A-Z0-9-]+` code format, and requires **exactly 33** entries (raises `ValueError` otherwise). It is well-formed and internally consistent. It is not consumed anywhere in the Java backend.

## 7. TAX-1 migration status — the actual resolution mechanism, verified

Critical correction to the earlier `CLASSIFICATION-PRICE-LIST-WIP-REVIEW-1` cluster report: comparing the `cat` code **string** directly against DB codes (as that report did, finding 0% direct match) is **not how the system actually resolves categories at runtime**. Reading `CategoryResolutionService.java` in full:

```java
/**
 * Resolves the engine's suggested category LABEL to a WAAD MedicalCategory.
 *
 * ⚠️ NEVER by CAT-code: the script's approved list and WAAD's
 * medical_categories share the CAT0xx numbering but with DIFFERENT meanings
 * for several codes (verified: script CAT003 = العناية الفائقة, WAAD CAT003 =
 * الولادة القيصرية). Code-based mapping would silently misclassify —
 * a direct financial risk. Resolution is by canonicalized NAME equality only;
 * unresolved lines are forced into the review queue (CATEGORY_UNRESOLVED).
 */
```

This is a **deliberate, pre-existing safety design**: the code explicitly documents that even the *old* CAT-numbering scheme didn't reliably line up in meaning between the script and the DB, so resolution was built to ignore the code entirely and match only on the canonicalized Arabic/English category **name**. This means the code string itself (`CAT-LAB` vs `CAT023`) was **never** the actual resolution key — so my earlier code-only comparison, while numerically true, measured the wrong thing.

Re-running the comparison the way the system actually works — replicate `ArabicTextCanonicalizer.canonicalize()` in Python, resolve each TAX-1 category's `name_ar` from `official_taxonomy.json` against the real `medical_categories.name_ar` values from `V57` — gives:

- **13 / 33 TAX-1 categories (39%) match an existing DB category name exactly** (e.g. `CAT-IMG-ADV` → "التصوير بالرنين المغناطيسي و المقطعي و الطبقي" matches `CAT011` exactly; `CAT-DIALYSIS` → "الغسيل الكلوي" matches `CAT017` exactly).
- **20 / 33 (61%) do not match anything**, because TAX-1 deliberately **splits** several DB combined categories into finer pieces that don't exist as DB rows: e.g. DB `CAT023` ("رسوم اخصائيين ... العلاج النفسي ... تحاليل ... اشعة سينية ... اشعة تشخيصية" — doctor fees + psychiatry + labs + X-ray + diagnostics, all one bucket) has no single-name match to any of TAX-1's five finer splits (`CAT-PRACT-FEE`, `CAT-PSYCH-DRUG`, `CAT-PSYCH-SESS`, `CAT-LAB`, `CAT-IMG-DIAG`). Similarly DB `CAT031` (dental: crowns+orthodontics+implants combined) splits into TAX-1's `CAT-DENT-PROSTHO`/`CAT-DENT-ORTHO`/`CAT-DENT-IMPLANT`.

**No migration exists creating the new, finer TAX-1 category rows.** This is confirmed by grepping the entire `backend/src/main/resources/db/migration/` tree (`V1` through `V99`) for every TAX-1 code and for the literal string `TAX-1` — the only hit is an unrelated code comment in `PriceListImportLine.java` about delivery/coverage context, nothing about medical categories.

## 8. Samples of correct mappings

(Full 30-row sample in the validation script output, §16.) A representative slice — all `CAT-IMG-ADV` entries, which correctly resolves by name to DB `CAT011`:

```
'ct brain'                       -> CAT-IMG-ADV -> db_code=CAT011
'ct chest'                       -> CAT-IMG-ADV -> db_code=CAT011
'ct abdomen and pelvis'          -> CAT-IMG-ADV -> db_code=CAT011
'knee ap x ray or knee lat x ray view' -> CAT-IMG-ADV -> db_code=CAT011
```

These are genuine improvements over the old file in one sense: the old file lumped all of these MRI/CT services under the broad `CAT023` (which also contains lab tests, doctor fees, and psychiatric treatment); the new mapping correctly isolates them to the imaging-specific bucket, which — once a real migration exists for it — would be a real accuracy gain.

## 9. Samples of unresolved/wrong mappings

**Unresolved (name doesn't match any DB category — correctly falls to `NEEDS_REVIEW`, not a financial-safety bug, but defeats the review-reduction goal):**

```
'knee ap and lat x ray view'  cat=CAT-IMG-DIAG  tax1_name=اشعة سينية و اشعة تشخيصية   (no DB category has this name)
'iga h pylori'                cat=CAT-LAB       tax1_name=تحاليل و مختبرات            (no DB category has this name)
'تخطيط عصب للطرف الواحد'      cat=CAT-LAB       tax1_name=تحاليل و مختبرات
```

**Wrong (generator bug, §1 — mislabeled `CAT-ROOM` due to name+category text concatenation in `build_product_kb.py:64-67`, falsely tagged `sourceRule: "name_rule"`):**

```
'2 stent'                          -> CAT-ROOM  (a cardiac/surgical device, not "room accommodation")
'abdominoplasty'                   -> CAT-ROOM  (a surgical procedure)
'wound stitch a'                   -> CAT-ROOM  (a procedure)
'ciprofloxacin 200mg 100ml'        -> CAT-ROOM  (a drug)
'cystoscopy and dj stent insertion'-> CAT-ROOM  (a surgical procedure)
```

None of these service names contain "room", "ward", "غرفة", "جناح", "اقامة", or "إقامة" — the only way the `CAT-ROOM` rule (`classify()` in `build_product_kb.py:39,63-70`) could have matched is via the concatenated Odoo `category` field, not the service name. This affects **4,417 of 11,415 entries (38.7%)** — the single largest bucket in the entire regenerated file.

Cross-checking old vs. new for the 9,783 service names present in both files: **9,774 of them (99.9%) now resolve to a different (or no) category than before** — confirming this is a wholesale re-categorization, not a targeted fix, and that almost none of it currently lands on the "correct, improved, ready-to-use" outcome.

## 10. Impact on provider price-list import review volume

Directly measured (§4, §7, §9): resolution rate drops from **100% (old) to 37.7% (new)**. If wired into production as-is, roughly **62.3% of previously auto-resolvable knowledge-base entries would newly require manual category review** — the opposite of the ticket's stated purpose. The single largest contributor is the `CAT-ROOM` mis-bucketing bug (38.7% of all entries), which is a generator defect, not an inherent taxonomy-granularity issue.

## 11. Impact on the `PROVIDER-PRICE-IMPORT-REVIEW-1` `requiresReview` gate

Verified this is a **separate, later gate** that remains fully intact and unaffected by any of the above:

- `ImportProcessingService` (classification-time): any line with an unresolved category gets `flags.add("CATEGORY_UNRESOLVED")`, which forces `band = NEEDS_REVIEW` regardless of confidence score (`ImportProcessingService.java:152-169`) — the classification-stage safety net works correctly today and would continue to work correctly with the new file (it would just trigger far more often, per §10).
- `ProviderContractPricingItem.requiresReview` (pricing-item-level, from `PROVIDER-PRICE-IMPORT-REVIEW-1`/`V99__provider_pricing_item_review_flag.sql`): any pricing item without a resolved `medical_category_id` is flagged `requiresReview = true`.
- `ClaimMapper.processEngineCalculations()` throws `BusinessRuleException` immediately if `requiresReview = true`, **before** the coverage engine is ever consulted — confirmed via that ticket's own test (`verifyNoInteractions(coverageEngineService)`).

**No unresolved row can become financially usable.** Both gates are independent of this ticket's findings and are not weakened by them — the risk here is *review-volume regression and generator mislabeling*, not a *financial-safety* hole.

## 12. Is this safe to commit?

**No**, for two independent reasons, either one sufficient alone to block:

1. `odoo_knowledge.json` was generated by a script with a confirmed classification bug (`CAT-ROOM` mis-bucketing, §1/§9) affecting 38.7% of its output. Fixing the missing-migration problem (§7) without first fixing this bug would make those mislabeled entries *resolve successfully* to whatever category eventually maps to `CAT-ROOM` — silently misclassifying thousands of drugs/surgeries/devices as room-accommodation charges. This is exactly business rule #6 ("do not silently remap services to wrong medical categories").
2. Even ignoring the bug, no migration or mapping layer exists connecting the other 61% of TAX-1 codes to real DB categories (§7), so committing this file today would only increase manual-review volume, contradicting the ticket's own purpose (business rule #1/#7).

`official_taxonomy.json` and `build_product_kb.py` are themselves not safe to commit yet either — they are the source and generator of the unresolved taxonomy, and the generator needs a bug fix regardless of the migration question.

## 13. Recommended option: **D — DO NOT COMMIT / KEEP LOCAL WIP**

None of Options A/B/C's preconditions are currently met:
- **Not A** — new codes do not reliably resolve (37.7%), and 38.7% of the file is a confirmed misclassification bug.
- **Not B outright** — a migration alone would not fix the `CAT-ROOM` bug; committing the knowledge file "as is" now and fixing the migration later would ship the bad `CAT-ROOM` labels the moment that migration lands.
- **Not C outright, yet** — a mapping layer is the *right eventual shape* (TAX-1's 33 fine-grained categories are a legitimate design; several genuinely need to collapse back onto today's 31 broad DB categories, e.g. `CAT-LAB`/`CAT-IMG-DIAG`/`CAT-PRACT-FEE`/`CAT-PSYCH-DRUG`/`CAT-PSYCH-SESS` → `CAT023`), but building that mapping on top of the current, bug-affected `odoo_knowledge.json` would bake the wrong `CAT-ROOM` assignments into the mapping layer too.

**Recommended path forward** (for a future ticket, not this one):
1. Fix `build_product_kb.py`'s `classify()` to stop concatenating the Odoo `category` field into the same match pass as the service name (or scope the `CAT-ROOM` keywords to the name only) — re-derive the resolution rates in §4/§10 after the fix; the 38.7% `CAT-ROOM` bucket should shrink dramatically.
2. Decide, category-by-category, whether each of the 20 unmatched TAX-1 codes gets (a) a genuine DB migration splitting the old combined category, or (b) a mapping-table fallback to the current combined DB category until such a migration is prioritized. Given `CategoryResolutionService`'s own documented caution about silent code-based misclassification, prefer explicit, reviewed mapping-table rows over any automatic code-similarity heuristic.
3. Only then regenerate and re-validate `odoo_knowledge.json` before considering a commit.

## 14. Files that must not be committed

- `tools/classification-engine/odoo_knowledge.json` (working-tree version) — blocked per §1/§9/§12.
- `tools/classification-engine/official_taxonomy.json` — source of the not-yet-wired taxonomy.
- `tools/classification-engine/build_product_kb.py` — generator with the confirmed `CAT-ROOM` bug.
- `tools/classification-engine/odoo_knowledge.legacy.json` — Codex's own backup, not needed by any code path (§5).
- `tools/classification-engine/__pycache__/` — build artifact, never commit.
- `tools/classification-engine/odoo_knowledge.json`'s currently-**committed** version must stay as-is (do not overwrite it with the working-tree copy).

## 15. Files that may be committed if approved

None from this cluster, as-is. If the product owner wants to preserve the TAX-1 design work without shipping it, `official_taxonomy.json` and `build_product_kb.py` could be committed **separately, explicitly labeled as an in-progress/experimental taxonomy not yet wired to the engine**, with a code comment or README note stating the `CAT-ROOM` bug and the missing-migration/mapping status — but this was not requested and is not recommended without an explicit decision from the product owner, since it would put unfinished, bug-affected design artifacts into the tracked history.

## 16. Tests/build results

```
cd backend
mvn -o compile     → BUILD SUCCESS (unaffected by this ticket — no Java files touched)

cd ../frontend
npx vite build      → succeeded (already run this session; unaffected — no frontend files touched by this ticket)
```

Validation was performed via a one-off local Python script (not committed; written to the session scratchpad at `C:\Users\alfab\AppData\Local\Temp\claude\...\scratchpad\validate_taxonomy.py`), which:
- Extracts `(code, name_ar)` pairs from `V57__import_standard_medical_categories.sql`.
- Replicates `ArabicTextCanonicalizer.canonicalize()` in Python.
- Loads `official_taxonomy.json` and resolves each TAX-1 code's `name_ar` against the DB set, exactly mirroring `CategoryResolutionService.resolveCategoryId()`.
- Compares the old (committed) and new (working-tree) `odoo_knowledge.json` entry-by-entry, producing the counts in §4, §7, §9, §10.

No database was modified; no files were altered. The script and its output remain local-only pending approval to commit anything from this validation.

## 17. No commit / no push confirmation

Nothing was staged, committed, or pushed during this validation. `git status --short` for the classification-engine directory is unchanged from the state left by the prior `CLAUDE-WIP-RESUME-1`/`CLASSIFICATION-PRICE-LIST-WIP-REVIEW-1` review: `odoo_knowledge.json` modified (not staged), `official_taxonomy.json`/`build_product_kb.py`/`odoo_knowledge.legacy.json`/`__pycache__/` untracked (not staged). No file was deleted or reverted.

---

**CLASSIFICATION-KNOWLEDGE-TAXONOMY-VALIDATION-1 READY FOR DECISION — Recommended option D (do not commit). Root causes confirmed and quantified: (1) a real generator bug in `build_product_kb.py` mislabels 38.7% of entries `CAT-ROOM` by matching against the concatenated Odoo category field instead of the service name alone; (2) even setting the bug aside, only 39% of the new TAX-1 taxonomy's categories have a name-based match to the currently seeded `medical_categories` table, and no migration or mapping layer exists for the other 61%. Net effect if committed as-is: category-resolution rate falls from 100% to 37.7%, increasing manual review volume — the opposite of the ticket's intent — while the existing `requiresReview`/`CATEGORY_UNRESOLVED` financial-safety gates remain intact and are not at risk. Recommend a follow-up ticket to fix the generator bug and build an explicit mapping layer (or targeted migration) before any of these files are reconsidered for commit.**
