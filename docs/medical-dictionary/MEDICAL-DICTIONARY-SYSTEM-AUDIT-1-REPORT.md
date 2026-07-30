# MEDICAL-DICTIONARY-SYSTEM-AUDIT-1 — Medical Dictionary / Taxonomy System Audit

**Status:** MEDICAL-DICTIONARY-SYSTEM-AUDIT-1 READY FOR REVIEW  
**Mode:** Audit only. No application code, migrations, or database data were changed. No commit or push was performed.  
**Review basis:** Repository source and migrations on 2026-07-24. Live database inspection was attempted but Docker was inaccessible in this environment; therefore live row counts, duplicate counts, and orphan counts remain unverified.

## 1. Executive summary

The repository contains a medical taxonomy/catalog (`medical_categories`, `medical_services`, aliases), a provider-contract pricing model (`provider_contracts`, `provider_contract_pricing_items`), benefit policies/rules, and a newer medical-classification price-list workflow with staging, review, immutable versions, validation findings, and publish metadata.

The design is not fully converged. The governed workflow exists and is protected by reviewer/admin roles, but legacy direct pricing/import endpoints and services still exist. Pricing items are not consistently required to reference `medical_services`: the current schema requires a medical category but the medical-service FK was removed in the post-V228/V229 model; `version_id` is nullable for legacy rows. Claim lines preserve `service_code`, names, category snapshots, and `pricing_item_id`, but do not have database foreign keys to either pricing items or medical services.

This means the system has meaningful safeguards around published price-list versions and financial validation, but cannot be considered fully governed or demonstrably safe for all financial calculations until the live data is checked and the legacy path is retired or explicitly isolated.

## 2. Current dictionary architecture

There are three overlapping concepts:

1. **Official taxonomy/catalog:** `medical_categories` and `medical_services`. `medical_services.code` is unique in the schema and services have lifecycle statuses (`DRAFT`, `ACTIVE`, `INACTIVE`, `DEPRECATED`) plus soft-delete fields.
2. **Contract-specific commercial data:** `provider_contract_pricing_items`. These rows contain provider-negotiated prices and names/codes/categories. In the current model, a pricing item can exist without a `medical_service_id`; the source comments explicitly state that the old MedicalService FK path was removed.
3. **Classification and publication workflow:** imports and staging rows classify uploaded provider rows against the catalog, retain review decisions, create price-list versions, run financial validation, and publish an active version.

There is no separate database table clearly serving as a normalized “service category” dictionary. `service_category`/`service_category_id` appears as a compatibility or classification field in benefit, pre-authorization, claim, visit, and pricing DTOs/entities. Medical categories are the governed relational category used by the newer schema.

## 3. Main DB tables and relationships

| Area | Tables / fields | Meaning and relationship |
|---|---|---|
| Categories | `medical_categories`, `medical_category_roots` | Governed category hierarchy/root data. |
| Services | `medical_services`, `ent_service_aliases` | Catalog service; unique `code`; service belongs optionally to `medical_categories` through `category_id`; aliases point to a service. |
| Contracts | `provider_contracts` | Provider contract and commercial status. |
| Contract prices | `provider_contract_pricing_items` | Contract-specific service name/code/category and price. Current schema requires `medical_category_id`; `version_id` is added by V70 but nullable. Medical-service FK was removed by the later model. |
| Benefits | `benefit_policies`, `benefit_policy_rules` | Policy defaults plus category-level rules. Current migrations state service-level `medical_service_id` rules were removed; old columns are cleaned in V34. |
| Claims | `claims`, `claim_lines` | Claim lines store `service_code`, description/name, category IDs/names, financial snapshots, and nullable `pricing_item_id`; no FK to pricing or service is defined in V20. |
| Pre-authorizations | `pre_authorizations`, `pre_authorization_audit` | Request-side service/category fields and audit history. The migration comments indicate `medical_service_id` was removed from the older path. |
| Classification imports | `price_list_imports`, `price_list_import_lines` | Uploaded-file aggregate and row-level staging. Unknown/unresolved rows remain in staging; matched/final service and category IDs have FKs. |
| Publication | `provider_price_list_versions` | Draft/active/superseded/archived immutable version backbone; one active version per contract is enforced by a partial unique index. |
| Validation/audit | `price_list_validation_findings`, `catalog_classification_history`, `price_list_correction_requests` | Financial findings, category/classification history, and provider correction workflow. |

