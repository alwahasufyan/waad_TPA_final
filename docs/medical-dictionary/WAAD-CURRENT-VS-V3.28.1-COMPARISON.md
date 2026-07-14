# WAAD Current Medical Dictionary vs V3.28.1 — Read-only comparison

**Assessment date:** 2026-07-13  
**Scope:** source code, Flyway history, and read-only aggregates from the local development database `tba_waad_system`; independent structural validation of `WAAD_COMPRESSED_MEDICAL_DICTIONARY_V3_28_1_INTERIM_OPERATIONAL_RELEASE.xlsx`.  
**No code, migration, import, deletion, replacement, commit, or push was performed.** Counts below intentionally exclude patient, member, and claim-identifying data.

## 1. Executive summary

**Final verdict: C — Controlled hybrid architecture.**

The two assets solve different, complementary problems:

- **Current Waad** is the operational/billing system of record. It owns the current category tree, provider-contract price rows, price-list versions, claims/pre-authorization snapshots, benefit-category rules, and historical audit trail. Replacing it would break the distinction between a provider's billable line and a medical classification.
- **Excel V3.28.1** is materially stronger as a controlled normalization and safety layer: 2,116 concepts, 13,158 exact-match operational aliases, 732 quarantined aliases, 67 immediate blocks, 61 manual-only redirects, and explicit unsafe concept statuses. It is not a proven or complete billing catalog.

The workbook is **not suitable for direct import into `medical_services`, provider prices, benefits, claims, or pre-authorizations**. Its own operational sheets say this is an `INTERIM_OPERATIONAL_RELEASE`, not V4.0 final; its 300-row Golden Dataset is entirely `PENDING_HUMAN_REVIEW`; its independent external price-list gate remains open. It also includes broad concepts that intentionally combine clinically/billing-distinct work and must not be used as billable targets.

The safe target is therefore: retain existing Waad operational entities and history unchanged; introduce the workbook only as a separately versioned concept/alias/review layer after medical review and external validation. A classification result may *suggest* or record a mapping, but it must never silently replace the raw provider price-line identity or map a price directly to a broad concept.

## 2. Current Waad architecture

### 2.1 Authoritative operational entities

| Concern | Current authoritative structure | Evidence and implications |
|---|---|---|
| Medical taxonomy/category | `medical_categories` / `MedicalCategory` | Numeric `BIGINT` IDs; unique immutable `code`; Arabic primary `name` plus `name_ar` and `name_en`; self-parent hierarchy; context; active and full soft-delete fields. DB has a parent FK and unique code constraint. |
| Searchable/canonical service | `medical_services` / `MedicalService` | Numeric ID and unique code. It carries name, Arabic/English names, category, specialty, `ACTIVE/DRAFT/ARCHIVED` status, `is_master`, active/deleted lifecycle, cost/base price. Active services require a category in entity validation. This is currently a mixed catalog entity, not a pure clinical-concept model. |
| Alias/normalization | `ent_service_aliases` / `ServiceAlias` | Alias text, locale, provenance, weight and active flag map to a `medical_service_id`; FK to services prevents orphan aliases. Search repository supports case-insensitive exact text lookup, but the database has no documented normalized-key uniqueness constraint or dedicated quarantine table. |
| Provider-specific service and price | `provider_contract_pricing_items` | Stores provider line `service_code`, `service_name`, category label/subcategory/specialty, unit/quantity/currency, price, effective dates, active lifecycle, contract and version. It has a category FK but **no current service FK**; price rows retain provider text/code. |
| Price-list staging/versioning | `price_list_imports`, `price_list_import_lines`, `provider_price_list_versions`, audit/findings tables | V70/V71 support raw imported name/code/price, match/suggestion/final service IDs, review state, price-list versioning and financial validation. Published price rows remain contract pricing items. |
| Claim-line relationship | `claim_lines` | Uses `service_code`, `service_name`, category ID/name, pricing-item ID and immutable financial/coverage snapshots. There is no FK from a claim line to `medical_services`; this is deliberate tolerance for historical/provider-billable identity. |
| Pre-authorization relationship | `pre_authorizations` | Stores service code/name/category as snapshots/fields with contract and coverage values. The local database presently has no rows, so linkage observations are structural only. |
| Benefit/coverage | `benefit_policy_rules` | Current persisted rule targets `medical_category_id`; current entity validates that category is mandatory. Thus broad classification concepts cannot be substituted for coverage categories without a specifically designed mapping policy. |

