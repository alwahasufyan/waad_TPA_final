# Medical Classification Engine · MC-4B — Contract-as-Consumer — Completion Report

**Increment:** MC-4B (design review v1.1 §4/§10 — D3, D4)
**Status:** ✅ Implemented & verified · ⏸ **STOP — awaiting your manual test & approval before MC-4C (Exception Workflow).**

---

## The principle delivered

**العقد يستهلك الأسعار المنشورة — لا يحرر الطب.** Every in-place price/service/category mutation path on the contract screen is gone; the only ways prices change are the governed import cycle and (MC-4C, next) the exception workflow.

## What changed

### شاشة العقد — تبويب «قائمة الأسعار» الجديد (D3/D4)
Replaced the entire two-tab CRUD block (~700 lines: add/edit/delete dialogs, clear-all, inline Excel import, editable table) with one read-only consumer component `ContractPriceListTab`:

| Element | Delivered |
|---|---|
| **بطاقة القائمة السارية** | v-number, service count, total value, publish date + publisher, «عرض التقرير» link — verified live: contract #1 shows **v3 · 224 خدمة · 17,298.50 · superadmin** |
| **بحث الأسعار (قراءة فقط)** | Search by name/code over the active rows — the officer's daily "كم سعر X؟" |
| **سجل النسخ المختصر (D4)** | v/date/status only (v3 سارية، v2 سابقة، v1 سابقة); row click → the Version Dashboard where all detail lives |
| **مؤشر «قائمة جديدة بانتظار النشر»** | Chip linking to the draft's report when one exists |
| **Primary action 1 (D3): «استيراد قائمة أسعار»** | Navigates to the hub with the provider preselected and the upload dialog auto-opened (`?providerId=…&open=1`) |
| **Primary action 2 (D3): «تعديل استثنائي»** | Present, disabled with an honest tooltip «يتوفر في التحديث القادم» — becomes live in MC-4C |
| **زر «؟»** | 6-bullet help dialog in the workflow language |

### Removed / decommissioned (design §4 legacy-action table + §10)
- **إضافة خدمة / تعديل السعر / حذف / مسح الخدمات / استيراد Excel المباشر** — all dialogs, mutations, queries, handlers and their per-row action buttons deleted from `ProviderContractView` (file shrank ~1,690 → ~600 lines; imports pruned; lint clean). The ungoverned overwrite path from the contract screen no longer exists in the UI. *(Backend Excel endpoints remain untouched until the M3 gate, per A9 discipline.)*
- **Edit-category-per-contract:** gone entirely — category is catalog truth (design decision: not even an exception).
- **`ProvidersDebugTest.jsx`** — deleted (developer artifact).
- **«تجهيز قوائم أسعار المرافق» (التجريبية)** — hidden from the menu with a dated comment; the route stays until M3, then deleted.

### Backend (one read-only endpoint)
`GET /api/v1/classification/versions/contract/{contractId}/summary` — active-version card (count + total value computed from the live rows) + draft indicator + brief history. No write surface added; contract lifecycle (activate/suspend/terminate) untouched.

## Verification

| Check | Result |
|---|---|
| Backend compile + restart | ✅ |
| Summary endpoint (contract #1) | ✅ v3 / 224 / 17,298.50 / superadmin; history v3→v2→v1 |
| Frontend production build + ESLint (contract view now warning-free) | ✅ |
| Vite transforms: ProviderContractView + ContractPriceListTab | ✅ 200/200 |
| No remaining CRUD affordance on the contract screen (code audit) | ✅ add/edit/delete/clear/excel dialogs and handlers absent |
| Old prep screen absent from menu | ✅ (route preserved for M3) |

**Honest limits:** the visual click-through of the contract tab (card, search, history links, prefit upload) is your manual test — the API and transforms behind every element are verified. «تعديل استثنائي» is deliberately a disabled placeholder until MC-4C. `InfoRow «عدد البنود»` in the contract header now shows the backend's `pricingItemsCount` only (was padded by a client-side list that no longer exists).

## How to manually verify (your test)
**http://localhost:3001** → عقود مقدمي الخدمة → افتح العقد #1:
1. تبويب «قائمة الأسعار»: بطاقة **v3 — 224 خدمة — 17,298.50** مع اسم الناشر والتاريخ، وسجل النسخ المختصر (اضغط v2 → يفتح تقريرها).
2. ابحث عن خدمة بالاسم أو الكود → السعر الساري يظهر للقراءة فقط — **لا أزرار تعديل/حذف في أي صف**.
3. زر «استيراد قائمة أسعار» → ينقلك لشاشة القوائم وحوار الرفع مفتوح والمرفق محدد مسبقًا.
4. زر «تعديل استثنائي» ظاهر معطّل مع تلميح صادق.
5. القائمة الجانبية: «تجهيز قوائم أسعار المرافق» التجريبية اختفت.

## Next (after approval) — MC-4C: Exception Workflow
«تعديل استثنائي» live: internal Patch Versions (D2 — invisible mechanics), تصحيح سعر / إضافة خدمة / إيقاف خدمة عبر نفس البوابة المالية ونفس مرحلتي الاعتماد والنشر، ورollback من لوحة النسخة.

---

*STOP. Awaiting your manual test and approval of MC-4B.*
