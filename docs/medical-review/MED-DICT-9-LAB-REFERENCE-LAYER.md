# MED-DICT-9 — WAAD LAB Reference/Knowledge Layer

**Status:** Implemented, additive, **disabled by default**. First real dictionary-strengthening phase.
**Scope:** Laboratory services only (`CAT-LAB`). No DB, taxonomy, price, contract, claim, or Benefit-Engine change.

## 1. Why synonym-only was rejected
MED-DICT-7 **measured** the synonym-file form of this enrichment at **0 coverage lift** on real engine runs. In `tpa_service_mapper.py`, synonyms only remap incoming text onto a target that must already exist in the reference/knowledge base (`exact_syn` is built from reference rows). The WAAD_COMPRESSED LAB concepts are not in the 1,377-row contract reference, so synonyms pointing at them can never produce a documented match.

## 2. Why a reference layer is used
MED-DICT-8 **measured** that adding the LAB AUTO concepts as **reference rows** produced real lift (labs 0 → ~73.5% in the controlled A/B). A row becomes "documented" when its name matches a reference entry; making the LAB concepts reference entries is therefore the mechanism that works. This layer does exactly that — additively, without merging into the base catalogue.

## 3. What is included
`tools/classification-engine/waad_lab_reference_layer_v1.json` — built from the approved WAAD_COMPRESSED V3.28.1 LAB AUTO candidates:
- **1,599 LAB concepts**, **2,662 normalized aliases** (+ canonical AR/EN names).
- Every entry: `master_id`, `canonical_ar/en`, `aliases[{alias, normalized}]`, `category = CAT-LAB`, `family = LAB`, `source = WAAD_COMPRESSED_AUTO_REFERENCE`, `source_version = WAAD_COMPRESSED_V3_28_1`, `generated_at`, `confidence = 0.95`.
- Concepts are `READY_FOR_CONTROLLED_USE` only; collision-free and quarantine-free (excluded upstream in MED-DICT-8).

## 4. What is excluded
- **DENTAL_ROUTINE, PROFESSIONAL_FEES**, and the 5 HIGH families (DIALYSIS/AMBULANCE/TRANSPLANT/HOME_NURSING/ACCOMMODATION) — out of scope for this phase.
- **REVIEW / NEEDS_MEDICAL_REVIEW / collision / quarantined / deprecated** — never included.
- **Risk spot-check removals (5 aliases):** generic sample/qualifier words whose *whole* normalized form is generic (`serum`, `plasma`, `blood`, `urine`, `kit`, …) and any normalized alias `< 3` chars. Standard lab abbreviations (crp, psa, cbc, bun, hcg…) are **kept** — they are the point of the layer and are low-risk because the layer emits only CAT-LAB and matches whole normalized strings, not substrings. Full exclusion list: `MED-DICT-AUDIT/med_dict_9_layer_exclusions.xlsx`.

## 5. How to enable / disable
Two environment variables (read by the engine entry point; inherited by the backend's engine subprocess):

| Variable | Default | Meaning |
|---|---|---|
| `ENGINE_WAAD_LAB_REFERENCE_ENABLED` | `false` | `true/1/yes/on` enables the layer |
| `ENGINE_WAAD_LAB_REFERENCE_PATH` | `/app/tools/classification-engine/waad_lab_reference_layer_v1.json` | layer file path |

- Local Docker: set `ENGINE_WAAD_LAB_REFERENCE_ENABLED=true` in `.env.local` (compose.local passes it through; the engine folder is bind-mounted `:ro`, so the JSON is already in the container).
- Documented in `.env.local.example` and `.env.production.example`.

## 6. Rollback procedure
The layer is a single additive file behind a default-off flag:
1. **Instant disable:** set `ENGINE_WAAD_LAB_REFERENCE_ENABLED=false` (or unset) and re-run — behaviour returns to exact baseline. No restart of anything else required.
2. **Full removal:** delete `waad_lab_reference_layer_v1.json`. With the flag off this is a no-op; with the flag on the engine fails clearly (by design) until the flag is turned off.
No DB migration, no data mutation, nothing to revert in the catalogue, taxonomy, or synonyms.

## 7. Risk controls
- Emits **CAT-LAB only** (enforced in the loader: the layer's `category` and every concept must be `CAT-LAB`, else it refuses to load).
- The approved `official_taxonomy.json` remains the only source of categories; this layer cannot create categories.
- Generic/short aliases removed (§4); collisions/quarantine excluded upstream.
- `tpa_service_mapper.py` is **unmodified** (A9 byte-stability); integration lives in the entry point + a new helper module.
- Disabled ⇒ byte-identical baseline; enabled-but-missing ⇒ clear failure; disabled-and-missing ⇒ silent no-op.

## 8. Verification results (measured)
Via the real entry point `classify_json.py` on a lab list (المختبر الطبي الأول, 282 rows):

| Case | Documented | Notes |
|---|---:|---|
| Flag **off** (baseline) | 0 | `lab_reference_layer = null`; identical to current baseline |
| Flag **on** | **147** | +147, **all CAT-LAB**, 0 non-CAT-LAB attributed to the layer (safety pass) |
| Flag on + file missing | — | raises `FileNotFoundError` clearly |
| Flag off + file missing | 0 | no error; matches baseline |

Provenance is recorded in the engine result (`knowledge.lab_reference_layer`: layer, source_version, concepts, aliases, rows added). Engine self-check (`classify_json.py --help`), Python syntax, and JSON validity all pass. Consistent with the MED-DICT-8 measured lift for the same file (0 → 147).

## 9. Why no DB import is done
Coverage recognition is an **engine** concern; it needs no `MedicalService` rows or `ent_service_aliases` seeding. Keeping it a file layer makes it fully reversible and keeps the approved dictionary/taxonomy untouched. DB seeding stays deferred until a reviewer sign-off and a Golden-300 human review exist.

## 10. Regeneration
The layer is regenerated from the pinned source (`WAAD_COMPRESSED_MEDICAL_DICTIONARY_V3_28_1_INTERIM_OPERATIONAL_RELEASE.xlsx`, sheets `02_مفاهيم_تشغيلية` / `05_مرادفات_تشغيلية`) via the MED-DICT-8/9 candidate pipeline; bump `waad_lab_reference_layer_v2.json` for future versions and keep old files for rollback.
