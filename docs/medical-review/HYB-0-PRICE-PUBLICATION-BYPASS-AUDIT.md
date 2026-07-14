# HYB-0 — Provider Price Publication Bypass Audit

**Audit date:** 2026-07-13  
**Scope:** original source-level, read-only audit of every identified write path to `provider_contract_pricing_items`. The findings below are the baseline for the HYB-0B implementation update added at the end of this document.

## Executive verdict

**Original final decision: C. Bypass exists and must be closed before hybrid work.**

> **HYB-0B superseding conclusion (2026-07-13):** The prohibited direct-import and un-audited published-price paths identified by this audit have been closed. Approved, limited operational corrections remain available as **audited operational edits**, with role separation, a mandatory reason, a complete before/after snapshot, and transactional audit persistence. The governed import/review/DRAFT/financial-validation/publication workflow remains the required path for a full price-list import or broad change.

System A has a sound governed publication path:

`price_list_imports` staging → medical review → `provider_price_list_versions` DRAFT → financial validation → authorized publication.

However, it is not the only path that can create, update, or deactivate `provider_contract_pricing_items`. The repository exposes active-price writes through a legacy Excel endpoint, generic single/bulk CRUD endpoints, an actively rendered Contract Price List tab, and a provider-portal endpoint used from claim-entry screens. Those paths can alter rows used as effective provider pricing without a price-list import, DRAFT version, financial validation, comparison, approval, or immutable version publication.

The most serious issue is not merely an unused legacy endpoint: `ContractPriceListTab` renders direct correction/add/deactivate/classification actions, and `ProviderClaimsSubmission.jsx` plus `ClaimBatchEntry.jsx` post to the provider-portal direct-create endpoint. These are reachable UI paths.

## Authority and baseline

The table is structurally capable of supporting the governed model but does not enforce it:

- [`ProviderContractPricingItem.java`](../../backend/src/main/java/com/waad/tba/modules/providercontract/entity/ProviderContractPricingItem.java) maps `provider_contract_pricing_items`; `version_id` is nullable and the entity has no invariant requiring a DRAFT/ACTIVE version or publication authorization.
- [`V70__medical_classification_module.sql`](../../backend/src/main/resources/db/migration/V70__medical_classification_module.sql) creates `provider_price_list_versions`, a one-ACTIVE-version partial unique index, and adds nullable `provider_contract_pricing_items.version_id`.
- [`V71__mc1_import_provenance_and_version_backfill.sql`](../../backend/src/main/resources/db/migration/V71__mc1_import_provenance_and_version_backfill.sql) backfills historical rows to v1. It does not prevent later null-version or direct writes.
- [`PriceListVersionService`](../../backend/src/main/java/com/waad/tba/modules/medicalclassification/pricelist/service/PriceListVersionService.java) is the only implementation that consistently creates version-tagged immutable publication artifacts.

There is no class named `ProviderContractPricingItemController`; the generic item CRUD endpoints are implemented in [`ProviderContractController`](../../backend/src/main/java/com/waad/tba/modules/providercontract/controller/ProviderContractController.java).

## Original write-path inventory (superseded status is recorded below)

