# Medical Classification Engine · MC-2 — Medical Classification Workspace — Completion Report

**Increment:** MC-2 (critical-queue review + Approve Remaining + learning loop)
**Plan:** [provider-price-list-classification-module-plan.md](../plans/provider-price-list-classification-module-plan.md) v1.2
**Owner directive honored:** this is a **Medical Classification Workspace**, not an error-fixing screen — every reviewer decision adds knowledge to the WAAD medical dictionary, and that is now **measured, visible in the UX, and proven end-to-end** (17 lines of the very next provider file were auto-recognized from the first file's decisions).
**Status:** ✅ Implemented & verified end-to-end on REAL files · ⏸ **STOP — awaiting your manual test & approval before MC-3 (versioning + Financial Validation + Version Comparison).**

---

## The learning loop — proven, not promised

| Step | Evidence |
|---|---|
| Import #2 (مختبر الحياة، 152 خدمة) reviewed: 14 individual decisions + **اعتماد المتبقي (138)** | Import → `REVIEW_COMPLETE`; approval modes audited: 14 `INDIVIDUAL` + 138 `BULK_REMAINING` |
| Every approval wrote knowledge | **134 catalog services** created (find-or-create — duplicate wording collapsed), **264 aliases** (Arabic + English wording), **152 classification-history rows** (who/what/confidence/source line) |
| Import #4 (المختبر الطبي الأول، 282 خدمة — a DIFFERENT provider file) | **17 lines auto-recognized from the WAAD dictionary** (`classification_source=KNOWLEDGE_BASE`, reason: "✔ معروف من قرارات مراجعة سابقة (قاموس وعد الطبي)") → skipped straight to the trusted majority. The queue shrinks with every provider, exactly as directed. |

## Critical safety catch (financial-integrity guard, discovered during MC-2)

The script's approved-category codes and WAAD's `medical_categories` share CAT0xx numbering **with different meanings** (script CAT003 = العناية الفائقة؛ WAAD CAT003 = الولادة القيصرية؛ إزاحة كاملة في CAT024–029). Code-based mapping would have silently misclassified services — with direct coverage impact.
**Resolution implemented:** `CategoryResolutionService` maps engine suggestions to WAAD categories **by canonicalized NAME only, never by code**; unresolved lines are flagged `CATEGORY_UNRESOLVED` and **forced into the critical queue** regardless of confidence. Live proof: 150/152 lines resolved by name; the 2 unresolved (script-CAT003 lab tests) surfaced in the محظورات tab and were fixed by an explicit reviewer decision to the correct lab category. This is the MC-6 mapping problem contained safely until then.

## What was built

**Backend:**
- `V72`: `reference_match` column + queue index + **fix of V70's `medical_services.status` CHECK** (it didn't match the Java enum `DRAFT/ACTIVE/ARCHIVED`).
- `ArabicTextCanonicalizer` — conservative canonical form for *exact* lookups only (fuzzy stays exclusively in Python, R5).
- `CategoryResolutionService` — name-only category resolution (cached index over `medical_categories`).
- `CatalogKnowledgeService` — the learning loop: find-or-create `MedicalService` **only at approval** (A6), alias writes (`ent_service_aliases`, reviewer-tagged), `catalog_classification_history` audit, knowledge index consulted by the import pipeline.
- `ImportProcessingService` enriched: dictionary lookup before banding (KNOWLEDGE_BASE hits skip review), category resolution, `CATEGORY_UNRESOLVED` guard, `reference_match` persisted.
- `ReviewService` + `PriceListReviewController` (`/api/v1/classification/imports/{id}/review/…`): summary (progress, queue breakdown, Approve-Remaining gate, knowledge counter), queue tabs (`UNKNOWN/LOW_CONFIDENCE/DUPLICATE/GUARD` — DB-side predicates), per-line + bulk decisions (APPROVE with category/service, REJECT with note), **Approve Remaining** (blocked while the critical queue > 0; defense-in-depth re-check of category resolution; fully audited), import status machine `CLASSIFIED → IN_REVIEW → REVIEW_COMPLETE`, category/service pickers.
- A4 unchanged: `isAutoApprovalEnabled()` still hard-returns `false`; every approval is human.