Important migration evidence is in `V9`, `V11`, `V12`, `V20`, `V57`, and `V70`–`V75`, with cleanup/backward-compatibility changes in `V33`–`V47`.

## 4. Definitions and authoritative identifiers

- **Medical category:** official governed classification node, represented primarily by `medical_categories.id` and its current code/name fields.
- **Service category:** a legacy/compatibility label or category field used in DTOs and older screens. It is not clearly a separate authoritative dictionary table in the current migrations.
- **Medical service:** catalog entity in `medical_services`, with the system-wide unique `medical_services.code`.
- **Provider pricing item:** contract-specific negotiated row in `provider_contract_pricing_items`; its identity is the pricing item ID and its commercial price is contract-specific.
- **Benefit rule:** policy-specific coverage/limits/pre-approval rule, currently category-oriented.
- **Claim line service:** a financial transaction snapshot. It stores service code/name/category and pricing item ID, but is not relationally constrained to the catalog/pricing row.

**Authoritative service code:** for the official catalog, `medical_services.code` is authoritative because it has a unique constraint. For provider-contract rows and claims, `service_code` is also persisted and may originate from the provider price list or generated legacy import data. Therefore the repository does not enforce one universal authority at every boundary. The newer governed import can retain `matched_service_code`/`final_service_id`, but the legacy import explicitly allows direct `serviceCode`/`serviceName` and can auto-generate a code.

## 5. Service selection flow

The Provider Portal claim hook `frontend/src/pages/provider/hooks/useProviderClaimSubmission.js` loads contract services and carries both identities where available: `medicalServiceId` for a real catalog link and `pricingItemId` for the provider contract row. The UI matching order is pricing item ID, medical service ID, then code. Submission requires a line to have either a medical/catalog identity or a pricing item identity and to have contract pricing.

The same hook contains compatibility handling for service/category names and a literal `Unknown Service` fallback when no catalog-linked name can be resolved. `ClaimMapper` also falls back to `Unknown Service`; `ClaimLine` has the same fallback in its entity logic. Consequently, ambiguous or contract-only rows can reach claim DTO construction even when `medicalServiceId` is null, provided a pricing item is present.

Backend claim creation/review is under `ClaimController`, `ClaimService`, and `ClaimMapper`. Coverage and calculation are handled through `CoverageEngineController`/`CoverageEngineService` and `CostCalculationService`; the repository also contains a claim rule-engine path. The claim-line schema snapshots coverage, copay, limits, applied category, approved price, and amounts so later display/calculation can use the recorded decision rather than re-resolving the catalog.

Manual/data-entry claim routes are also exposed by `ClaimController` and accept broader authenticated roles than taxonomy administration. Exact live behavior for every manual form should be regression-tested because the claim schema itself does not force a pricing/catalog FK.

## 6. Provider Excel price-list flow

Two implementation families are present:

### Governed classification path

`PriceListImportController` (`/api/v1/classification/imports`) delegates to `ImportOrchestrationService`/`ImportProcessingService`. The file is represented by `price_list_imports`; rows are staged in `price_list_import_lines`; matching/classification records raw code/name/category, confidence, method, flags, suggested/final service/category, review status, reviewer, and timestamps. Classification history is stored in `catalog_classification_history`.

Review is implemented by `ReviewService` and `PriceListReviewController`. A `PriceListVersion` is created and `FinancialValidationService` checks price spikes/drops, outliers, zero/negative prices, total swings, and duplicate conflicts. `PriceListVersionService.publish` is the publish boundary; the schema records approver/publisher and enforces one active version per contract. The code comments state blockers prevent publish and published artifacts are not revalidated.

### Legacy/direct provider-contract paths

`ProviderContractPricingExcelController` exposes the older direct contract import through `ProviderContractPricingExcelService`. `PriceListExcelTemplateService` is another direct template/import service. These paths parse Excel, validate cells and prices, resolve categories when supplied, preserve provider service name/code, and can generate a code when Excel omits one. The older service comments explicitly say “No MedicalService catalog lookup — use serviceCode/serviceName directly.” This is a material governance gap relative to “official categories only / fallback disabled.”

Rejected/unmatched rows in the governed workflow are represented by import-line status and findings. Direct-import behavior is result/error based and does not use the same immutable version/audit backbone in every method; reachability must be verified against the deployed frontend/API configuration before decommissioning.

## 7. Governed taxonomy / publish workflow status

**Present:** staging, review statuses, final service/category FKs on staging rows, classification history, version lifecycle, publish actor/time, validation findings, correction requests, and role-protected controllers.

