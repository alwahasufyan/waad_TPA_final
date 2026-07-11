# Medical Classification Workflow — Design Review & Consolidation Plan

**Version:** 1.3 — ✅ **FINAL APPROVAL (2026-07-10).** v1.1 = D1–D5; §6-B = E1–E5; §0-C = the owner's final review decisions F1–F7. Implementation proceeds per §14 with STOP gates.

## 0-C. Final Review Decisions (binding, v1.3)

| # | Decision |
|---|---|
| F1 | **New-version is NEVER forced by line count.** E5's "bulk > ~10 lines" rule is **rescinded**. Mandatory new-version cases are by *nature of change*: (a) رفع قائمة أسعار جديدة، (b) ملحق عقد جديد، (c) إعادة تسعير شاملة يعلنها المستخدم، (d) تغيير هوية الخدمة (ربط بخدمة كتالوج مختلفة). Multiple simple price fixes / stopping / adding *existing* services may stay in-place if the user chooses and policy allows. |
| F2 | **Adding a service in the exception flow:** catalog-existing services → added directly. Unknown/new services → never added directly; they must pass the classification + review cycle first (the exception dialog's service picker searches the catalog only). |
| F3 | **UI minimalism gate:** any new feature must either reduce steps or save user time — otherwise it is not added. No new buttons/options/pages except by necessity. |
| F4 | **Help «؟» button mandatory on every finished screen** — 5–7 practical Arabic bullets, no PDFs/manuals. |
| F5 | **The contract remains a consumption screen only** — price management lives entirely in the review cycle. |
| F6 | **Exception policy = `ASK`** (user chooses edit-current vs new-version); the system forces new-version only for real governance reasons (F1's nature-based list + blocker-grade repricing per approved thresholds), never for line counts. |
| F7 | **De-emphasize "Version" for normal users:** the contract card leads with القائمة الحالية، تاريخ النشر، عدد الخدمات، القيمة الإجمالية — the v-number is a small detail, prominent only in the auditor-facing Version Dashboard. |
**Type:** Design document — implementation follows the approved roadmap (§14), increment by increment with STOP gates.
**Scope:** Full workflow audit of MC-0…MC-3 before any new feature. Governing principle (mandatory): **the Libyan market is Excel-literate, not insurance-tech-literate — complexity lives in the engine, never in the UI.**
**Framework:** ATEF.

## 0. Owner Amendments (binding, v1.1)

| # | Decision | Effect |
|---|---|---|
| D1 | **Approve and Publish remain TWO separate stages** — simplified UI, but two distinct audited acts. (Design note: this also future-proofs dual-control — the approver and publisher can become different people later with zero rework.) | §1 rows 8–9, §3, §11 updated: each stage = one clear button on the report («اعتماد التقرير» ثم «نشر») — no merge |
| D2 | **"Patch Version" is an internal concept only** — the user sees «تعديل استثنائي» and nothing else; version mechanics (clone, vN+1, source=PATCH) stay entirely behind the curtain | §6 updated — UI language contains no version vocabulary |
| D3 | **Contract screen = consumption screen with exactly TWO primary actions:** «استيراد قائمة أسعار» (launches the upload flow pre-filled with this provider/contract) and «تعديل استثنائي». Everything else read-only; rollback moves off the contract into the Version Dashboard | §4 updated |
| D4 | **Version history inside the contract is brief** (v, date, status only); all detail — diff summaries, findings, provenance, rollback — lives in the Version Dashboard | §4/§5 updated |
| D5 | **Roadmap order:** MC-4A → MC-4B → MC-4C → **MC-6 (knowledge migration + category mapping)** → MC-5 (dashboard) → MC-7 (M3 gate + legacy removal). Rationale endorsed: dashboard numbers are only honest *after* the dictionary is migrated | §14 updated |

---

## 1. Current Workflow Review — every step challenged

| # | Current step | Who clicks | Real decision made by a human? | Verdict |
|---|---|---|---|---|
| 1 | Upload price list (provider + hint + file) | Officer | Yes — which file, which provider | ✅ Keep (1 screen, 1 dialog) |
| 2 | Automatic classification (engine, async) | Nobody | No | ✅ Keep — invisible, as it should be |
| 3 | Review critical queue (unknown/low-conf/duplicate/guard) | Medical reviewer | Yes — the core human judgment | ✅ Keep — this IS the workspace |
| 4 | "اعتماد المتبقي" (Approve Remaining) | Reviewer | Yes (explicit audited bulk act, A5) | 🔶 Keep the act, **merge the click** into "إنهاء المراجعة" (§3) |
| 5 | **"إنشاء نسخة أسعار" (Create Version)** | Reviewer | **No — zero decision content.** The system knows the import, the contract, and that review is complete | ❌ **Remove as a user step** — auto-create the draft when review completes |
| 6 | Financial validation | Nobody (auto) | No | ✅ Keep — already automatic; only its *findings* need a human |
| 7 | Handle findings (fix/resolve/waive) | Reviewer/financial | Yes — real financial judgment | ✅ Keep (on the report, where it already lives) |
| 8 | **Approve Version** | Accountant | Yes — signature on the report | ✅ Keep as its own stage (**D1**) — one clear button «اعتماد التقرير»; future-proofs dual-control (approver may differ from publisher later) |
| 9 | **Publish Version** | Accountant | Yes — the financial act | ✅ Keep as its own stage (**D1**) — «نشر» enabled only after approval; simplified UI, two distinct audited acts |
| 10 | Contract consumes version | Nobody | No | ✅ Keep — automatic |

**Human decisions in the whole flow: four.** (1) upload, (2) review the critical minority, (3) approve the report, (4) publish (D1). Everything else is engine work and must disappear from the user's path.

## 2. Problems Found

1. **P1 — Two "create" ceremonies with no decision content:** "إنشاء نسخة" and the approve/publish split add 3 clicks and 2 concepts (Draft, Approval) that an Excel user does not have.
2. **P2 — The Provider Contract screen still allows raw price CRUD** (add/delete/edit price/category inline, ~1,674-line screen). This is the *old* world: it bypasses classification, bypasses the financial gate, bypasses versioning — and keeps risk R2 (in-place price mutation under `ClaimLine.pricingItemId`) alive through a side door. It directly contradicts "the contract is a consumer".
3. **P3 — Two competing entry points:** the new module and the old experimental "تجهيز قوائم أسعار المرافق" screen (`/settings/facility-price-preparation`, marked تجريبي). Two doors to the same job = confusion.
4. **P4 — Developer vocabulary leaks into the UI:** "كيان مالي", "PENDING_BULK", statuses like `REVIEW_COMPLETE`, "staging", engine version chips. Users need at most four statuses.
5. **P5 — "Link to existing service" in the decision dialog is an expert tool** shown to everyone; the engine already does find-or-create. For a normal reviewer it is noise and a wrong-click risk.
6. **P6 — Screen fragmentation:** imports list, review page, version dashboard are three URLs with no single home; versions are reachable only by remembering an id.
7. **P7 — No exception path:** the one *legitimate* reason users edited contracts (add one missing service / fix one price) has no home in the new model yet — which will push users back to P2's CRUD if we don't design it.
8. **P8 — Rollback undefined:** if a published version proves wrong, there is no safe, designed way back.

## 3. Recommended Workflow (target)

**What the user sees (3 decisions, 4 statuses):**

```
① ارفع الملف            → الحالة: «قيد المعالجة» (دقائق)
② راجِع الحالات الحرجة    → الحالة: «بحاجة مراجعة (N)»
   … عند إفراغ الطابور: زر واحد
   «إنهاء المراجعة واعتماد الموثوق (N)»
      = Approve Remaining + create draft + financial validation + فتح التقرير — تلقائيًا
③ تقرير النسخة           → الحالة: «بانتظار النشر»
   عالج الموانع / أعفِ التحذيرات (إن وجدت) ثم مرحلتان واضحتان (D1):
   «اعتماد التقرير»  ثم  «نشر»  → الحالة: «منشورة» — العقد يستهلكها فورًا
```

- **Merged:** Approve Remaining + Create Version + Validate = one button at the natural end of review (the audited `BULK_REMAINING` act is preserved verbatim — one click now *does* it instead of preceding it). **Approve and Publish stay separate (D1)** — each is one clear button on the same report; «نشر» activates only after «اعتماد التقرير».
- **User-visible statuses (rename map):** قيد المعالجة (UPLOADED/PROCESSING) → بحاجة مراجعة (CLASSIFIED/IN_REVIEW) → بانتظار النشر (REVIEW_COMPLETE + DRAFT version) → منشورة (PUBLISHED/ACTIVE); plus فشل. Internal states remain in the DB for audit — they just stop being UI vocabulary.
- **Nothing changes in the engine, the gate, the audit, or A1–A11.** This is pure step-consolidation; every control point survives, three clicks disappear.

## 4. Provider Contract Redesign

**Principle: the contract is a CONSUMER of published versions. It displays money; it does not edit medicine.**

The "قائمة الأسعار" tab becomes a **consumption screen with exactly TWO primary actions (D3)** — everything else read-only:

| Element | Content |
|---|---|
| **بطاقة النسخة النافذة** | v-number, publish date, published-by, service count, total value, "منذ متى" — one glance answers "ما الذي نعمل به الآن؟" |
| **بحث في الأسعار** | Read-only searchable table of the active version's rows (the officer's daily need: "كم سعر خدمة X؟") |
| **سجل النسخ (مختصر — D4)** | v3 → v2 → v1: version, date, status only. Row click → Version Dashboard (all detail lives there) |
| **مؤشر «نسخة قيد الإعداد»** | If a draft exists: who is working on it + link |
| **زر «استيراد قائمة أسعار» (primary action 1, D3)** | Launches the upload flow pre-filled with this provider/contract |
| **زر «تعديل استثنائي» (primary action 2, D3)** | Opens the Exception Workflow (§6) |
| ~~زر استرجاع نسخة سابقة~~ | **Moved off the contract (D3)** — rollback lives in the Version Dashboard only (publisher role): creates a *new* draft cloned from the chosen historical version, through the normal gate. Rollback = roll *forward* a copy; immutability never violated |

**Editing on the contract: removed entirely.** Decision per legacy action:

| Legacy contract action | Decision | Where it goes | Why |
|---|---|---|---|
| Add Service (inline) | **Replace** | Exception Workflow (§6) for 1–10 services; import cycle for more | Unclassified, ungated additions are how bad data reached claims |
| Delete Service (inline) | **Replace** | Exception Workflow ("إيقاف خدمة") → new version without the row; old rows never deleted | Hard delete breaks claim history (R2) |
| Edit Price (inline) | **Replace** | Exception Workflow (single-price patch) | In-place repricing under `pricingItemId` is the exact financial-integrity bug versioning exists to kill |
| Edit Category (inline) | **Remove** (not even an exception) | Category is *catalog* truth — corrected in the Medical Catalog / review workspace, never per-contract | One service must not have different medical meanings per contract |
| Bulk Excel import into contract (old `ProviderContractPricingExcelService` path) | **Retire** (after M3 gate passes, per A9 discipline) | The classification module IS this feature, governed | Ungoverned overwrite path |
| View/search prices | **Keep** | Contract tab (read-only) | The daily legitimate use |

**Permissions:** contract officers = read + create exception *requests*; medical reviewer = classify exception lines; publisher (SUPER_ADMIN/ACCOUNTANT) = publish patches & rollbacks. Nobody edits published rows — the capability ceases to exist in the UI.

## 5. Version Management Design — what lives where

| Information | Contract tab | Version Dashboard |
|---|---|---|
| Active version card (v, date, who, counts, value) | ✅ | ✅ (header) |
| Version list — **brief: v, date, status only (D4)** | ✅ | — |
| Diff summaries + full A11 comparison (distribution, top-20, reclassified) | — | ✅ |
| A10 findings + waiver audit | — | ✅ |
| Approve then Publish — **two stages (D1)** | — | ✅ (report is the artifact) |
| Read-only price search | ✅ | — |
| Rollback initiation | — (**D3**: off the contract) | ✅ (publisher role; executes as new draft) |
| Import provenance (file hash, engine, dictionary) | — | ✅ (collapsed "تفاصيل تقنية" — hidden by default, P4) |

Rule of thumb: **the contract answers "what is in force and what changed"; the dashboard answers "why, exactly, and who signed".** Version identity stays per-contract (v1, v2, v3…) with source tag: `IMPORT` / `PATCH` / `ROLLBACK`.

## 6. Exception Workflow Design — the "Patch Version"

Evaluated models for the "hospital asks to add one service / fix one price" case:

| Model | Verdict | Why |
|---|---|---|
| Direct edit of active version | ❌ | Destroys immutability — the entire point of MC-3 |
| Override/exception table beside the version | ❌ | Two sources of truth for one price; every consumer (portal, claims, reports) must merge two tables forever; audit splits |
| Full re-import for one change | ❌ | Punishes the user; violates the simplicity principle |
| **Patch Version (mini version)** | ✅ **Recommended** | One mechanism for everything: a new DRAFT **cloned from the ACTIVE version ± the requested lines**, flowing through the *same* classification (for added services), the *same* financial gate (scoped to the changed lines), the *same* one-button publish → becomes vN+1. Claims history intact; audit uniform; zero new invariants |

**User experience (contract tab → «تعديل استثنائي») — D2: the words "Patch"/"Version"/"نسخة" never appear to the user:**
1. Small dialog: نوع التعديل (تصحيح سعر / إضافة خدمة / إيقاف خدمة) + الخدمة + القيمة + السبب (mandatory — it is the waiver-grade audit note).
2. System — silently — clones the active version, applies the change, runs the gate → the user sees only **"طلب تعديل: CBC ‏18 → 22 (+22%) — بانتظار النشر"**.
3. Publisher presses «اعتماد التقرير» ثم «نشر» (D1). Done — under a minute, fully governed.

Guardrails: max ~10 lines per patch (beyond that: "استورد ملفًا"); added services pass through the classification engine + reviewer confirmation (a 1-line critical queue); internally the result is a version tagged `PATCH` in history — visible as such only to auditors in the Version Dashboard, never in the exception dialog (D2). *(Implementation note for later: needs "version without source import" support — small, deferred until MC-4C.)*

## 6-B. MC-4C Redesign (owner decisions, v1.2 — 2026-07-10) — DESIGN ONLY, pre-implementation

### The decisive technical verification (owner's one reservation — resolved)

**Question:** do claims depend on the *current* price, or on the price stored at claim time?
**Finding (verified in code):** `ClaimLine` is a **full snapshot entity** — it persists `unitPrice`, `requestedUnitPrice`, `approvedUnitPrice`, `totalPrice`, `approvedAmount`, `companyShare`, `patientShare`, `refusedAmount`, `priceExcessRefused`, plus benefit-limit/usage snapshots, all documented in-code as "denormalized snapshot at claim time … for historical accuracy". The pricing row is read **once, inside `ClaimMapper` at claim-processing time**; every report (verified: `ReportDataService`) reads the claim's own stored values, never the pricing table.
**Conclusion:** editing a price on the current version **cannot change any historical claim's results**. The owner's proposal is technically sound. `ClaimLine.pricingItemId` remains only an audit *pointer*; a full change-audit on the row (below) keeps that pointer's story reconstructible.

### E1 — Review-stage editing (data-cleaning phase)
During review (pre-publish) the reviewer may freely: **edit price** (surface the existing audited `fixLinePrice` in the decision dialog), **change category** (exists), **link service** (exists, under خيارات متقدمة), **remove a line** (reject — exists), and **add a missing service** (new: manual line added to the import, passes the same decision flow). All pre-publish — zero integrity impact, full per-line audit.

### E2 — «تعديل استثنائي» offers TWO paths (one small dialog, no new screens)
```
تعديل استثنائي → [ تعديل النسخة الحالية ]  أو  [ إنشاء نسخة أسعار جديدة ]
```
- **تعديل النسخة الحالية:** no new version. The change is applied directly to the active row(s) with a **mandatory full audit record** per change: old value, new value, user, timestamp, reason (required). New table `price_change_audit` (pricing_item_id, contract_id, version_id, old/new price, changed_by, reason, at). The version header thereafter shows «معدّلة استثنائيًا (N تعديلات)» and the Version Dashboard lists the audit trail. Guard: an inline scoped A10 check runs on the new value; soft-deactivation only for "إيقاف خدمة" (rows never deleted); «إضافة خدمة» inserts a new active row tagged to the current version (service passes classification/reviewer confirmation).
- **إنشاء نسخة جديدة:** the existing clone→gate→approve→publish cycle (the old Patch design, still internal-only per D2).

### E3 — Configurable policy (`classification_settings`: `exception.edit_mode`)
| Value | Behavior |
|---|---|
| `EDIT_CURRENT` | Apply in place silently (audit always) |
| `NEW_VERSION` | Always route through a new version |
| `ASK` (**default**) | Show the two-option dialog each time |

### E4 — UX stays flat
One dialog, two buttons, then the same tiny form (نوع التعديل + الخدمة + القيمة + السبب). No versions vocabulary (D2), no extra screens.

### E5 — Mandatory new-version cases (FINAL per F1/F6 — nature-based, never line-count)
Because claims snapshot their figures, in-place editing is safe for history in all cases. The new-version path is **forced** only for governance reasons rooted in the *nature* of the change:
1. **رفع قائمة أسعار جديدة** (an import is always a new version — existing behavior).
2. **ملحق عقد جديد** (contract addendum).
3. **إعادة تسعير شاملة يعلنها المستخدم** (the user declares it a repricing → comparison report as the approval artifact, A11).
4. **تغيير هوية الخدمة** (re-linking a row to a different catalog service — identity changes create a new row in a new version).
5. **Blocker-grade single change (>±100% per approved thresholds)** — must pass the full A10 gate + two signatures.
~~Bulk > 10 lines~~ — **rescinded (F1)**: line count is not a criterion. Multiple simple fixes may stay in place if the user chooses. Default policy `ASK` (F6).

*(§6's original Patch-Version design remains valid as the «إنشاء نسخة جديدة» path.)*

## 7. Benefit Engine Integration Review

**Current architecture:** `CATEGORY_RULE > POLICY_DEFAULT` resolution; service-level rules only as overrides; coverage attached to main/sub category; the engine consumes `contractPrice` + category id. **Verdict: keep exactly this — it is the correct shape for this market.** Small policies (dozens of category rules) are explainable to a non-technical client; thousands of service rules are not maintainable and not auditable.

The classification module has quietly *strengthened* it: every module-published pricing row now carries a guaranteed `medical_category_id` (152/152 in the live test), so category resolution stops depending on hand-typed claim-form categories — better coverage decisions with **zero engine change** (A3 stands).

Design-only improvements (no structural change):
1. **Override-count governance metric:** each policy shows "قواعد على مستوى الخدمة: N" — target ≈ 0; a rising N is a smell that categories are wrong, not that more overrides are needed. (Belongs later in the A8 dashboard.)
2. **Category quality is now the single hinge** between pricing and benefits — the MC-6 mapping sheet (script CAT codes ↔ WAAD categories, name-verified) graduates from "nice cleanup" to **prerequisite for scale**; recommend scheduling it immediately after the consolidation work.
3. No new rule types, no per-service pricing rules in policies — explicitly rejected to keep policies small.

## 8. Provider Price Version Lifecycle (final, canonical)

```
(source: IMPORT | PATCH | ROLLBACK)
        │
   [hidden: DRAFT]  ── discard → مؤرشفة (ARCHIVED)
        │  auto-created at «إنهاء المراجعة» / exception request
        │  A10 gate: موانع تُعالَج (لا تُعفى) · تحذيرات تُحل أو تُعفى بسبب
        ▼
  «بانتظار النشر»  ── «اعتماد التقرير» ثم «نشر» (D1 — مرحلتان، دور مالي، مسجّلتان)
        ▼
   «منشورة» (ACTIVE) ── immutable · one per contract · rows inserted, never updated
        ▼  (next version publishes)
  «مستبدلة» (SUPERSEDED) ── rows deactivated, NEVER deleted — permanent claims reference
```

Invariants (already enforced, restated as canon): one ACTIVE per contract; published prices immutable; `ClaimLine.pricingItemId` always resolves to the exact historical row; every state change carries who/when; every version reproducible from its source (import hash + decisions, or patch request).

## 9. UX Simplification Recommendations

1. **Help-button standard — adopt system-wide (ATEF UI Constitution addendum):** every screen gets a small «؟» opening a dialog of **≤ 7 Arabic bullets**, action-phrased ("ارفع ملف الأسعار كما استلمته — لا تنسّقه"), no PDF/manual/video. Content lives beside the page code and is reviewed with it. Start with the 4 classification screens as the pilot.
2. **One menu entry, one home:** «قوائم أسعار المرافق» — a single hub listing imports/versions as *one* timeline per provider ("قائمة مختبر الحياة — منشورة v2 · 10-07"). Review and report open from rows. Kills P6.
3. **Terminology purge (P4):** المستخدم يرى فقط: قائمة أسعار، بحاجة مراجعة، موثوقة، بانتظار النشر، منشورة، تعديل استثنائي. يختفي من الواجهة: staging، PENDING_BULK، DRAFT، كيان مالي، engine version (يبقى في "تفاصيل تقنية" مطوية للمدقق).
4. **Progressive disclosure in the decision dialog (P5):** default = suggestion + category + اعتماد/رفض. "ربط بخدمة موجودة" + الملاحظة تحت «خيارات متقدمة» مطوية.
5. **Empty-state guidance:** أول زيارة للشاشة تعرض الخطوات الثلاث كبطاقات ("١ ارفع → ٢ راجِع → ٣ انشر") بدل جدول فارغ.
6. **Counts speak, chips don't:** «بحاجة مراجعة (12)» على الصف مباشرة — لا يحتاج المستخدم فتح الشاشة ليعرف أن عليه عملًا.

## 10. Screens To Remove

| Screen | Action | Timing |
|---|---|---|
| «تجهيز قوائم أسعار المرافق» (`/settings/facility-price-preparation`, تجريبي) | **Hide from menu now; delete after the M3 regression gate passes.** It predates the module, duplicates the door, and is already labelled experimental. Not converted to an internal tool — the *folder script* (A9) is the fallback, not this screen | Hide: with consolidation · Delete: post-M3 |
| Contract inline pricing CRUD (add/delete/edit dialogs inside `ProviderContractView`) | **Remove** — replaced by §4/§6 | With consolidation |
| `ProvidersDebugTest.jsx` | Developer artifact — remove from routes | With consolidation |
| Old contract bulk-Excel import path (backend + its UI trigger) | **Retire after M3 gate** (A9 discipline: fallback stays until parity proven) | Post-M3 |

## 11. Screens To Merge

| Merge | Into | Effect |
|---|---|---|
| Imports list + versions list | One «قوائم أسعار المرافق» hub | One door, one timeline |
| «اعتماد المتبقي» + «إنشاء نسخة» + validation trigger | One button «إنهاء المراجعة واعتماد الموثوق (N)» | −2 steps, audit act preserved |
| «اعتماد» + «نشر» | **NOT merged (D1)** — two stages, each one clear button; «نشر» activates after approval | Dual-control preserved & future-proofed |
| Contract "قائمة الأسعار" tab + version summary | One consumer tab (§4, D3/D4) | Contract answers the daily question |

## 12. Screens To Keep

- **Upload dialog** (provider + hint + file) — already minimal.
- **Review Workspace** — the crown jewel; keep tabs/queues exactly, apply §9.3–9.4 polish only.
- **Version Dashboard (A11 report)** — the approval artifact; keep, with provenance collapsed.
- **Engine health probe** — keep, admin-only, out of normal users' sight.

## 13. ATEF Compliance Review

| Principle / decision | Status after this design |
|---|---|
| A1 CLI transport · A2 no new catalog · A3 Benefit Engine untouched | ✅ Unaffected — consolidation is workflow-level only |
| A4 no auto-approval | ✅ Preserved: the merged button still *is* the explicit human act; nothing approves without a person |
| A5 hidden majority + explicit bulk act | ✅ Preserved verbatim inside «إنهاء المراجعة» (same `BULK_REMAINING` audit) |
| A6 services created only on approval | ✅ Unchanged; exception-added services also pass reviewer confirmation |
| A9 script untouched & authoritative until M3 | ✅ Reaffirmed — old screen/import path retire only *after* the gate |
| A10 financial gate · A11 report-as-artifact | ✅ Strengthened: the report becomes the *only* road to publish; contract CRUD side-door closes |
| Financial integrity / reproducibility (owner's financial-artifact decree) | ✅ Improved: removing contract CRUD closes the last in-place-mutation path (R2 fully retired); rollback designed as roll-forward |
| Sub-stage lifecycle & STOP discipline | ✅ This document itself is the analyze step; nothing proceeds without approval |

## 14. Final Recommendation

**Consolidate before extending.** The engine, gate, and audit built in MC-0…MC-3 are sound; the *seams between steps* are where complexity leaks onto users. Approve the following, in order:

1. **MC-4A — Workflow Consolidation:** «إنهاء المراجعة واعتماد الموثوق» (merges Approve-Remaining + auto-draft + validation); Version Dashboard keeps **two stages** «اعتماد التقرير» ثم «نشر» (D1); one hub screen; terminology purge; status rename map; progressive disclosure in the decision dialog; help-dialog pilot on the classification screens.
2. **MC-4B — Contract-as-Consumer:** rebuild the contract's قائمة الأسعار tab per §4 (D3: two primary actions only — استيراد + تعديل استثنائي; D4: brief history); remove inline CRUD + debug screen; hide the old preparation screen.
3. **MC-4C — Exception Workflow:** internal Patch Versions (§6, D2 — invisible mechanics) incl. rollback-as-roll-forward from the Version Dashboard.
4. **MC-6 — Knowledge migration + category mapping sheet** — **before the dashboard (D5):** stats are only honest after the dictionary is migrated.
5. **MC-5 — Classification Dashboard (A8)** — incl. the benefit-override governance metric (§7.1).
6. **MC-7 — M3 regression gate**, then retire the old prep screen and legacy import path.

The result for the user: **رفع → مراجعة → اعتماد → نشر.** Four clear decisions, four statuses, one home screen, one exception button — and every control ATEF demands still standing behind the curtain.

---

*DESIGN v1.1 — APPROVED with amendments D1–D5. Implementation proceeds per §14 order with STOP gates.*