### 2.2 Current taxonomy, selection, and import behavior

- `MedicalServiceLookupController` exposes a canonical selector at `GET /api/v1/medical-services/lookup`; it maps active catalog services by code/name/category and deliberately degrades lookup errors to an empty result rather than a 500.
- Category UI/API use `MedicalCategoryController` and category-scoped service selection. Front-end API clients exist for category and medical-catalog lookup, while provider-contract workflows use provider price rows.
- Existing provider price-list Excel import intentionally accepts provider `serviceCode`/`serviceName` directly and comments that it performs **no `MedicalService` catalog lookup**. It preserves provider-side values; missing code can be generated. This is important evidence that the system distinguishes provider billable rows from the catalog.
- The medical-classification module stages raw rows in `price_list_import_lines`; it keeps raw values, a normalized string, match/suggestion/final IDs, confidence, method, flags, reviewer decision, and review status. Its price-list version pipeline, financial validation, audit, patch/rollback workflow, and manual category/service linking are already separate from claim history.
- `MedicalServiceCategory` is present in source as a future multi-context mapping entity, but the local database has no `medical_service_categories` table and Flyway history stops at V78. It must not be assumed available in the running deployment.

### 2.3 IDs, lifecycle, constraints, and observed code patterns

- Local current data: service IDs are 1–345 with 345 rows; category IDs are 1–2701 with 55 remaining rows. Category IDs therefore reflect prior history/deletion/import activity rather than a compact sequence.
- All 345 current services use `MCE-*` codes; categories use `CAT-*` (49) and `SUB-*` (6). Both code columns have unique constraints and no duplicate code was found.
- Services are all `ACTIVE`, `active=true`, `deleted=false`; categories are all active and not deleted in this local DB. The lifecycle model nevertheless supports DRAFT/ACTIVE/ARCHIVED and soft deletion in code/schema.
- Database integrity is stronger for category references than service references: `medical_services.category_id` and pricing `medical_category_id` are FKs. Contract price rows do not carry a medical-service FK; current code describes `medicalServiceId` as deprecated/removed for pricing rows.

## 3. Current database statistics (read-only)

| Check | Result | Interpretation |
|---|---:|---|
| Categories / active categories | 55 / 55 | Operational category reference set is small and currently live. |
| Medical services / active operational services | 345 / 345 | Current catalog is far smaller than the workbook; it is an operational catalog, not equivalent coverage. |
| Inactive/archived/deleted services | 0 | No current data examples of lifecycle states, though schema supports them. |
| Active aliases | 476 | Existing alias layer is much smaller than Excel's 13,158 operational aliases. |
| Duplicate service codes / category codes | 0 / 0 | DB uniqueness works for these business keys. |
| Duplicate simple normalized service display names | 0 | Measured as trimmed lower-case `name_ar` fallback to `name`; this is not Arabic linguistic normalization. |
| Services missing Arabic / English name | 0 / 233 | Current catalog is Arabic-complete but English-incomplete. |
| Services without category / orphan category reference | 0 / 0 | Consistent with active-service rule and FK. |
| Categories with no direct service | 51 | Most current categories are used as coverage/pricing classification rather than direct service containers; do not infer that they are empty/unused. |
| Provider contract pricing items / active | 980 / 224 | All 980 have a valid category; all retain a code and a name. |
| Price rows whose code equals a current service code | 824 | 156 price rows remain provider-specific or otherwise do not exactly equal the small central service catalog. This supports preserving raw provider identity. |
| Claim lines / code matches to current service | 8 / 0 | Claim history uses `service_code` as a historical/billable field, not a catalog FK. |
| Pre-authorizations / code matches | 0 / 0 | No local pre-authorization records to assess. |
| Orphans: price category / claim category / pre-auth category / service category / aliases | 0 / 0 / 0 / 0 / 0 | No orphan references found in tested foreign/key joins. |
| Active benefit rules | 62 | Current coverage is category-based. |
| Classification imports / published | 18 / 2 | Existing governed import workflow is in use. |
| Staged import lines / matched / final service chosen | 6,316 / 269 / 381 | The system already distinguishes automatic suggestion from reviewer final selection. |
| Price-list versions / active | 5 / 1 | Versioned pricing is live. |

