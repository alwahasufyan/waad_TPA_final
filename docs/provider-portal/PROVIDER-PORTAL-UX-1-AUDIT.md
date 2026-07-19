# PROVIDER-PORTAL-UX-1 — Phase 0 Audit (Revision 1 — approved plan)

**Status:** Phase 0 approved by product owner with plan revisions below. No implementation committed/pushed yet.
**Date:** 2026-07-19
**Scope:** functional + UX audit of the WAAD Provider Portal, with a phased, severity-ranked backlog.

> **Revision 1 — owner feedback incorporated (2026-07-19):** phase order changed (unified messaging engine built *before* either of its two surfaces), a new **Performance** phase inserted, and two cross-cutting priorities added that apply to every remaining phase: **(a) data-entry speed** for the front-desk clerk (fewest clicks/screens, full keyboard operability) and **(b) full-viewport workspace layouts** (no long scrolling pages). See §7–§10 below and the revised backlog in §3.

> **Method note (honesty):** this audit is a **code + API-contract analysis** (routes, pages, controllers, guards, entities) cross-referenced with the supplied screenshots. A fully-authenticated **live-browser** reproduction was **not** run — there is no browser-automation/Playwright available in this environment and I won't materialize login credentials. Repro steps below are derived from the code paths + screenshots; live click-through remains a Phase-8 acceptance activity. The local stack (backend + frontend, healthy) and the backend API contracts were used to verify wiring.

---

## 0. Executive summary

The Provider Portal is **functionally wired and isolation-safe**, but has **three structural problems** that hurt daily use, plus one **critical data defect**:

1. **🔴 CRITICAL — Claim conversation is `localStorage`-only.** "محادثة المطالبة" persists in the **browser**, not the DB. Provider and reviewer have **separate** local stores, so messages **never reach the other side**. No server persistence, sender identity resolution, unread state, notifications, isolation, or audit. This is not a UX polish item — the feature does not actually work across users.
2. **🟠 HIGH — Claim creation is one 2,561-line page.** `ProviderClaimsSubmission.jsx` renders everything (context, services, diagnosis, attachments, conversation) in a single long vertical scroll with a **decorative** 3-step stepper (`workflowActiveStep` is derived from state, not real navigation).
3. **🟠 MEDIUM — Sticky bottom action bar can overlap content/messages** (`position: sticky; zIndex: 10`) with no reserved layout space.
4. **🟡 MEDIUM — Generic errors.** Failures surface as **«حدث خطأ غير متوقع»** from the global toaster/`ErrorFallback` instead of specific, recoverable Arabic messages.

**Strong points (do not regress):** `ProviderContextGuard` enforces robust isolation (providerId from the security context only; request-supplied ids rejected → 403); reports are strictly scoped (`getRequiredProviderIdStrict`); the eligibility page already has name-search + camera-fix + employer-support messaging (prior work); provider report exports now carry an immutable facility signature.

---

## 1. Inventory

### Provider routes (`MainRoutes.jsx`)
| Route | Component | Notes |
|---|---|---|
| `/provider/eligibility-check` | `ProviderEligibilityCheck` | recently improved (name search, camera, employer-support) |
| `/provider/visits` | `ProviderVisitLog` | scoped via `getProviderFilter()` |
| `/provider/claims/submit` | `ProviderClaimsSubmission` | **2,561 lines** — the core problem |
| `/provider/pre-approvals/submit` | `ProviderPreApprovalSubmission` | |
| `/provider/documents` | `ProviderDocuments` | `/api/v1/provider/documents` (+ stats) |
| `/provider/pre-auth-inbox` | `PreAuthInbox` | |
| `/provider/reports/{claims,pre-auth,visits}` | provider reports | scoped + facility signature |

### Backend controllers
`ProviderPortalController` (eligibility, visits, my-services, my-contract, pricing), `ProviderReportsController` (scoped reports + exports), `ProviderDocumentController` (`/api/v1/provider/documents`), `ClaimController` (`/api/v1/claims`, incl. `/{id}/return-for-info`), `ClaimAttachmentController` (`/claims/{id}/attachments`).

### Isolation
`security/ProviderContextGuard`: `getRequiredProviderId`, `getRequiredProviderIdStrict`, `getProviderFilter`, `validateProviderAccess` (throws on cross-facility), `enforceProviderId` (forces own id for PROVIDER users). **Verified robust.**

---

## 2. Defect log (severity-ranked)