| Endpoint/action | Caller/UI | Roles | Writes staging? | Creates DRAFT version? | Financial validation? | Writes live pricing directly? | Audit? | Classification |
|---|---|---|---:|---:|---:|---:|---:|---|
| `POST /api/v1/classification/imports` | Classification Imports page | `SUPER_ADMIN`, `MEDICAL_REVIEWER` | Yes (`PriceListImport`, lines) | No | No; later at draft creation | No | Import/review provenance | **AUTHORIZED GOVERNED PATH** |
| Review decisions / `PATCH /classification/imports/{id}/review/lines/{lineId}/price` | Classification Review page | `SUPER_ADMIN`, `MEDICAL_REVIEWER` | Yes, import lines only | No; `finishReview` creates DRAFT | No; later | Reviewer identity/time/note | **AUTHORIZED GOVERNED PATH** |
| `POST /classification/versions/from-import/{importId}`, then approve/publish | Classification Review/Version pages | Draft: `SUPER_ADMIN`, `MEDICAL_REVIEWER`; approve/publish: `SUPER_ADMIN`, `ACCOUNTANT` | Consumes reviewed staging | Yes | Yes, on draft creation and again at publish | Only at authorized publish; inserts a new version and deactivates prior rows | Version approval/publication fields + findings | **AUTHORIZED GOVERNED PATH** |
| PATCH exception draft: create/record/add/deactivate/approve/publish/rollback under `/classification/versions` | `ExceptionEditDialog` is governed; version page performs approval/publication | Draft edits: `SUPER_ADMIN`, `MEDICAL_REVIEWER`; publish/rollback: `SUPER_ADMIN`, `ACCOUNTANT` | No import staging (appropriate for an exception) | Yes; PATCH/ROLLBACK draft | Yes, on draft mutation and publish | No live change until `applyPatchDraft`; then atomically activates draft rows | `PriceChangeAudit`, version approval/publication fields | **AUTHORIZED GOVERNED PATH** |
| `POST /api/v1/provider-contracts/{contractId}/pricing/import` | Frontend API wrapper remains; no checked component imports this wrapper | `SUPER_ADMIN`, `ACCOUNTANT` | No | No | No | Yes: `PriceListExcelTemplateService.upsertPricingItem` inserts/updates active rows | No price-change/version audit | **LEGACY BYPASS — NOT REACHABLE FROM CHECKED UI, BUT REACHABLE AS AN AUTHORIZED REST API** |
| Orphan `ProviderContractPricingExcelService#importFromExcel` | No controller or caller found | N/A | No | No | No | Yes if invoked by future code | No | **LEGACY BYPASS — NOT CURRENTLY ROUTED** |
| `POST /api/v1/provider-contracts/{contractId}/pricing` | Client API wrapper only; no checked UI caller | `SUPER_ADMIN`, `ACCOUNTANT` | No | No | No | Yes: creates active item | No | **UNSAFE DIRECT WRITE — NOT REACHABLE FROM CHECKED UI, BUT REACHABLE AS AN AUTHORIZED REST API** |
| `POST /api/v1/provider-contracts/{contractId}/pricing/bulk` | Client API wrapper only; no checked UI caller | `SUPER_ADMIN`, `ACCOUNTANT` | No | No | No | Yes: loops through direct create | No | **UNSAFE DIRECT WRITE — NOT REACHABLE FROM CHECKED UI, BUT REACHABLE AS AN AUTHORIZED REST API** |
| `PUT /api/v1/provider-contracts/pricing/{pricingId}` | Client API wrapper only; no checked UI caller | `SUPER_ADMIN`, `ACCOUNTANT` | No | No | No | Yes: updates active item in place | No | **UNSAFE DIRECT WRITE — NOT REACHABLE FROM CHECKED UI, BUT REACHABLE AS AN AUTHORIZED REST API** |
| `DELETE /api/v1/provider-contracts/pricing/{pricingId}` and `DELETE /{contractId}/pricing` | Client API wrappers only; no checked UI caller | `SUPER_ADMIN`, `ACCOUNTANT` | No | No | No | Yes: soft-deactivates active rows | No | **UNSAFE DIRECT WRITE — NOT REACHABLE FROM CHECKED UI, BUT REACHABLE AS AN AUTHORIZED REST API** |
| `POST /api/v1/provider-contracts/{contractId}/pricing/items/{itemId}/price-correction` | Rendered `ContractPriceListTab` → `ContractPriceEditDialogs` | `SUPER_ADMIN`, `ACCOUNTANT` | No | No | No | Yes: updates active price in place | `PriceChangeAudit` only | **ADMIN MANUAL EXCEPTION / UNSAFE DIRECT WRITE** |
| `POST /api/v1/provider-contracts/{contractId}/pricing/items` | Rendered `ContractPriceListTab` add-service dialog | `SUPER_ADMIN`, `ACCOUNTANT` | No | No | No | Yes: creates active service immediately | `PriceChangeAudit` only | **ADMIN MANUAL EXCEPTION / UNSAFE DIRECT WRITE** |
| `POST /api/v1/provider-contracts/{contractId}/pricing/items/{itemId}/deactivate` and `/classification` | Rendered `ContractPriceListTab` row actions | `SUPER_ADMIN`, `ACCOUNTANT` | No | No | No | Yes: changes active state/classification in place | `PriceChangeAudit` only | **ADMIN MANUAL EXCEPTION / UNSAFE DIRECT WRITE** |
| `POST /api/v1/provider/my-contract/pricing` | `ProviderClaimsSubmission.jsx` and `ClaimBatchEntry.jsx` | `SUPER_ADMIN`, `MEDICAL_REVIEWER`, `DATA_ENTRY`, `PROVIDER_STAFF` | No | No | No | Yes: delegates to direct `ProviderContractPricingItemService#create` | No | **UNSAFE DIRECT WRITE — REACHABLE FROM UI** |
| Contract hard-delete cleanup | Provider Contract administration | `SUPER_ADMIN`, `ACCOUNTANT` | No | No | No | Hard-deletes all pricing rows only when deleting an already soft-deleted contract | No separate pricing audit | **ADMIN MANUAL EXCEPTION** (contract lifecycle, not publication) |
| Read-only price search/listing, version comparison, validation findings, audit views | Contract, provider, claim/pre-auth and version UIs | Varies | No | No | No | No | N/A | **READ ONLY** |