**Limitations:** Counts are from the available local development database, not production. No personal data was queried or displayed. A count of zero does not prove production behavior.

## 4. Excel V3.28.1 independent validation

### 4.1 Operational-sheet control

The workbook includes several older RC/V3.28 sheets as well as the operational release. The following are the only declared operational sheets and must be allow-listed, not inferred by prefix or position:

`01_قواعد_التشغيل`, `02_مفاهيم_تشغيلية`, `05_مرادفات_تشغيلية`, `06_محجورة_تشغيلية`, `12_حظر_فوري`, `13_إعادة_توجيه_مؤقت`, and `14_تصحيحات_أسماء`.

Workbook verification found all seven are **static values (zero formula cells)**. The workbook has no defined names. It contains older sheets such as `02_المفاهيم_RC`, `05_مرادفات_نشطة`, `02_المفاهيم_V3_28`, and `05_مرادفات_V3_28`; an importer that selects by loose name/prefix could import a superseded candidate/index. This is a **HIGH** operational risk.

### 4.2 Counts and integrity checks

| Check | Independently observed result | Assessment |
|---|---:|---|
| Operational master concepts | 2,116 | Matches release statement. |
| LAB / GENERAL concepts | 1,842 / 274 | Matches claimed split. |
| Operational aliases | 13,158 | Matches release statement. |
| Quarantined aliases | 732 | Matches release statement. |
| Immediate blocks | 67 | Matches release statement. |
| Temporary redirects | 61 | Matches release statement. |
| Canonical-name corrections | 20 | Matches release statement. |
| Duplicate/blank Master_ID | 0 / 0 | Pass. |
| Operational aliases pointing to multiple Master_ID values | 0 | Pass for published operational index. |
| Aliases pointing to missing concept | 0 | Pass. |
| Duplicate normalized alias *rows* | 258 | Not inherently unsafe because all duplicate rows mapped to the same Master_ID; nevertheless an importer must deduplicate or tolerate deterministic identical-target duplicates. |
| Blank normalized aliases | 0 | Pass. |
| Concept Arabic names absent | 1,179 | High bilingual-completeness limitation: aliases may exist, but canonical Arabic is not present for most concepts. |
| Concept English names absent | 15 | English coverage is substantially better. |
| Alias languages | 6,111 AR / 7,047 EN | Explicit language separation exists. |
| Published alias operational status | 13,158 `ACTIVE_EXACT_MATCH_ONLY` | Strong controlled-matching rule. |
| Golden Dataset | 300 rows, all `PENDING_HUMAN_REVIEW` | Independent human validation is not complete. |
| External price-list validation | `PENDING_EXTERNAL_PRICE_LIST` / `PATCHED_PENDING_INDEPENDENT_RETEST` | Not complete. |

### 4.3 Status and safety distribution

Concept status is not binary “safe/unsafe.” Of 2,116 concepts, only 1,864 are `READY_FOR_CONTROLLED_USE`. The rest include 20 `SPLIT_REQUIRED`, 12 `NEEDS_USER_DECISION`, 16 `REVIEW_ONLY_NOT_AUTO_APPROVE`, 14 `BILLING_ONLY_REVIEW`, 4 `MOVE_OUT_OF_LAB_REVIEW`, 4 `NEEDS_SOURCE_DESCRIPTION`, 171 `READY_ENGLISH_ONLY_ARABIC_AMBIGUOUS`, and deprecated/merged records.

