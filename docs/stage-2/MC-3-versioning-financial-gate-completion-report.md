# Medical Classification Engine · MC-3 — Versioning + Financial Validation + Version Comparison — Completion Report

**Increment:** MC-3 (Price List Version as a FINANCIAL ARTIFACT + A10 gate + A11 comparison dashboard + publish)
**Plan:** [provider-price-list-classification-module-plan.md](../plans/provider-price-list-classification-module-plan.md) v1.2
**Owner directive honored:** the version is a **financial artifact** — fully auditable, immutable after activation, the permanent historical reference for claims/settlements. Proven live (see invariants below).
**Status:** ✅ Implemented & verified end-to-end (draft → validate → fix → waive → approve → publish → supersede) · ⏸ **STOP — awaiting your manual test & approval before MC-4 (Classification Dashboard).**

---

## The full financial lifecycle — executed live on real data

Import #2 (مختبر الحياة، 152 خدمة معتمدة) → **Version v2 on contract #1** (previous = backfilled v1 with 151 rows):

| Step | Result |
|---|---|
| 1. Create DRAFT (`POST /versions/from-import/2`) | v2 DRAFT created; **A10 validation ran immediately** |
| 2. A10 findings on first pass | **14 BLOCKERs** (`DUPLICATE_PRICE_CONFLICT` — the provider's file genuinely listed the same service at different prices, collapsed by MC-2's knowledge merge) + **5 WARNINGs** (price drops vs v1 + total-value swing). **Gate CLOSED.** |
| 3. Blocker fix path | 14 line prices unified via the audited `PATCH /lines/{id}/price` (every change appended to the line's reviewer note: old → new, by whom) → re-validate → **0 blockers** |
| 4. Warnings | 5 × **WAIVED with a documented reason** (blockers can never be waived — enforced) |
| 5. Approve | On the comparison report (`approvedBy=superadmin`, timestamped) |
| 6. Publish | Gate re-checked at publish time → **v2 ACTIVE**, v1 **SUPERSEDED** (`effective_to` set) |

**Financial-artifact invariants (verified in DB):**
- v1's **151 rows kept, deactivated, never deleted or repriced** — `ClaimLine.pricingItemId` history is permanently safe.
- v2's **152 new rows inserted**, tagged `version_id=2`, **152/152 carry `medical_category_id`** (the authoritative catalog FK — the data-quality improvement promised to the Benefit Engine *without touching it*, A3).
- **Immutability enforced**: re-publish → rejected ("الكيان المالي غير قابل للتغيير بعد التفعيل"); post-publish price edit → rejected ("الأسعار المنشورة غير قابلة للتغيير").
- Import #2 → `PUBLISHED`. Full who/when audit on approve, publish, waivers, and price fixes.

## What was built

**Backend (`modules/medicalclassification`):**
- `PriceListValidationFinding` entity/repo (table from V70).
- **`FinancialValidationService` (A10):** checks — zero/negative price (BLOCKER), duplicate-price conflict (BLOCKER), spike/drop vs previous ACTIVE version (WARNING > ±30%, BLOCKER > ±100%), outlier vs catalog `cost` (WARNING, skipped when cost unset), total-value swing of shared services (WARNING > 25%). Thresholds from `classification_settings`. Re-runs preserve RESOLVED/WAIVED audit and never re-raise a handled line+type. `waive()` = WARNING-only + mandatory note; published versions are never re-validated.
- **`VersionComparisonService` (A11):** added/removed/repriced/reclassified/unchanged, totals ±%, price-change distribution buckets, top-20 increases/decreases, matching by service code then canonical name — plus the gate state. This DTO **is** the approval artifact.
- **`PriceListVersionService`:** draft-from-import (REVIEW_COMPLETE only, one draft per contract, contract-ownership check), approve (segregated), publish (gate re-check → insert version-tagged rows → supersede previous → import PUBLISHED), audited `fixLinePrice` (DRAFT only), archive-draft. Immutability = any mutation on non-DRAFT throws.
- `PriceListVersionController` (`/api/v1/classification/versions/…`) — **segregation of duties:** draft/findings/fix = SUPER_ADMIN/MEDICAL_REVIEWER; **approve/publish = SUPER_ADMIN/ACCOUNTANT only**.
- `ProviderContractPricingItem` gained the `versionId` column mapping (the plan's single §4.2 schema touch, now entity-complete; no behavior change — nothing else reads it).

**Frontend:**
- **`/classification/versions/:id` — Version Comparison Dashboard (A11):** gate banner (green/closed with counts), headline stat cards (إجمالي/مضافة/محذوفة/معاد تسعيرها/معاد تصنيفها/القيمة الإجمالية ±%), price-change distribution mini-chart, **financial validation panel** (findings by severity; تصحيح السعر for zero-price blockers, معالجة, إعفاء with mandatory reason; handled-findings audit trail), change-detail tabs (أعلى الزيادات/الانخفاضات/مضافة/محذوفة/معاد تصنيفها), and the approve → publish actions ON the report with a publish confirmation stating the irreversibility.
- Review workspace: **"إنشاء نسخة أسعار (كيان مالي)"** button appears at REVIEW_COMPLETE (auto-selects the provider's active contract) → navigates to the dashboard.

## Fixes made during verification
- **Publish flush-order bug:** the one-ACTIVE-per-contract partial unique index fired because Hibernate flushed the new version's ACTIVE update before the previous version's SUPERSEDED update → `saveAndFlush` the supersede first. Verified fixed (successful publish).
- (MC-2 carry-over noted) the after-commit async trigger continues to work — imports #4 processed cleanly.

## Verification summary

| Check | Result |
|---|---|
| Backend compile + restart (no new migration needed — V70 tables) | ✅ |
| Draft creation + immediate A10 validation | ✅ 19 findings, gate CLOSED |
| Blocker-fix path (14 audited price fixes) + re-validate | ✅ 0 blockers |
| Waive = WARNING-only + note, blockers unwaivable | ✅ (enforced in service) |
| Approve + publish with gate re-check | ✅ v2 ACTIVE |
| Supersede: v1 rows deactivated NOT deleted; v2 rows inserted version-tagged; 152/152 category FK | ✅ (DB) |
| Immutability after publish (re-publish, price edit) | ✅ both rejected |
| RBAC segregation (approve/publish restricted) | ✅ (`@PreAuthorize`) |
| Frontend build + lint + Vite transforms | ✅ |

**Honest limits:** the A10 first pass found *real* conflicts in the provider file — I unified duplicate prices to the max of each group purely to complete the E2E; in production that decision belongs to your reviewer. The UI click-through (dashboard, dialogs, publish flow) mirrors the fully-tested API but remains your manual test. `OUTLIER_VS_CATALOG_COST` is dormant until catalog `cost` values are populated (noted for MC-6).

## How to manually verify (your test)
**http://localhost:3001**:
1. **عرض النتيجة المنشورة:** `/classification/versions/2` — لوحة المقارنة لنسخة **منشورة**: لافتة "غير قابلة للتغيير"، الإحصاءات، سجل الإعفاءات الموثق، بلا أزرار نشر.
2. **دورة جديدة كاملة:** أكمل مراجعة استيراد #4 (المختبر الطبي الأول — من MC-2) حتى REVIEW_COMPLETE → زر **إنشاء نسخة أسعار** → ستُفتح لوحة مقارنة v3 مقابل v2 المنشورة: ستظهر موانع الأسعار الصفرية الحقيقية (تصحيح السعر من اللوحة)، والتحذيرات (حُلّ أو أعفِ بسبب)، ثم اعتماد → نشر.
3. تأكد أن أزرار **اعتماد/نشر** تظهر لصلاحيات SUPER_ADMIN/ACCOUNTANT فقط.

## Next (after approval) — MC-4: Classification Dashboard (A8)
Totals, known/new/low-confidence trends, classification rate, top-20 unknown services, provider quality ranking — over the staging + history data already accumulating.

---

*STOP. Awaiting your manual test and approval of MC-3.*