## Evidence by implementation

### Governed import/review/version publication

1. [`PriceListImportController#upload`](../../backend/src/main/java/com/waad/tba/modules/medicalclassification/pricelist/controller/PriceListImportController.java) delegates to `ImportOrchestrationService#createImport`; it writes imports and import lines, not contract pricing rows.
2. [`ReviewService#decide`, `#decideBulk`, `#updateLinePrice`, `#finishReview`](../../backend/src/main/java/com/waad/tba/modules/medicalclassification/pricelist/service/ReviewService.java) act on `PriceListImportLine`. `finishReview` calls `PriceListVersionService#createDraftFromImport` only after review completion.
3. [`PriceListVersionService#createDraftFromImport`](../../backend/src/main/java/com/waad/tba/modules/medicalclassification/pricelist/service/PriceListVersionService.java) requires `REVIEW_COMPLETE` and at least one `APPROVED` line; it creates a DRAFT and runs `FinancialValidationService#validate`.
4. [`PriceListVersionService#publish`](../../backend/src/main/java/com/waad/tba/modules/medicalclassification/pricelist/service/PriceListVersionService.java) requires DRAFT and `approvedBy`, revalidates, blocks a closed gate, inserts new version-tagged rows, supersedes the prior ACTIVE version, deactivates prior active rows, and records publication identity/time on the version. The method is `@Transactional`.
5. [`FinancialValidationService#validate`](../../backend/src/main/java/com/waad/tba/modules/medicalclassification/pricelist/service/FinancialValidationService.java) uses `VersionCandidateService` and persists validation findings. It is only invoked by governed version paths; it is not called by legacy/manual direct pricing paths.

### Governed PATCH and ROLLBACK exception workflow

1. [`PriceListVersionService#createPatchDraft`](../../backend/src/main/java/com/waad/tba/modules/medicalclassification/pricelist/service/PriceListVersionService.java) reuses an existing PATCH DRAFT, otherwise clones active rows to inactive version-tagged draft rows and validates them.
2. `#recordPriceChange`, `#addServiceException`, and `#deactivateServiceException` require a PATCH DRAFT, preserve audit entries in `PriceChangeAudit`, and re-run validation. They do not alter the current active rows.
3. `#applyPatchDraft` requires `approvedBy`, revalidates, checks the gate, supersedes/deactivates the existing ACTIVE rows, activates the draft rows, and saves the new ACTIVE version within one transaction.
4. `#createRollbackDraft` clones a historical non-DRAFT version to an inactive ROLLBACK DRAFT, validates it, and must follow ordinary approval/publication.
5. [`PriceListVersionController`](../../backend/src/main/java/com/waad/tba/modules/medicalclassification/pricelist/controller/PriceListVersionController.java) enforces separate reviewer versus approver/publisher roles for the governed paths.

### Direct Excel bypass