Operational aliases retain these target-status warnings: 643 aliases map to `SPLIT_REQUIRED`, 450 to `NEEDS_USER_DECISION`, 636 to `REVIEW_ONLY_NOT_AUTO_APPROVE`, 164 to `BILLING_ONLY_REVIEW`, and 373 to Arabic-ambiguous concepts. Therefore **“exact alias match” alone is not an approval rule**. Exact matching is only eligible for automatic approval when the target status is explicitly safe and no block/quarantine applies.

The workbook’s own rules are sound and should be preserved verbatim in any future implementation:

1. Check `12_حظر_فوري` first; it overrides every alias.
2. Use `05_مرادفات_تشغيلية` only for exact normalized matching.
3. Do not auto-approve `SPLIT_REQUIRED` or `NEEDS_USER_DECISION` (and the same principle applies to other review-only status labels).
4. Fuzzy matching is suggestion-only.
5. No match becomes `UNCLASSIFIED` / `NEEDS_REVIEW`, never a guess.
6. `13_إعادة_توجيه_مؤقت` is manual guidance only.
7. The operational concepts sheet is the authoritative name/family source for this release.

Quarantine data is meaningful, not decorative: it includes collision reasons naming multiple potential targets and 71 rows marked `EXTERNAL_VALIDATION_MATERIAL_ERROR_OR_AMBIGUOUS`. Immediate blocks require 61 manual remaps and 6 review-only decisions. Redirects are all `MANUAL_GUIDANCE_NOT_AUTO_APPROVED`.

## 5. Semantic and billing-safety review

### 5.1 Six distinct concepts that must not be collapsed

| Term | Safe responsibility | Must not be treated as |
|---|---|---|
| Medical classification concept | Clinical/semantic grouping and controlled normalization target | Automatically billable price row. |
| Canonical searchable concept | The approved name/synonym target for human search and matching | A coverage or contract-price key by itself. |
| Billable medical service | A granular service identity appropriate to quantity, method, laterality, setting, physician level, and adjudication | A broad umbrella/clinical family. |
| Provider-specific service | The raw code/text and local meaning from the provider list | A lossless synonym of a universal concept without review. |
| Provider contract price | Contract/version/unit/quantity/currency/effective-date specific rate | The price of a broad concept or canonical text. |
| Benefit/coverage mapping | Policy decision, currently category-based in Waad | A clinical matching result. |

### 5.2 Concrete over-compression examples

| Excel example | Why it is unsafe as a billable target | Required handling |
|---|---|---|
| `MED-0001` — “إجراء كي أو ليزر / Ablation or Laser Procedure”, `SPLIT_REQUIRED` | Its aliases span breast-cyst aspiration, thyroid/neck/epididymal cyst work, varicose-vein thermoablation (including bilateral), ophthalmic YAG/iridoplasty (one eye), dental scaling, skin-wart cryotherapy by quantity, lithotripsy, urology laser, and more. These differ by specialty, method, laterality, setting, anesthesia, unit, and price. | Never map a price/claim/pre-auth directly to `MED-0001`. Route to review and map to a granular billable service, or retain raw line as unmatched. |
| `MED-0017` — “خدمة أخرى - القلب والأوعية / Other Service - Cardiology and Vascular”, `REVIEW_ONLY_NOT_AUTO_APPROVE` | Aliases include coronary/carotid/renal/peripheral angiography, diagnostic cath access route, PCI/stents, pacemaker battery work, thrombolysis and ICU-like procedures. The procedure, device, route and professional component materially change coverage and contract rate. | Review-only concept; create/choose a granularity-preserving billable mapping. |
| `MED-0004` — Blood Transfusion, `NEEDS_USER_DECISION` | Combines generic transfusion, one-unit transfusion, neonatal exchange transfusion, and a complex uterine-bleeding episode. Unit, component, patient group and bundled context are clinically and financially distinct. | Require source context and reviewer decision; no auto-price. |
| `MED-0190` — Ablation/Laser professional and anesthesia fees, `BILLING_ONLY_REVIEW` | This is an accompanying fee concept, not proof of the clinical procedure. It includes local/general anesthesia and different procedures. Combining it with the clinical service can double-pay or misapply benefit rules. | Keep component billing separate and review price/coverage relationship to the underlying procedure. |
| `LAB-0031` — Arabic “تحليل املاح البول” vs English “24h urine creatinine”, `READY_ENGLISH_ONLY_ARABIC_AMBIGUOUS` | The Arabic label is broader/different from the English canonical test; an exact Arabic match can choose the wrong analyte or collection method. | No Arabic automatic approval until medical terminology is resolved. |