### D1 — 🔴 CRITICAL — Claim conversation is localStorage-only (not persisted, not cross-user)
- **Route/files:** `pages/claims/ClaimViewMedicalReview.jsx` (~L363–376), `pages/provider/ProviderClaimsSubmission.jsx` (~L607, L2321).
- **Evidence:** `localStorage.getItem(chatStorageKey)` + `JSON.parse(...)`; both sides log `Failed to parse chat history`. No `Conversation`/`Message`/`ClaimMessage` entity exists in the backend; no message/conversation endpoint on `ClaimController`.
- **Expected:** messages persist in DB, linked to the claim; reviewer sees provider messages and vice versa; sender full name + role + facility + timestamp; server-derived unread; isolation + audit.
- **Actual:** messages live only in the author's browser; the other party never sees them; refresh on another device loses them.
- **Root cause:** no server-side messaging model; UI writes to `localStorage`.
- **Phase:** **5** (rebuild claim-linked conversation on a real model) → **6** (central messaging center). Matches the endorsed design: claim-linked thread → aggregated reviewer/provider inbox.

### D2 — 🟠 HIGH — Claim creation page is a single 2,561-line scroll
- **Route/file:** `/provider/claims/submit` — `ProviderClaimsSubmission.jsx` (2,561 lines).
- **Evidence:** `workflowSteps = ['بيانات المطالبة','الخدمات الطبية','المرفقات']` with `workflowActiveStep` **derived from completion state** (L1296–1297) — a decorative indicator, not step navigation; all sections rendered together.
- **Expected:** step-based workflow; each step ≈ one viewport; compact persistent context header; only the working panel scrolls.
- **Actual:** long vertical scroll; user must travel between eligibility/member/visit/services/diagnosis/attachments/submit; hard to see "what's missing / next".
- **Phase:** **3** (workflow redesign — steps, context header, service workspace, dedicated diagnosis + attachment steps, review step).

### D3 — 🟠 MEDIUM — Sticky bottom action bar overlaps content
- **File:** `ProviderClaimsSubmission.jsx` L2454–2456 (`position:'sticky'; zIndex:10`) + a sticky summary rail L2381.
- **Expected:** action bar never covers fields/validation; reserved layout space; safe at 100/125/150% zoom + laptop heights.
- **Actual:** sticky footer can cover form content/messages (matches the brief).
- **Phase:** **3** (bottom-bar approach with reserved padding / step footer).

### D4 — 🟠 MEDIUM — Generic error messages (revised after re-reading the error pipeline)
- **Files:** `utils/axios.js` (interceptor), `utils/api-error.js` (`normalizeApiError`), `services/errorLogger.js` (`getUserFriendlyMessage`), `components/GlobalApiErrorToaster.jsx`, `components/ErrorFallback.jsx`, `components/tba/ErrorFallback.jsx`.
- **Correction to original finding:** the frontend pipeline already *prefers* the backend's `messageAr`/`message` for 400s and forwards it through `normalizeApiError` → `api:error` → the toaster; «حدث خطأ غير متوقع» is only the **last-resort fallback** when the backend sends no usable message (or on unexpected exceptions/5xx with no body). So this is **not purely a frontend defect** — the real gap is **backend coverage**: which claim/visit/draft/pre-auth failure paths actually populate `messageAr` today, and which throw generic exceptions that reach the client with no message. That must be enumerated from real backend exception classes, not guessed.
- **Expected:** every blocking failure on eligibility/visit/draft/submit/services/attachments returns a specific `messageAr` from the backend (e.g. "لا يوجد عقد نشط لمقدم الخدمة لهذه الخدمة", "تم تعديل هذه المسودة في جلسة أخرى، الرجاء إعادة التحميل") with a recovery hint; the frontend fallback string is only ever seen for truly unexpected system errors.
- **Phase:** **1** — audit backend exception → response mapping for the blocking flows first (not just the frontend components), then fill in missing `messageAr` values.

### D5 — 🟡 MEDIUM — Draft/autosave needs verification
- **File:** `ProviderClaimsSubmission.jsx` (`autosaveStatus`, `autosaveAt`, `saveDraft`, L412–413, L707–729).
- **Verify:** debounce; no overlapping requests; **no duplicate drafts**; optimistic-locking/version preserved; no overwrite of newer server data; single clear status (جاري الحفظ / تم الحفظ / تعذر الحفظ).
- **Phase:** **1** (if it creates duplicates / loses version) or **3** (status UX).

### D6 — 🟡 MEDIUM — Attachment lifecycle needs end-to-end verification
- **Files:** `ProviderDocuments.jsx` (`/api/v1/provider/documents`), claim attachments (`/claims/{id}/attachments`), `ClaimAttachmentController`.
- **Verify (backend):** provider owns claim/visit; state validity; MIME + extension + size; filename sanitization; storage key; path-traversal guard; read/download/delete authZ; audit; reviewer visibility; cross-provider rejection.
- **Verify (frontend):** upload/progress/preview(PDF,PNG,JPEG)/download/delete/retry; oversized/unsupported/network/session; no page reset after upload; count in step status.
- **Phase:** **4**.

