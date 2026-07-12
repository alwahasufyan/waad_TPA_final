# MC-4C Simplified Closure Report

**Date:** 2026-07-12
**Scope:** Simplify & finalize the provider-contract price-list workflow (price correction, add service, deactivate service, classification/code correction), engine path, session startup, lookup, currency, audit. No Reporting, no MC-5, no Claims/Benefits/Settlements/Docker-workflow/.claude changes.
**Approved business rule applied:** individual service edits update the ACTIVE price list in place (audited) and **never create a new version**. A new version is created only by a full import or by restoring an archived version.

---

## 1. Files changed

**Backend — new:**
- `db/migration/V74__mc4c_direct_price_edits.sql` — widens `price_change_audit` operation vocabulary + `old_value`/`new_value` columns.
- `providercontract/dto/ContractPriceEditDtos.java` — request/response records.
- `providercontract/service/ContractPriceEditService.java` — the 4 direct, audited, version-less operations + audit trail.
- `providercontract/controller/ContractPriceEditController.java` — REST endpoints.
- `medicaltaxonomy/controller/MedicalServiceLookupController.java` — lookup (present from prior working tree; hardened here).

**Backend — modified:**
- `pricelist/entity/PriceChangeAudit.java` — extended `ChangeType` enum + `oldValue`/`newValue`.
- `pricelist/controller/PriceListVersionController.java` — `sourceType` added to version summary card.
- `engine/service/CliClassificationEngineClient.java` — clear engine-path diagnostic.

**Frontend — new:**
- `components/classification/ContractPriceEditDialogs.jsx` — 4 row dialogs + audit dialog.

**Frontend — modified:**
- `components/classification/ContractPriceListTab.jsx` — row-action menu + top actions + audit; removed the generic «تعديل استثنائي» and the version-report navigation.
- `services/api/provider-contracts.service.js` — 5 direct-edit service calls.
- `services/api/auth.service.js` — `session/me` now `suppress401Handling`.

**Not modified (deliberately):** `ExceptionEditDialog.jsx` remains in the tree but is no longer imported (dead code from the old flow); out-of-scope `ر.س` occurrences in Claims/Members/BenefitPolicy left untouched.

---

## 2. Old behavior removed

- The single large «تعديل استثنائي» dialog that bundled price/add/deactivate and **created a PATCH draft version** then **navigated to a version report** for every single-row change.
- Row edits no longer route through draft → validate → approve → publish.
- The generic exception button/icon on each row is gone.

---

## 3. New behavior implemented

- **Row actions** (⋮ menu per active row): تعديل السعر · تعديل التصنيف / الكود · إيقاف الخدمة.
- **Top actions:** رفع قائمة أسعار جديدة · إضافة خدمة جديدة · سجل التعديلات.
- Each edit: small focused dialog → save → **direct in-place update of the active pricing item** → audit row → table refresh → success toast → **stays on the same contract page** (no navigation, no version).
- Endpoints:
  - `POST /api/v1/provider-contracts/{id}/pricing/items/{itemId}/price-correction`
  - `POST /api/v1/provider-contracts/{id}/pricing/items`
  - `POST /api/v1/provider-contracts/{id}/pricing/items/{itemId}/deactivate`
  - `POST /api/v1/provider-contracts/{id}/pricing/items/{itemId}/classification`
  - `GET  /api/v1/provider-contracts/{id}/pricing/audit`

---

## 4. Confirmation: row edits no longer create versions

**Verified live.** Contract 1 versions before edits = **5**; after price-correction + add + deactivate + classification = **5**. No version row created by any single-row edit. (A new version is still created only by import — the existing import→review→publish flow — unchanged.)

**Why this is safe:** `ClaimLine` is a full snapshot entity (persists `unitPrice`, `approvedUnitPrice`, `totalPrice`, `approvedAmount`, `companyShare`, `patientShare`, etc. at claim time). Editing an active price never alters historical claim results — confirmed by code inspection.

---

## 5. Price correction test result
`POST .../items/{id}/price-correction {newPrice:91.00, reason:"..."}` → **HTTP 200**, `contractPrice=91.0`. Audit row `PRICE_CORRECTION old=… new=91.0`. No new version.

## 6. Add service test result
`POST .../items {serviceName, serviceCode:E2E-001, categoryId, price:45, reason}` → **HTTP 200**, new item id created, appears active in the list. Audit row `ADD_SERVICE new=45.0`. No new version. (Lookup returning no match does NOT block adding — a provider service is created with code/name/category/price.)

## 7. Deactivate service test result
`POST .../items/{id}/deactivate {reason}` → **HTTP 200**, `active=false`. Item removed from the active list (active count decreased; row physically retained, not deleted). Audit row `DEACTIVATE_SERVICE ACTIVE→INACTIVE`. No new version.