**Frontend (`/classification/imports/:id/review`):**
- Workspace header: progress bar, counters (متبقي/موثوقة/معتمدة/مرفوضة), **"قرارات أُضيفت للقاموس: N"** knowledge chip, and the **اعتماد المتبقي (N)** button — disabled with an explanatory tooltip until the critical queue hits zero, then a confirmation dialog stating the count and the `BULK_REMAINING` audit.
- Tabs: الطوابير الحرجة (غير معروفة/منخفضة الثقة/مكررة/محظورات بأعدادها) + تبويبات تدقيق للقراءة (موثوقة بانتظار الاعتماد/معتمدة/مرفوضة) — the reviewer never scrolls the trusted majority (A5).
- Per line: bilingual raw name, guard-flag chips, engine suggestion + **"من قاموس وعد"** badge for knowledge hits + "بدون تصنيف معتمد" warning, confidence, the script's reason string.
- Decision dialog: WAAD category autocomplete (pre-filled from the resolved suggestion), optional map-to-existing-service search, note; bulk selection + bulk decision; success toast: "أُضيفت معرفة جديدة إلى قاموس وعد الطبي ✨".
- Imports list gained the **مراجعة** action.

## Verification

| Check | Result |
|---|---|
| Backend compile + V72 applied + restart | ✅ |
| Full review E2E (import #2): bulk 12 + individual 2 + Approve Remaining 138 → REVIEW_COMPLETE, 152/0 approved/rejected | ✅ |
| Approve-Remaining gate: disabled at 14 pending → enabled at 0 → executed | ✅ |
| Knowledge writes: 134 services / 264 aliases / 152 history | ✅ (DB counts) |
| **Learning loop on next file (import #4)**: 17 KNOWLEDGE_BASE hits | ✅ |
| Guards on real data (import #4): 52 zero/negative-price lines, 8 in-file duplicates, 6 category-unresolved — all forced to review | ✅ |
| Frontend build + ESLint + Vite transform | ✅ |
| Fix during verification | **After-commit async trigger**: import #3 failed ("No value present") because the @Async job raced the upload transaction; processing now starts in `afterCommit`. Verified fixed by import #4. |

**Honest limits:** my curl-based API test needed UTF-8 request files (git-bash console encoding) — browser requests are unaffected; the review UI itself compiles/transforms and mirrors the tested API, but its click-through is your manual test. Import #4 was left in `IN_REVIEW`-ready state (95 real review cases incl. 52 zero-price lines — real data-quality findings in that provider file) so you can experience the workspace on genuine content.

## How to manually verify (your test)
**http://localhost:3001** → استيراد قوائم الأسعار → صف الاستيراد #4 (المختبر الطبي الأول) → زر **مراجعة**:
1. الترويسة: التقدم، العدادات، شارة القاموس، وزر **اعتماد المتبقي (187)** معطّل مع تلميح السبب.
2. تبويب **محظورات**: أسعار صفرية حقيقية في ملف المرفق + "بدون تصنيف معتمد" — قرر فيها (اعتماد بتصنيف صحيح أو رفض).
3. تبويب **منخفضة الثقة**: لاحظ سبب النتيجة واقتراح المحرك؛ جرّب قرارًا فرديًا وقرارًا جماعيًا للمحدد.
4. عند إفراغ الطوابير الحرجة يتفعّل **اعتماد المتبقي** → حوار تأكيد → اكتمال المراجعة.
5. لاحظ في تبويب "موثوقة" الأسطر الحاملة شارة **"من قاموس وعد"** — هذه تعلّمها النظام من مراجعتك لاستيراد مختبر الحياة.

## Next (after approval) — MC-3
Versioning + **Financial Validation Engine (A10)** + **Version Comparison Dashboard (A11)** + publish gate onto the contract.

---

*STOP. Awaiting your manual test and approval of MC-2.*