**Not fully enforced across the system:**

- Legacy direct imports can store provider codes/names without resolving a catalog service.
- `provider_contract_pricing_items.version_id` is nullable for legacy rows.
- Claim lines have no FK to `pricing_item_id`, `medical_services`, or `medical_categories`.
- Benefit rules are category-oriented after cleanup; service-level rules are not a current normalized path.
- The repository contains an `MedicalServiceCategory` entity, while no corresponding current migration was found; this is a stale/dead-entity risk.

Unpublished draft pricing should not affect claims when the governed version path is used; pricing repository comments describe draft rows as held under a draft version until publish. However, the older direct pricing services and nullable version links mean that this guarantee is not universal without runtime route and data verification.

## 8. Benefit rule matching flow

The current migration model has policy defaults in `benefit_policies` and category-oriented rows in `benefit_policy_rules`. V34 removes the former service-category and service-level legacy columns, and V11 comments state that `medical_service_id` was removed after the later taxonomy change.

The effective conceptual precedence is: explicit category rule, then policy default. Service-level matching is not a reliable current database capability; where service/category fields are present in DTOs, they are compatibility inputs rather than evidence of a normalized service-level rule. Coverage percentage, copay/limits, pre-approval requirements, and snapshots are therefore sensitive to category resolution and fallback behavior.

There are multiple calculation/coverage components (`BenefitPolicyRuleService`, `BenefitPolicyCoverageService`, `CoverageEngineService`, and `CostCalculationService` were specifically requested for audit and corresponding code paths exist). This duplication increases the risk of different precedence or fallback behavior. A focused contract test should assert identical results for service, category, and default cases across all engines.

## 9. Pricing-to-claim-line and financial dependency points

The normal intended path is:

`provider contract → active pricing item/version → claim line pricing_item_id + service_code/name/category → benefit rule/default → coverage/copay/limits → claim-line financial snapshots → review/settlement`.

Financially sensitive fields include `unit_price`, `total_price`, `pricing_item_id`, `service_code`, `service_category_id`, `applied_category_id`, `coverage_percent_snapshot`, `patient_copay_percent_snapshot`, limits, approved unit price/quantity, refused amount, and used/remaining benefit amounts.

Main risks are wrong provider price from a stale/unversioned pricing item, wrong service code from direct Excel or generated-code import, wrong category fallback, and inconsistent benefit matching. Claim snapshots help preserve historical decisions but do not prevent an incorrect decision at submission or review time.

## 10. UI pages/routes involved

Repository pages/components include:

- Provider claim submission: `frontend/src/pages/provider/**`, especially `useProviderClaimSubmission.js`, `SmartServicePicker`, `MedicalServicePicker`, and `CategoryServicePicker`.
- Claims and manual/batch entry: `frontend/src/pages/claims/**` and claim batch components.
- Claim review: `frontend/src/pages/claims/review/**`.
- Provider contracts/pricing: `frontend/src/pages/provider-contracts/**`, classification pricing components, and contract price edit dialogs.
- Benefit policy and package management: `frontend/src/pages/benefit-policies/**` and `frontend/src/pages/benefit-packages/**`.
- Medical audit/reports: `frontend/src/pages/reports/**`, report components, and audit exports.
- Reusable Excel import UI: `frontend/src/components/ExcelImport/**` and `ExcelUploadButton`.

Backend route families include `/api/v1/medical-categories`, `/api/v1/medical-services`, `/api/v1/provider-contracts/**`, `/api/v1/classification/imports`, `/api/v1/claims/**`, `/api/v1/claim-batches/**`, `/api/v1/pre-authorizations`, and `/api/v1/reports/**`.

## 11. Permissions/RBAC

- Medical category administration and category Excel import: primarily `SUPER_ADMIN`; lookups are available to authenticated/provider/reviewer roles according to controller methods.
- Governed classification import/review/publish: `SUPER_ADMIN` and `MEDICAL_REVIEWER` on classification controllers.
- Direct contract pricing/edit/import: principally `SUPER_ADMIN` and `ACCOUNTANT` in provider-contract controllers.
- Claims: provider staff, data entry, medical reviewer, and super admin for submission/operations; reviewer-only or super-admin-only actions protect approval/administration depending on endpoint.
- Pre-authorizations: provider staff/data entry create paths; medical reviewer/super admin decision and administration paths.
- Reports: broader read roles including finance/accountant, employer admin, provider staff, and medical reviewer depending on report.