### D7 — 🟡 LOW — Navigation duplication (top nav + System Categories)
- **Evidence:** provider users get the horizontal top nav **and** the System Categories launcher (SidebarLayout).
- **Expected:** header = logo + Provider Portal identity + System Categories + user/provider identity + account/logout; no duplicate nav; System Categories carries all provider-authorized modules, RBAC-aware.
- **Phase:** **2**.

### D8 — 🟡 LOW — Service/category selector clarity & disabled reasons
- **Evidence:** claim page uses a category→service dependency; brief reports "states not always clear", "fields disabled without reason".
- **Expected:** category first → service filtered; explicit disabled reason; contract price shown when found; specific message when no contract price («لا يوجد سعر تعاقدي لهذه الخدمة ضمن العقد النشط»).
- **Phase:** **3**.

### Non-defects / strengths (do not regress)
- **Isolation** (`ProviderContextGuard`) — robust; keep.
- **Eligibility** — name search + camera + employer-support already fixed.
- **Reports** — scoped + immutable facility signature.

---

## 3. Phased backlog (revised order per owner decision, 2026-07-19)

| Phase | Title | Size | Risk | Addresses |
|---|---|---|---|---|
| **0** | Audit (this doc) | M | L | — |
| **1** | Critical functional errors: backend `messageAr` coverage + duplicate-draft/version check + regression tests | M | M | D4 (backend-first), D5 (dup-draft), any blocking failures found in live repro |
| **2** | Navigation simplification (System-Categories-only header) | S | L | D7 |
| **3** | Claim-creation **workspace** redesign: real steps, full-viewport layout, keyboard/speed-first data entry | L | M | D2, D3, D5(UX), D8, §7–8 |
| **4** | **Performance optimization** (new) — frontend + backend | M | M | §9 |
| **5** | Documents & attachments lifecycle + version history | M | M | D6, §6 |
| **6** | **Unified Messaging Engine** (new, backend-first) — one model/API for all conversation contexts | M | M | **D1** (foundation) |
| **7** | Two surfaces on the same engine: in-claim conversation (Jira/GitHub-issue style) + Messages Center | L | M/H | D1 (delivery), §5 |
| **8** | UX polish & responsiveness (1366×768, 125% zoom) | M | L | residual |
| **9** | End-to-end authenticated acceptance | M | L | verification |

**Order:** Phase 1 (unblock) → Phase 2 (nav) → Phase 3 (workspace) → Phase 4 (performance) → Phase 5 (documents) → Phase 6 (messaging engine) → Phase 7 (conversation + center on the engine) → Phase 8–9 (polish + acceptance).