[`ProviderContractPricingExcelController#importPriceList`](../../backend/src/main/java/com/waad/tba/modules/providercontract/controller/ProviderContractPricingExcelController.java) exposes:

`POST /api/v1/provider-contracts/{contractId}/pricing/import`

to `SUPER_ADMIN` and `ACCOUNTANT`. It calls [`PriceListExcelTemplateService#importFromExcel`](../../backend/src/main/java/com/waad/tba/modules/providercontract/service/PriceListExcelTemplateService.java), which processes each row and calls `upsertPricingItem`. That method calls `pricingRepository.save(item)` for a match and `pricingRepository.save(draft)` for a new row. The created item is active; it does not receive a DRAFT version, is not staged, is not medically reviewed, is not financially validated, and has no `PriceChangeAudit` record.

The old [`ProviderContractPricingExcelService#importFromExcel`](../../backend/src/main/java/com/waad/tba/modules/providercontract/service/ProviderContractPricingExcelService.java) is also a direct active-row upsert implementation (`pricingRepository.save` at its update and insert branches). A full source search found no controller injection or production caller for this service. It is therefore dormant rather than the endpoint currently called by the controller, but its presence is a future-regression risk.

### Generic CRUD/bulk bypass

[`ProviderContractController`](../../backend/src/main/java/com/waad/tba/modules/providercontract/controller/ProviderContractController.java) exposes direct single, bulk, update, single-delete and all-delete operations. They delegate to [`ProviderContractPricingItemService#create`, `#createBulk`, `#update`, `#delete`, `#deleteByContract`](../../backend/src/main/java/com/waad/tba/modules/providercontract/service/ProviderContractPricingItemService.java).

Those methods create active items or mutate/deactivate active items in place. They check contract mutability and basic price/category constraints, but do not require `version_id`, a review import, a DRAFT, validation findings, approval, publication, or a price-change audit. `createBulk` is a loop over the same direct `create` method.

### Direct MC-4C tab bypass

[`ContractPriceEditController`](../../backend/src/main/java/com/waad/tba/modules/providercontract/controller/ContractPriceEditController.java) explicitly describes itself as “direct audited price-list edits — no new version.” Its backing [`ContractPriceEditService`](../../backend/src/main/java/com/waad/tba/modules/providercontract/service/ContractPriceEditService.java) calls `pricingItemRepository.save` directly in `#correctPrice`, `#addService`, `#deactivateService`, and `#correctClassification`.

The methods do record `PriceChangeAudit`, but an audit record is not a publication gate: there is no DRAFT, financial validation, comparison, approval, or version publication. `#addService` assigns the current active version ID when one exists, which makes an in-place added row appear associated with an already-published version without actually being published as part of it.

This is reachable from the live frontend: [`ContractPriceListTab.jsx`](../../frontend/src/components/classification/ContractPriceListTab.jsx) imports and renders `PriceCorrectionDialog`, `AddServiceDialog`, `DeactivateServiceDialog`, and `ClassificationCorrectionDialog`; [`ContractPriceEditDialogs.jsx`](../../frontend/src/components/classification/ContractPriceEditDialogs.jsx) calls the direct endpoints. The tab itself states that edits save immediately “without creating a new version.”

### Provider-portal/claim-entry bypass

[`ProviderPortalController#addMyContractPricing`](../../backend/src/main/java/com/waad/tba/modules/provider/controller/ProviderPortalController.java) exposes:

`POST /api/v1/provider/my-contract/pricing`

to `SUPER_ADMIN`, `MEDICAL_REVIEWER`, `DATA_ENTRY`, and `PROVIDER_STAFF`. It resolves an active contract and delegates to `ProviderContractPricingItemService#create`, the direct active-row creator above. It is called by:

- [`ProviderClaimsSubmission.jsx`](../../frontend/src/pages/provider/ProviderClaimsSubmission.jsx), and
- [`ClaimBatchEntry.jsx`](../../frontend/src/pages/claims/batches/ClaimBatchEntry.jsx).

Therefore a provider-staff claim-entry workflow can add a custom price to an active contract without the required governance chain. This is the highest-risk reachable bypass because it combines a low-privilege operational role with a financial master-data write.

### Scripts, seeds, and non-application writes

