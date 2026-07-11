# Medical Classification Engine · MC-4A — Workflow Consolidation — Completion Report

**Increment:** MC-4A (design review v1.1 §14.1 — approved with amendments D1–D5)
**Status:** ✅ Implemented & verified end-to-end · ⏸ **STOP — awaiting your manual test & approval before MC-4B (Contract-as-Consumer).**

---

## What changed for the user (the whole point)

| Before (MC-3) | After (MC-4A) |
|---|---|
| 8 statuses بلغة المطورين | **4 حالات**: قيد المعالجة → بحاجة مراجعة (N) → بانتظار النشر → منشورة (+فشل) |
| «اعتماد المتبقي» ثم «إنشاء نسخة أسعار (كيان مالي)» ثم فتح التقرير يدويًا | زر واحد: **«إنهاء المراجعة واعتماد الموثوق (N)»** — يعتمد الموثوق (A5 كما هو)، ينشئ النسخة، يشغّل الفحص المالي، ويفتح التقرير |
| «اعتماد» + «نشر» بصياغة تقنية | **مرحلتان واضحتان (D1)**: «اعتماد التقرير» (توقيع المراجعة) ثم «نشر» — الثاني معطّل حتى يتم الأول، مع شارة «اعتُمد بواسطة …» |
| «كيان مالي»، BULK_REMAINING، staging في الواجهة | **إزالة كاملة للمصطلحات التقنية** — لغة عربية عملية فقط؛ التفاصيل التقنية (hash/محرك) في عمود مطوي «تفاصيل تقنية» |
| ثلاث شاشات بلا رابط | **مدخل واحد** «قوائم أسعار المرافق»: زر «مراجعة» للحالات المفتوحة، زر «التقرير والنشر» عندما تنتظر النشر، «التقرير» بعد النشر |
| لا مساعدة | **زر «؟» بمعيار ≤7 نقاط عربية عملية** على الشاشات الثلاث (مكوّن `HelpDialog` قابل لإعادة الاستخدام على مستوى النظام) + بطاقات الخطوات الثلاث عند الجدول الفارغ |
| أدوات الخبراء ظاهرة للجميع | حوار القرار: «ربط بخدمة موجودة» والملاحظة **مطويان تحت «خيارات متقدمة»** (§9.4) |

**Nothing changed in the engine, gate, audit, RBAC, or A1–A11** — pure seam-removal. `isAutoApprovalEnabled()` still hard-false; the A5 bulk act records the same `BULK_REMAINING` audit; approve/publish keep separate audit fields and role restrictions (SUPER_ADMIN/ACCOUNTANT).

## What was built

**Backend:**
- `ReviewService.finishReview()` + `POST …/review/finish`: validates the critical queue is empty → Approve Remaining (A5, verbatim) → auto-creates the DRAFT version (contract from the import, else the provider's ACTIVE contract) → A10 validation → returns `versionId` for navigation. No contract → review still completes with a clear Arabic message.
- `VersionComparisonDto` + `approvedBy/approvedAt` (D1 — UI gates «نشر» on it).
- `PriceListImportDto` + `versionId/versionStatus` (hub links each import straight to its report); repo `findFirstBySourceImportIdOrderByIdDesc`.

**Frontend:**
- `components/common/HelpDialog` — the system-wide help standard (hard-capped at 7 bullets).
- Hub (`/classification/imports`): status rename map with needs-review count on the chip, «التقرير والنشر» action, empty-state step cards, help, menu renamed to «قوائم أسعار المرافق».
- Review workspace: single closing action (button + confirmation dialog both run finishReview and navigate to the report), advanced options collapsed, terminology cleaned, help.
- Version report: «اعتماد التقرير» → chip «اعتُمد بواسطة …» → «نشر» (disabled until approved & gate green), banner/publish texts rewritten in user language, help.

## End-to-end verification (real file, fresh import #6 — المختبر الطبي الأول، 282 خدمة)

| Check | Result |
|---|---|
| Learning loop still compounding | ✅ 188 trusted (vs 187 in MC-2's run — one more line auto-recognized from accumulated dictionary) |
| Critical queue worked via bulk decisions | ✅ 50 zero-price rejected · 3 unresolved-category approved with correct lab category · 8 duplicates rejected · 33 low-confidence approved |
| **One-click «إنهاء المراجعة»** | ✅ single call: 188 bulk-approved (A5 audit) + v3 DRAFT auto-created + contract auto-resolved (#1) + validation ran + versionId returned |
| **D1 ordering enforced** | ✅ publish before approve → rejected ("النسخة غير معتمدة — الاعتماد يتم على تقرير المقارنة أولًا") |
| A10 gate on real data | ✅ 8 spike BLOCKERs (>100% vs v2 — genuine cross-file price differences) fixed via the audited price-fix path; 6 WARNINGs waived with reason |
| Approve → Publish (two audited stages) | ✅ v3 ACTIVE (`approved_by` + `published_by` recorded separately); v2 SUPERSEDED |
| Financial-artifact invariants | ✅ rows per version: v1=151 & v2=152 kept inactive (never deleted), v3=224 active |
| Backend compile + frontend build + ESLint + Vite transforms | ✅ all green |

**Notes:** import #4 was found CANCELLED (from your manual testing) — terminal states correctly allowed re-uploading the same file (idempotency behaves as specified). The spike blockers arose because two different labs share dev-contract #1; in production each provider has its own contract, so cross-provider spikes won't occur — the gate still proved it catches them.

## How to manually verify (your test)
**http://localhost:3001** → القائمة: **قوائم أسعار المرافق**:
1. لاحظ الحالات الأربع الجديدة والعداد على «بحاجة مراجعة (N)»، وزر «؟» في رأس الشاشة.
2. ارفع ملفًا جديدًا (أو افتح استيرادًا قائمًا): شاشة المراجعة الآن تحمل زرًا واحدًا **«إنهاء المراجعة واعتماد الموثوق (N)»** — يفتح التقرير مباشرة بعد التأكيد.
3. في التقرير: **«اعتماد التقرير»** أولًا (يتحول لشارة باسمك) ثم يتفعل **«نشر»** — جرّب أن «نشر» معطّل قبل الاعتماد.
4. في حوار قرار السطر: الأساسيات فقط ظاهرة، و«خيارات متقدمة» مطوية.
5. `/classification/versions/3` — تقرير النسخة المنشورة بالصياغة الجديدة.

## Next (after approval) — MC-4B: Contract-as-Consumer
Rebuild the contract's قائمة الأسعار tab per §4/D3/D4 (بطاقة النسخة النافذة + بحث للقراءة + سجل مختصر + زرا «استيراد» و«تعديل استثنائي» فقط), remove inline pricing CRUD + debug screen, hide the old preparation screen.

---

*STOP. Awaiting your manual test and approval of MC-4A.*