**Why the engine moved before its surfaces (owner's call, adopted):** building "claim conversation" first and a separate "messaging center" second risks two divergent data models bolted together later. Instead Phase 6 ships one engine (`Conversation` / `Message` / `ReadReceipt`, one API) with **zero UI**, and Phase 7 is purely two read/write surfaces over the same engine — the in-claim thread and the aggregated center are two views, not two systems.

---

## 4. Messaging design (revised — engine first, two surfaces on top)

```
                Unified Messaging Engine
                (Conversation / Message / ReadReceipt)
                    one model, one API
                         │
             ┌───────────┴───────────┐
             ▼                       ▼
    In-claim conversation      Messages Center
    (Phase 7, surface A)       (Phase 7, surface B)
```

- **Phase 6 — engine only, no UI.** `Conversation(contextType, contextId, providerId, status, lastMessageAt)`, `Message(conversationId, senderUserId, senderRole, senderProviderId?, body, attachmentIds[], createdAt, editedAt?, deletedAt?)`, `ReadReceipt(conversationId/messageId, userId, readAt)`. New tables + migration (current store is `localStorage`, not reusable). One service/API layer both surfaces call — no duplicated read/write logic.
- **Phase 7 — two surfaces, same data:**
  - **In-claim thread**: embedded in the claim workspace (Phase 3 layout), scoped to that claim's conversation.
  - **Messages Center**: `/provider/messages` (provider) and `/messages/provider-claims` (reviewer) — aggregated list: claim number, member, provider/facility, last sender, last message preview, time, unread count, claim status, assignment. Opening a row deep-links to the same in-claim thread.
- **Initial context = CLAIM only.** PREAUTHORIZATION / CONTRACT / PRICE_LIST / GENERAL_SUPPORT are future contexts on the same engine, not new engines.
- **Conversation style — Jira/GitHub Issue, not WhatsApp** (owner requirement): each message renders as a discrete, timestamped entry showing **sender full name, role, facility name, timestamp, and any attachments** — a permanent audit-style record, not an ephemeral chat bubble stream. No message editing that hides history (edits keep an `editedAt`, no silent overwrite); deletion is soft (`deletedAt`) and stays visible as "message removed" for audit continuity.
- **Notifications:** in-app only (unread badge in System Categories + both inboxes); no Telegram/email/SMS/WhatsApp in this scope; no cross-provider leakage — every read enforces `ProviderContextGuard` the same way reports and visits already do.

---

## 5. Attachments — extended scope (owner addition, Phase 5)

Beyond preview/download/delete (original D6), Phase 5 must also cover:
- **Replace before submission** — swap a file on a still-draft claim without losing its slot/order.
- **Version history** — if a file is replaced, keep prior versions retrievable (who replaced it, when), not just overwritten in storage.
- **Provenance metadata surfaced in the UI**, not just stored server-side: uploader name, upload time, file size, MIME/type — shown next to each attachment, not only in an audit log.

This extends D6's existing backend checklist (ownership, state validity, MIME/extension/size, filename sanitization, path-traversal guard, authZ, audit) rather than replacing it.

## 6. Cross-cutting priority: data-entry speed (applies to Phases 3, 5, 7, 8)

The owner's stated goal: a front-desk clerk should be able to register a claim in **1–2 minutes**. This is a **design constraint on every remaining phase**, not a separate checklist item:
- Minimize clicks and screen transitions for the golden path (eligibility → visit → services → submit).
- Full keyboard operability: sensible **Tab order**, **Enter** advances to the next logical field (not just form submit), barcode scanner input works without a mouse click first, search fields are keyboard-reachable and keyboard-navigable (arrow keys + Enter to select).
- **Auto-focus** the first actionable field on step entry; **auto-select** field contents where re-entry is common (e.g. re-scanning a card number).
- Any new UI (workspace layout, attachments, conversation, messages center) must be evaluated against click-count and keyboard-completeness, not just visual polish.

## 7. Cross-cutting priority: full-viewport workspace layout (Phase 3)

Not "remove scrolling" — **replace the long page with a workspace that uses the full screen**, similar to established claims/ERP systems:
- Small, persistent header (claim/member identity, step indicator).
- One side panel: member/eligibility context (read-only reference while working).
- Other side panel: running claim summary (services added, running total, attachment count).
- Center: the active step's working area — this is the only region that scrolls, if anything.
- Applies to claim creation first (`ProviderClaimsSubmission`); the same pattern should be reused for pre-authorization submission where practical.

## 8. Performance optimization (new Phase 4)

Added as its own phase between workspace redesign and documents, so slowness introduced or exposed by the Phase 3 rebuild is caught before later phases build on top of it:
- **Frontend:** request count per screen/action, unnecessary refetches, React re-render hot spots, lazy-loading + code-splitting for heavy provider routes, bundle-size check, virtualization for large tables (documents list, messages center once built).
- **Backend:** N+1 query audit on provider-facing endpoints (visits, claims list, documents, reports), query plans on the hot paths, response caching where safe (e.g. static reference/category data), request deduplication on the frontend for rapid repeated calls (autosave, eligibility lookups).
- **UX of latency:** loading skeletons instead of blank/spinner-only states on the workspace and lists.
- Deliverable: a short before/after measurement (request counts, p95 latency on the golden-path endpoints, bundle size) — not just "it feels faster."

## 9. Not to be touched (per instruction)
Claims medical/financial business rules, taxonomy/medical classification, settlements, monitoring, backup, system administration — untouched unless a Phase proves a concrete defect. No `git add .`; no commit/push before each phase report is approved.

---

## 10. Open items to confirm during Phase 1 live repro
- Exact backend exception classes / status / body for a failing draft-save, submit, and eligibility check (to author the specific `messageAr` values — see revised D4).
- Whether autosave can create duplicate drafts (idempotency key / existing-draft reuse).
- Whether the "no contract price" path currently shows a vague vs specific message.
- Terminal-state policy for conversation (read-only vs open once claim is finalized) — decided in Phase 6 engine design, **must be explicit, not silent**.

---

## 11. Status

**Plan approved by product owner on 2026-07-19** with the revisions in this document (engine-before-surfaces messaging order, new Performance phase, speed-first and full-viewport constraints applied across Phases 3/5/7/8). Proceeding to **Phase 1**.

**PROVIDER-PORTAL-UX-1 PHASE 0 AUDIT — APPROVED (REVISION 1)**