- No runtime shell, PowerShell, batch, or Python script writing the table was found in the checked source tree. `tmp_pricingcheck.py` contains read-only diagnostic `SELECT`s only; prior cleanup-quarantined scripts are not runtime paths.
- The only SQL `UPDATE`s found are historical Flyway migrations `V43` and `V71`; they are migration/backfill operations, not an application publication endpoint. No current seed/import SQL inserts active contract pricing rows were found.
- [`ProviderContractPricingItemRepository`](../../backend/src/main/java/com/waad/tba/modules/providercontract/repository/ProviderContractPricingItemRepository.java) still declares bulk soft/hard delete queries. The hard delete is used by `ProviderContractService#hardDelete` only after the contract is soft-deleted; it is a contract lifecycle operation and must not be used for price publication.

## Explicit answers

1. **Can a user upload Excel directly from a provider-contract page and write active pricing rows without staging?** **Yes at the API level.** `POST /provider-contracts/{contractId}/pricing/import` directly upserts active rows. The checked current frontend does not import `uploadContractPricingExcel`; its contract tab routes new full imports to `/classification/imports`. That does not make the endpoint safe: authorized users can still call it directly.
2. **Does a legacy endpoint still call direct `importFromExcel`?** **Yes, the active legacy endpoint calls `PriceListExcelTemplateService#importFromExcel`, which is a direct active-row import.** A second old `ProviderContractPricingExcelService#importFromExcel` remains in the application but has no checked controller/caller; it is dormant, not the controller's current implementation.
3. **Can `PENDING_REVIEW` or unmatched rows reach `provider_contract_pricing_items`?** **Not through the governed publication path:** only `APPROVED` staging lines are published. **Yes in effect through direct imports/manual creation:** the legacy template importer can accept rows with unresolved category mapping and persist them; generic/provider-portal creation permits no category when the DTO omits it. Neither direct path has a review status gate.
4. **Can manual CRUD modify an active published price in place?** **Yes.** Generic `PUT /provider-contracts/pricing/{pricingId}` and the ContractPriceEdit direct correction endpoint both mutate active items. The latter is audited but not version-governed.
5. **Can bulk import create or overwrite prices outside a version?** **Yes.** The Excel endpoint upserts directly; `POST /provider-contracts/{contractId}/pricing/bulk` creates directly. Neither creates a version.
6. **Can an exception/patch flow alter active prices without approval?** **The governed PATCH/ROLLBACK flow cannot:** it edits inactive DRAFT rows and requires `approvedBy` plus a green financial gate before activation. **A separate direct MC-4C edit flow can:** `ContractPriceEditService` alters the active row immediately, bypassing approval despite audit logging.
7. **Can the same price row be written through two different workflows?** **Yes.** A version-published active row can later be updated/deactivated by generic CRUD, legacy Excel upsert, or direct ContractPriceEdit operations. Direct add may even attach the existing active `version_id` while bypassing publication.
8. **Can frontend users reach deprecated/direct endpoints?** **Yes.** `ContractPriceListTab` reaches the direct-edit endpoints. `ProviderClaimsSubmission` and `ClaimBatchEntry` reach the provider-portal direct-create endpoint. The generic CRUD and Excel client wrappers are retained but were not imported by a checked UI component; their REST endpoints remain callable by authorized users.
9. **Are all publication actions transactional and audited?** **No.** Governed `publish` and `applyPatchDraft` are transactional and carry version publication/audit evidence. Direct paths are transactional at method level but are not publication transactions and mostly have no audit. ContractPriceEdit writes an audit record but has no validation/approval/publication.
10. **Do claims continue using immutable historical snapshots after rollback?** **Yes, by source evidence.** The governed rollback clones to a new version and deactivates prior price rows rather than editing historical rows. [`ClaimMapper`](../../backend/src/main/java/com/waad/tba/modules/claim/mapper/ClaimMapper.java) stores pricing item ID and financial values on `ClaimLine`; [`ClaimService`](../../backend/src/main/java/com/waad/tba/modules/claim/service/ClaimService.java) documents/enforces financial snapshot immutability after approval. This protects existing claim financial results from a governed rollback. This audit did not execute a live rollback test.