The same issues arise for inpatient/outpatient distinction, physician component versus facility component, bilateral versus unilateral, units (per test/session/day/implant), and bundles. The current Waad price table already has separate `unit`, `quantity`, specialty and effective dates; the Excel concept sheet does not carry sufficient structured billing attributes to replace that table.

## 6. Comparison matrix

| Criterion | Current Waad evidence | Excel V3.28.1 evidence | Winner | Risk | Recommendation |
|---|---|---|---|---|---|
| Taxonomy quality | 55 categories, 345 operational services | 2,116 concepts / 38 families | Excel | Broad concepts remain unsafe | Use Excel as supplementary classification taxonomy. |
| Medical granularity | Billable rows and category model exist but catalog is small | Large coverage, but explicit broad/split concepts | Hybrid | Direct substitution loses billable distinctions | Keep billable services separate. |
| Arabic quality | All current service rows have Arabic; 233 lack English | 1,179 concepts lack Arabic canonical name; 171 Arabic-ambiguous status | Current for Arabic completeness | Incorrect Arabic match | Retain Waad Arabic display; medical-review Excel Arabic gaps. |
| English quality | 112/345 populated | 2,101/2,116 populated | Excel | Translation/canonical mismatch still exists | Use Excel English as terminology reference, not overwrite. |
| Aliases | 476 active aliases, basic locale/provenance | 13,158 aliases, AR/EN, normalized exact index | Excel | Published alias statuses may be unsafe | Import only into a separate governed alias layer after review. |
| Normalization | Lookup uses case-insensitive text search; no dedicated normalized-key contract seen | Explicit Normalized field and exact-only rule | Excel | Different normalizers may produce mismatches | Version the normalizer with the dictionary release. |
| Duplicate protection | Unique service/category codes; aliases lack unique normalized target constraint | No duplicate Master_ID; no multi-target published aliases; 258 same-target duplicate alias rows | Hybrid | Alias duplicate ingestion variability | Enforce unique `(dictionary_version, normalized, locale, concept)` in future layer. |
| Ambiguity handling | Review staging exists, but current catalog has no classification-concept statuses | Explicit status taxonomy and review-only instructions | Excel | Unsafe statuses could be ignored by a naïve importer | Gate auto approval on safe statuses only. |
| Quarantine | No standalone taxonomy quarantine entity | 732 quarantined rows and reasons | Excel | Quarantine bypass | Persist and check quarantine before alias lookup. |
| Medical-review workflow | Existing price-list review/final selection and audit | Golden review and manual decision columns, but incomplete | Hybrid | Workbook process not completed | Use Waad workflow; seed controlled queue from Excel. |
| Provider pricing compatibility | Proven price/version/contract/unit model; raw code/name preserved | No provider price model | Current | Concept-based price collapse | Never price an umbrella concept. |
| Claims compatibility | Claim lines preserve service code/name/pricing snapshots | No claim/adjudication data model | Current | Historical rewrite | Do not touch claim history. |
| Benefit-engine compatibility | Category-based rules with active policy controls | Family/status, not benefit rule semantics | Current | Coverage wrongly inferred from concept | Map concepts to categories only through approved policy. |
| Auditability | Price versions, findings, reviews, price-change audit, timestamps | Static workbook sheets and correction notes | Hybrid | Spreadsheet has no transactional audit | Persist immutable release/version and decision audit. |
| Versioning | Price lists/versioned contracts; Flyway-backed schema | V3.28.1 labels and older sheets coexist | Hybrid | Wrong sheet/release ingestion | Explicit release manifest and allow-list. |
| Governance | RBAC and reviewed publish workflow | Explicit gates; GATE-02/GATE-05 open | Hybrid | False “ready” claim | Do not promote until gates close independently. |
| Import readiness | Existing staged imports | Operational release is static but not independently validated | Current pipeline, not Excel payload | Direct import corruption | Stage raw file; no direct master-data import. |
| Production safety | Contract/claim history constraints and snapshots | Interim; Golden/external gates pending | Current | Broad mappings affect money | Controlled pilot only after approvals. |
| Maintainability | Small catalog but mixed responsibilities; source has future entity/schema drift | Large spreadsheet manual/static maintenance | Hybrid | Spreadsheet drift/version ambiguity | Move reviewed data into governed tables, retain workbook as source artifact. |
| Scalability | Existing database workflow/versioning | 13k alias index is feasible but not transactionally governed in Excel | Hybrid | Fuzzy/matching performance and audit gaps | DB index normalized aliases; exact lookup only. |