The RBAC boundary is reasonably explicit in annotations, but the presence of two import families means permissions should be checked against every deployed endpoint, not only the governed classification controllers.

## 12. Data-quality findings

### Confirmed from code/schema

- `medical_services.code` has a unique database constraint.
- Claim-line `pricing_item_id` is not a foreign key and may be null.
- `provider_contract_pricing_items.version_id` is nullable.
- Pricing items require `medical_category_id` in V12-era schema but do not require `medical_service_id` in the current model.
- `medical_services` contains both lifecycle status and legacy-style `active`/`deleted` fields, creating multiple activity signals.
- `MedicalServiceCategory` exists in Java while the current migration set does not show a corresponding table; `MedicalSpecialty`/specialty references also need lifecycle review.
- `Unknown Service` is reachable from `ClaimLine`, `ClaimMapper`, and `PreAuthorizationService`, and the provider hook contains explicit fallback logic.

### Not verifiable without DB access

Duplicate service names, duplicate provider codes per contract, orphan pricing rows, claim lines without pricing IDs/codes, benefit rules pointing to missing categories, inactive services used by new claims, and archived services used by historical claims require safe SELECT queries against the live database. Docker access failed with a permission error, so no data-quality counts are asserted here.

## 13. Risks

### High

- Dual direct-import and governed-import paths can bypass official service matching and version publication.
- Claim lines are financially active without relational enforcement of pricing/catalog identity.
- Category-oriented benefit matching combined with multiple coverage/calculation services can produce different coverage, copay, cap, or pre-approval outcomes.
- `Unknown Service` and generated/direct provider codes remain reachable.

### Medium

- Nullable `version_id` leaves legacy pricing outside the immutable version backbone.
- Multiple active/inactive/deleted flags on services can diverge.
- Stale entities and compatibility fields make the authoritative taxonomy unclear to maintainers.
- Live duplicate/orphan state is unknown.

### Low

- Historical snapshots and audit/version tables provide useful traceability once the governed path is used.
- Partial unique index prevents more than one active price-list version per contract.

## 14. What is working correctly

- Official service codes are unique at the catalog table level.
- Import staging separates raw provider rows from final catalog decisions.
- Review metadata and classification history exist.
- Financial validation has blocker/warning concepts and is intended to run at publish.
- Published price-list versions are modeled as immutable artifacts with publisher metadata.
- Draft/active/superseded/archived version states and one-active-per-contract enforcement exist.
- Claim financial decisions are snapshot-oriented, supporting historical reproducibility after a decision.
- Controller-level RBAC is present for taxonomy, classification, pricing, claims, pre-authorizations, and reports.

## 15. What is unclear or incomplete

- Which frontend pages are currently linked to the legacy direct Excel endpoints in the deployed build.
- Whether all production pricing rows have a `version_id` and whether all new rows use the governed path.
- Whether ambiguous import lines can be approved in bulk under current configuration and whether any API permits approval without a final service/category.
- Exact precedence parity among benefit and coverage services.
- Live duplicate/orphan/inactive-row counts.
- Whether `MedicalServiceCategory` and specialty entities are dead code or still used by runtime paths.

## 16. Recommended follow-up tickets

1. **Unify pricing ingestion:** disable/remove legacy direct Excel endpoints after proving UI/API reachability and migrate remaining data to versioned imports.
2. **Enforce catalog identity:** require a final governed service/category for publish and require `version_id` for new pricing rows; decide how contract-only services are intentionally supported.
3. **Harden claim-line integrity:** validate pricing item, contract, service code, category, active status, and effective version server-side; add appropriate immutable references or snapshots.
4. **Consolidate benefit matching:** define one precedence contract and make all coverage/cost services call the same resolver.
5. **Remove/retire dead taxonomy entities and fields:** document or delete `MedicalServiceCategory`, specialty remnants, and compatibility fields only after usage analysis.
6. **Run live data-quality audit:** safe SELECTs for duplicates, orphans, inactive use, missing links, and `Unknown Service` incidence.
7. **Block unknown/custom services:** require explicit review workflow for free-text/manual services and prohibit financial approval until resolved.

## 17. No-code-change confirmation

No backend, frontend, migration, configuration, or database data was modified. The only requested artifact created by this ticket is this report.

## 18. No-commit/no-push confirmation

No commit was created and nothing was pushed.

**MEDICAL-DICTIONARY-SYSTEM-AUDIT-1 READY FOR REVIEW**