## Bypass risks and smallest safe closure

| Severity | Bypass | Root risk | Smallest safe closure |
|---|---|---|---|
| **HIGH** | Provider portal direct creation | `PROVIDER_STAFF`/claim-entry UI can alter the active pricing catalogue during claim entry. | Disable `POST /provider/my-contract/pricing` for active contracts and change the two UI callers to submit a correction/request or governed DRAFT action; do not call `ProviderContractPricingItemService#create` from provider/claim UI. |
| **HIGH** | ContractPriceListTab direct-edit dialogs | `SUPER_ADMIN`/`ACCOUNTANT` alter current rows instantly; audit alone does not provide validation, approval, or immutable version history. | Replace dialog calls with the existing PATCH DRAFT endpoints and version-report approval/publish path; remove/hide the direct write actions. |
| **HIGH** | Provider-contract Excel import | Authorized REST caller can upsert active rows, including unresolved mapping, with no financial gate/audit/version. | Disable/deprecate `POST /provider-contracts/{contractId}/pricing/import`; route its UI/API contract to `POST /classification/imports` only. Retain template download only if it is clearly read-only. |
| **HIGH** | Generic CRUD/bulk/delete endpoints | They create/update/deactivate active rows outside a version, and delete-all can retire a live list. | Restrict/remove write mappings for published/active contracts; if retained for pre-publication contract setup, enforce “no ACTIVE price-list version” and require a DRAFT-only scope. |
| **MEDIUM** | Dormant `ProviderContractPricingExcelService` | It is an alternate direct importer ready to be accidentally wired back in. | Mark deprecated and remove from component scanning only after explicit implementation approval, or make it fail closed and cover with a route-absence test. |
| **MEDIUM** | `version_id` is nullable and not lifecycle-enforced | Any service can create active null-version rows or mutate a version-tagged row. | Add application-level publication guard before hardening schema; later consider DB enforcement only after all legacy rows/workflows have been migrated safely. |
| **LOW** | Contract hard-delete cleanup | It removes pricing history only as part of permitted contract destruction. | Keep separate from publication; retain existing inactive-contract precondition and audit contract deletion. |

## Endpoints to retain versus deprecate/disable

### Retain as the only financial publication interface

- `POST /api/v1/classification/imports`
- Review endpoints under `/api/v1/classification/imports/{importId}/review`
- Version endpoints under `/api/v1/classification/versions` for draft, validation, comparison, approval, publication, governed PATCH, and governed ROLLBACK.
- Read-only contract price-list summary/list/search and audit endpoints.

### Deprecate/disable as price-publication interfaces

- `POST /api/v1/provider-contracts/{contractId}/pricing/import`
- `POST /api/v1/provider-contracts/{contractId}/pricing`
- `POST /api/v1/provider-contracts/{contractId}/pricing/bulk`
- `PUT /api/v1/provider-contracts/pricing/{pricingId}`
- `DELETE /api/v1/provider-contracts/pricing/{pricingId}`
- `DELETE /api/v1/provider-contracts/{contractId}/pricing`
- `POST /api/v1/provider-contracts/{contractId}/pricing/items/{itemId}/price-correction`
- `POST /api/v1/provider-contracts/{contractId}/pricing/items`
- `POST /api/v1/provider-contracts/{contractId}/pricing/items/{itemId}/deactivate`
- `POST /api/v1/provider-contracts/{contractId}/pricing/items/{itemId}/classification`
- `POST /api/v1/provider/my-contract/pricing`

The endpoints need not be physically deleted in the first closure change. The smallest operational step is to return a controlled deprecation/409 response for active/published price lists and direct callers to the governed DRAFT workflow, while preserving only explicitly approved pre-publication setup behavior if that is still required.

## Required regression tests before hybrid work

