# Medical Classification Engine · MC-1 — Import & Staging — Completion Report

**Increment:** MC-1 (upload + async classification + staging + Version-1 backfill + imports screen)
**Plan:** [provider-price-list-classification-module-plan.md](../plans/provider-price-list-classification-module-plan.md) v1.2
**Status:** ✅ Implemented & verified end-to-end on a REAL provider file · ⏸ **STOP — awaiting your manual test & approval before MC-2 (review workspace).**

---

## Owner conditions — how each was honored

| Condition | Implementation | Verified |
|---|---|---|
| **#1 Idempotency by file hash** | SHA-256 computed server-side on upload; DB-level partial unique index `(provider_id, file_hash)` over non-terminal statuses + service-level check with a clear Arabic message pointing at the existing import. FAILED/CANCELLED never block a retry. | ✅ Re-uploading the same file returned **HTTP 422**: "هذا الملف مستورد مسبقًا لنفس المرفق (استيراد رقم 1، الحالة: CLASSIFIED)". |
| **#2 Full provenance** | Stored per import: `file_hash` (sha256), `file_size_bytes`, `engine_version`, `fuzz_engine`, `execution_ms`, `dictionary_version` (JSON: sha256+size of the reference, approved-categories, synonyms and Odoo-KB files at run time), `threshold_config` (settings snapshot incl. `auto_approval=false`), uploader + timestamps. | ✅ Import #1 shows: engine `waad-mce-cli/1.0.0`, rapidfuzz, 3,135 ms, all four knowledge files hashed, threshold snapshot saved. |
| **#3 No review/publish before staging is complete** | This increment exposes ONLY: upload / list / detail / staged-lines (read-only) / cancel / engine-health. No decision, approval, or publish endpoint exists; the UI states it explicitly. | ✅ Controller surface audited — nothing mutates review state. |

## What was built

**Backend (`modules/medicalclassification`):**
- `V71` migration: 4 provenance columns + the idempotency unique index + **M1 Version-1 backfill** (each contract with pricing items → `PriceListVersion #1 ACTIVE`, items tagged).
- Entities/repos: `PriceListImport`, `PriceListImportLine`, `PriceListVersion`.
- `ImportOrchestrationService`: validations (type xlsx/xls/csv/pdf/pptx, ≤25MB, provider/contract ownership, one in-flight import per contract — R7), SHA-256, module-local file storage, cancel.
- `ImportProcessingService` (`@Async`): PROCESSING → CLI engine → staging with **queue banding** (§6: engine-trusted + score ≥85 + no flags → `PENDING_BULK` hidden majority; else `NEEDS_REVIEW`), in-file duplicate detection (`DUPLICATE_IN_FILE`), zero/negative-price guard, counters, provenance; failure → `FAILED` + reason, never partial data (re-run clears prior lines first).
- `PriceListImportController` (`/api/v1/classification/imports`, roles SUPER_ADMIN/MEDICAL_REVIEWER): upload, list, detail, lines (filterable by band), cancel, engine health.

**Frontend:** `pages/classification/imports` — imports list (UnifiedMedicalTable, status chips, provenance column with hash/dictionary tooltip, needs-review counters), upload dialog (provider + facility-type hint + file; idempotency errors surfaced), 5s polling only while an import is UPLOADED/PROCESSING, engine-health banner, cancel action. Route + menu (قسم الكتالوج الطبي → "استيراد قوائم الأسعار"). **No review/publish UI (condition #3).**

## End-to-end verification (real file: مختبر الحياة، 152 خدمة)

| Check | Result |
|---|---|
| Backend compile + start; Flyway **V71 applied** | ✅ |
| **Backfill**: 1 contract with pricing items → 1 Version-1; **151/151** items tagged | ✅ (counts verified in DB) |
| Login → engine health endpoint | ✅ "OK" |
| **Upload → async classify → CLASSIFIED** | ✅ import #1: 152 lines, known 140, low-confidence 12, unknown 0, duplicates 0 |
| **Banding parity with the authoritative script** | ✅ DB: 140 `PENDING_BULK` + 12 `NEEDS_REVIEW` — exactly the script's own 140/12 split (A9 parity) |
| Staged lines API (filter NEEDS_REVIEW) | ✅ 12 lines with reason strings + confidence (75.0%, 71.4% …) |
| **Arabic integrity in DB** | ✅ raw names, reasons, category labels verified clean via psql (an apparent corruption was my console pipeline only) |
| Idempotent re-upload | ✅ rejected 422 referencing import #1 |
| Frontend production build + ESLint + Vite transform | ✅ build 28.6s exit 0; lint clean after `--fix`; page transforms 200 |

## Deviations / fixes made during verification
- **API prefix:** controller corrected to `/api/v1/...` (frontend axios baseURL is `/api/v1`; first attempt used bare `/api/`).
- **File storage:** the shared `LocalFileStorageService` whitelists only PDF/images/DICOM. Instead of loosening that **global** security policy to admit xlsx, the module stores its files itself under `storage/uploads/classification/{uuid}.{ext}` (ASCII-safe absolute paths for the engine) with its own extension/size/hash validation. Flagged for your awareness as a deliberate security choice.
- Backend restart procedure note (log-file lock) recorded in the dev-setup memory.

## Honest limits
- `curl` console tests showed mojibake in the *echoed* filename (curl/CP1256 artifact); the browser path sends correct UTF-8 — please confirm the filename renders correctly in your manual test.
- Contract selection is not in the upload dialog yet (API supports `contractId`); it becomes meaningful in MC-3 when versions attach to contracts — tell me if you want it earlier.

## How to manually verify (your test)
App: **http://localhost:3001** (`superadmin@tba.sa` / `Admin@123`) → القائمة: **إدارة الكتالوج الطبي → استيراد قوائم الأسعار** (أو `/classification/imports`):
1. زر **رفع قائمة أسعار** → اختر مرفقًا وملف Excel حقيقيًا → رفع. الحالة تتقدم تلقائيًا (بانتظار المعالجة → قيد التصنيف → مصنّف) بلا تحديث يدوي.
2. بعد الاكتمال: عدد الخدمات/تحتاج مراجعة/المحرك والزمن تظهر في الصف، وhash الملف في تلميح عمود المحرك.
3. أعد رفع **نفس الملف** لنفس المرفق → رسالة رفض واضحة (Idempotency).
4. جرّب ملفًا لمرفق آخر أو بتلميح (أسنان/بصريات/علاج طبيعي).
5. تأكد أن الشاشة لا تعرض أي اعتماد أو نشر — مطابق للشرط #3.

## Next (after your approval) — MC-2: Review Workspace
Critical queue only (غير معروفة/منخفضة الثقة/مكررة/محظورات) + per-line decisions + **"اعتماد المتبقي"** + learning-loop writes (aliases + classification history). Then STOP.

---

*STOP. Awaiting your manual test and approval of MC-1.*