## 7. Recommended authoritative model (conceptual only; not implemented)

### 7.1 Minimal controlled-hybrid model

```text
medical_concepts (new, immutable per dictionary version)
  1 ──< medical_concept_aliases (published exact aliases only)
  1 ──< medical_alias_quarantine (never eligible for auto-match)
  1 ──< medical_mapping_review_queue (raw line → decision/audit)

existing medical_services (billable/searchable Waad service catalog)
  1 ──< provider_service_mappings (provider raw identity → approved billable service and/or concept)
provider_service_mappings ──> medical_concepts (optional semantic classification)

existing provider_contract_prices / provider_contract_pricing_items
  ──> provider-specific service mapping or raw provider line, never a broad concept alone
```

### 7.2 Authoritative responsibility

| Domain | Authoritative entity/source |
|---|---|
| Classification terminology | `medical_concepts` (versioned, reviewed dictionary release) |
| Exact searchable aliases | `medical_concept_aliases` after block/quarantine/status gates |
| Raw provider descriptions and codes | Staged import/raw provider line and `provider_service_mappings`; preserve exactly as received |
| Claim-line billing and historical evidence | Existing `claim_lines` and its snapshots; unchanged |
| Provider pricing | Existing `provider_contract_pricing_items` plus version/audit workflow; pricing maps only to granular billable identity |
| Benefit coverage | Existing `medical_categories` / `benefit_policy_rules`, with explicitly approved mapping—not inferred from an Excel family |
| Unsafe/ambiguous terms | `medical_alias_quarantine` and `medical_mapping_review_queue`, never auto-approved |

Required control order for every future classification: **immediate block → quarantine → exact normalized alias → target-status eligibility → review queue**. Fuzzy candidates may be shown to a reviewer but must not create an approval or a provider-price mapping. Persist the release ID, normalization version, decision actor/time, source text/code, and evidence for every reviewed mapping.

## 8. Risks