1. An authorized API client cannot create, update, bulk-create, import, or deactivate an active published pricing row through any deprecated direct endpoint.
2. `PROVIDER_STAFF`, `DATA_ENTRY`, and `MEDICAL_REVIEWER` cannot create provider-contract pricing from provider/claim-entry routes.
3. A direct Excel upload is accepted only by `/classification/imports` and produces `price_list_imports`/lines, never a pricing item before review/publication.
4. A `PENDING_REVIEW`, `NEEDS_REVIEW`, rejected, unmatched, or zero/invalid-price line cannot become a pricing item.
5. PATCH add/correct/deactivate writes only inactive DRAFT rows; active rows remain unchanged until an approver publishes a green gate.
6. PATCH and ROLLBACK publication require approval, a green validation gate, and result in exactly one ACTIVE version per contract.
7. Publication inserts new version-tagged rows and deactivates, rather than updates/deletes, prior rows.
8. Every governed exception change yields a `PriceChangeAudit` entry tied to its version; no direct active-row endpoint remains usable as a substitute.
9. A rollback leaves existing approved claim-line financial snapshots unchanged.
10. Route-level and frontend tests assert that ContractPriceListTab and claim-entry pages no longer call direct pricing mutation URLs.

## Audit conclusion

The governed implementation is a credible target architecture, and its import, review, DRAFT, validation, approval, publish, patch, and rollback mechanisms should remain. It is not currently authoritative in practice because active pricing rows have multiple competing direct writers. Hybrid work must wait until those direct writers are disabled, redirected, or strictly constrained to a non-published setup state.

**Explicit implementation confirmation:** no implementation was performed; this audit created this report only.

## HYB-0B implementation update — superseding conclusion

### Correct policy classification

The original audit correctly identified *un-audited* publication bypasses, but its treatment of every in-place operational edit as unsafe was too broad for the approved operating model. The following are now **APPROVED AUDITED OPERATIONAL EDITS**, not legacy bypasses:

- price correction, add service, deactivate service, reactivate service, and classification correction from `ContractPriceListTab`;
- only on an active contract with an already-published active price-list version;
- price operations: `SUPER_ADMIN` or `ACCOUNTANT`; classification: `SUPER_ADMIN` or `MEDICAL_REVIEWER`;
- a nonblank reason and server-authenticated actor are mandatory;
- each operation persists a `PriceChangeAudit` row in the same transaction as the pricing-row mutation;
- the audit includes contract, provider, pricing-item and version identifiers, operation type, old/new price, before/after state snapshot, actor, reason, and server `created_at` timestamp.

`ProviderContractPricingItem` has optimistic locking (`row_version`) for concurrent operational edits. An audit persistence failure propagates inside the transactional service and prevents the price change from committing.

### Closed direct-write paths

| Former path | HYB-0B disposition |
|---|---|
| `POST /api/v1/provider-contracts/{contractId}/pricing/import` | Retained only for compatibility; returns HTTP `410 Gone` with an Arabic controlled-path message. |
| `ProviderContractPricingExcelService#importFromExcel` | Fails closed and cannot write rows. |
| `PriceListExcelTemplateService#importFromExcel` | Fails closed and cannot write rows. |
| Generic create/bulk/update/delete pricing CRUD | Still usable for pre-publication setup only. It rejects a contract with an ACTIVE published price-list version, directing users to audited operational edits. |
| Provider-portal pricing create | Role-restricted to `SUPER_ADMIN`/`ACCOUNTANT` and receives the same published-list guard. |
| Claim batch/provider claim-entry custom service creation | Disabled in the frontend with a clear Arabic message directing the user to a contracted service or an authorized request/correction path. It no longer calls the provider-contract pricing create endpoint. |

### Governed workflow retained

The Classification Imports workflow remains unchanged and mandatory for full imports and broad changes:

`staging → medical review → DRAFT price-list version → financial validation → authorized publication`.

MC-4C PATCH/ROLLBACK versions remain separate governed workflows. HYB-0B does not replace version publication, financial validation, approval, or historical rollback.

### Historical claims invariant

No claim pricing code was changed. Claims retain the existing claim-line amount snapshots; an operational contract-price correction does not rewrite historical claim lines, approvals, settlements, or snapshots.

### Final HYB-0B decision

**A. No prohibited publication bypass remains in the examined routes.** Operational in-place edits are an explicitly approved, role-limited, auditable policy exception; full imports and broad changes remain governed version publication. This implementation performed no medical-dictionary import, AI integration, hybrid architecture work, or unrelated module change.