## 8. Classification/code edit test result
`POST .../items/{id}/classification {newServiceCode:E2E-CODE-9, reason}` → **HTTP 200**, `serviceCode=E2E-CODE-9`. Audit row `CLASSIFICATION_CORRECTION old="<code> · <category>" new="<code> · <category>"`. No new version.

## 9. Audit test result
`GET .../pricing/audit` returns all rows with operation type, code, name, old/new value, reason, user, timestamp. DB grouped counts: `PRICE_CORRECTION`, `ADD_SERVICE`, `DEACTIVATE_SERVICE`, `CLASSIFICATION_CORRECTION` all present. Reason is mandatory (validated server-side; empty reason → 400). Surfaced in UI via the «سجل التعديلات» button.

## 10. Currency fix result
The central `utils/formatters.formatCurrency` already renders **`د.ل`** (LYD). All MC-4C / contract price-list screens use it (dialogs, tab, audit). No `ر.س` exists in any MC-4C/provider-contract file. The remaining `ر.س`/`SAR` occurrences are only in **out-of-scope** Claims/Members/BenefitPolicy components, which this task must not touch — left unchanged.

## 11. Classification engine path fix result
- Actual folder confirmed: `للمرافق معالجة اكسيل  سكربت` (double space) — contains `classify_json.py` and `tpa_service_mapper.py`.
- `classification_settings.engine.script.dir` already holds this exact double-space host path (correct).
- **Root cause of "not ready" under the current setup:** the backend runs inside a Linux Docker container (`waad-local-backend`, JRE-alpine) that has neither the Windows host folder nor Python, so it cannot execute the file-based engine. This is an architectural constraint, not a path typo.
- **Action taken:** kept the correct path and added a **clear Arabic diagnostic** — the health message now explicitly states the path is a host path invisible to a container and that the backend must run locally to use the engine. (The engine ran successfully in earlier increments when the backend was launched locally.)
- **Not done (out of scope / by constraint):** installing Python + mounting the folder into the container (would modify the Docker image/workflow the task says not to touch, and pull heavy native deps into alpine). The engine is **not required** for the MC-4C price-edit closure.

## 12. No-session fix result
`GET /api/v1/auth/session/me` returning 401 when unauthenticated is expected. `auth.service.me()` now sends `suppress401Handling: true`, so the axios interceptor bails early — no `auth:session-expired` dispatch, no error log, no scary "unexpected error" toast on first load. Authenticated calls are unaffected; security is unchanged (backend still enforces auth).

## 13. Build results
- Backend `mvn compile`: **exit 0**.
- Backend `mvn test-compile`: **exit 0**.
- Frontend `yarn build`: **success** (built, lint of changed files: 0 errors).
- Docker backend rebuild: **built** (`waad-backend:local`), Flyway **V74 applied** ("now at version v74").
- Docker frontend rebuild: **built** (`waad-frontend:local`).

## 14. Docker health results
`waad.ps1 doctor`: all checks **[OK]** — Docker, compose files, `.env.local`, required vars, ports 3001/8081/5433, backend health reachable, frontend reachable. `waad.ps1 health`: backend + frontend reachable. Containers: `waad-local-backend Up (healthy)`, `waad-local-frontend Up (healthy)`.

⚠️ One operational gotcha discovered & documented: recreating containers with plain `docker compose up` (without `--env-file .env.local`) picks up a stale `DB_PASSWORD` and fails DB auth. Use `waad.ps1` (or pass `--env-file .env.local`). No code issue.

## 15. Remaining issues / notes
1. **Engine under Docker** — cannot run without mounting the folder + installing Python (deferred; not needed for MC-4C). Runs when backend is launched locally.
2. **Legacy PATCH versions v4/v5** (from the earlier wrong exception flow) still exist on contract 1; **v5 is ACTIVE and holds the current active pricing items**, so it must not be deleted. The new direct-edit flow creates no further versions. **Proposed dev cleanup (pending your approval):** leave v5 ACTIVE as-is (it is the live list); optionally relabel/annotate v4 as an artifact. No production-like data deleted.
3. **Restore archived version** UI action is not yet built (backend rollback endpoint exists internally). Documented as pending — the read-only history is shown; a one-click restore button can be added on request.
4. `ExceptionEditDialog.jsx` is now dead code (unused) — safe to delete later.
5. Deactivated services are hidden from the active list by default; a "show inactive" toggle can be added if desired.

## 16. Final status

**MC-4C CLOSED** for the approved simplified scope: version-less, audited, row-level price/add/deactivate/classification edits are implemented, built, deployed to the local Docker stack, and verified end-to-end (all four operations return 200, are audited, and create no new version); lookup never-500 confirmed; currency is `د.ل`; session-me 401 handled silently; engine path corrected with a clear diagnostic (engine execution under Docker remains a documented, out-of-scope constraint).

Items #2–#5 in §15 are minor follow-ups that do not block closure and await your direction.