| Severity | Risk | Evidence | Required control |
|---|---|---|---|
| HIGH | Direct import would collapse concepts, billable items and contract prices | `MED-0001`, `MED-0017`, `MED-0190` combine materially different services | Prohibit direct import to services/prices/benefits/claims. |
| HIGH | Workbook has open independent clinical and external price-list validation gates | Golden 300 all pending; GATE-05 pending independent retest | Medical review and independent provider-list validation before any production use. |
| HIGH | Wrong sheet can be imported | Old RC/candidate/V3.28 sheets coexist beside operational sheets | Hard-coded allow-list and release manifest/hash. |
| HIGH | Block/quarantine precedence can be bypassed | 67 immediate blocks; 732 quarantine records | Enforce block before alias lookup; never publish quarantined terms. |
| HIGH | Existing history could be retroactively rewritten | Waad claim/pre-auth/provider prices preserve snapshots/raw fields | Append mappings only; never update historic claims, pricing, benefit or settlement records. |
| MEDIUM | Arabic canonical completeness is low | 1,179 concepts lack Arabic canonical value; 171 Arabic-ambiguous | Arabic terminology review; UI fallback must be clear and not invent translation. |
| MEDIUM | Current services and aliases conflate canonical/billable concerns | `medical_services` has `is_master`; alias maps directly to it | Add separate concept layer rather than enlarging current service table with every workbook concept. |
| MEDIUM | Current price rows are only partly exact-code linked to central services | 824/980 exact code matches | Preserve provider text/code and use explicit mapping decisions. |
| MEDIUM | Current source contains a not-yet-migrated service-category entity | Source references V83/V90; DB Flyway history ends at V78/no table | Do not design adoption assuming this mapping exists until separately reconciled. |
| LOW | Same-target normalized alias duplicate rows | 258 duplicates, none multi-target | Deduplicate deterministically in a future publish process. |

## 9. Recommended phased adoption plan (no implementation authorized by this report)

1. **Freeze and validate the source artifact.** Record workbook hash, V3.28.1 release metadata, and an explicit seven-sheet allow-list. Reject RC/candidate/summary sheets as source data.
2. **Independent medical validation.** Complete human review of all 300 Golden rows, with special review of every unsafe status and Arabic ambiguity; preserve reviewer decisions/audit.
3. **External provider-price-list validation.** Test on independent files never used to construct V3.28.1; measure precision, false-positive severity, and price-line mapping effects.
4. **Build only a separate controlled concept/alias/quarantine/review layer.** Import no billing, claim, benefit, provider contract, or category records. Load operational aliases only after status and collision validation; store blocks/quarantine first.
5. **Shadow mode.** Classify new provider-list lines alongside existing workflow; no automatic publish. Compare reviewer decisions and financial validation outcomes.
6. **Approved mapping pilot.** Permit exact, safe-status mappings to create a review suggestion; require human approval before provider-service mapping, price version publication or benefit impact.
7. **Governed expansion.** Promote only after medical sign-off, independent price-list acceptance, rollback/audit tests, and a formal data-governance owner. Existing history remains immutable throughout.

## 10. Explicitly prohibited imports

The following must **not** be imported from V3.28.1 into the current system directly:

- Any entire workbook sheet selected by name pattern, including `*_RC`, `*_V3_28`, summaries, gates, historical tests, Golden sample, or candidate sheets.
- `SPLIT_REQUIRED`, `NEEDS_USER_DECISION`, `REVIEW_ONLY_NOT_AUTO_APPROVE`, `BILLING_ONLY_REVIEW`, `MOVE_OUT_OF_LAB_REVIEW`, `NEEDS_SOURCE_DESCRIPTION`, Arabic-ambiguous, deprecated, or merged concepts as automatic billable mappings.
- All `06_محجورة_تشغيلية` aliases and all `12_حظر_فوري` matches into an auto-match index.
- `13_إعادة_توجيه_مؤقت` as an automatic redirect/mapping rule.
- Any concept or alias directly into `provider_contract_pricing_items`, without an approved granular provider-service/billable mapping.
- Any concept/family directly into `benefit_policy_rules` or as a replacement for `medical_categories`.
- Any rewrite of existing `medical_services`, category codes, provider price rows, claims, pre-authorizations, benefit decisions, settlements, or historic audits.
- Fuzzy matches as automatic approvals, regardless of score.

## 11. Final decision

**Choose C — Controlled hybrid architecture.**

Current Waad remains authoritative for money, contracts, coverage, claims, pre-authorizations, provider raw text/code, and historical records. Excel V3.28.1 is the better source for a future governed semantic normalization layer because it has far broader vocabulary and explicit safety controls. It is not a replacement medical billing catalog and is not ready for direct production import.

No implementation follows from this decision. Any future work requires separate approval, completed independent medical review, external provider-price-list validation, a release allow-list/hash, and a non-destructive migration/design review.
