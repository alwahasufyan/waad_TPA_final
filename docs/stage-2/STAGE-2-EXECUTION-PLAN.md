# Stage 2 — Execution Plan (v3 — WORKFLOW-BASED)

**Theme:** Operational Experience — make Waad TPA feel like a modern enterprise platform used by professionals all day, not a set of CRUD screens.
**Organizing principle (changed):** Stage 2 is now organized around **business workflows**, not generic UI components.
**Status:** 📝 **PROPOSED — awaiting your approval. No implementation started** (per "regenerate before implementing further UI work").
**Supersedes:** the component-oriented plan (Enterprise Tables / generic sub-stages). The generic **Enterprise Filter Framework** is not dropped — it is built as shared infrastructure inside the first workflow that needs it (Claims → Filters) and reused by the rest.

---

## What is already done (foundation — reused by every workflow)

| Done & approved | What it gives every workflow |
|---|---|
| **2.1 + 2.1-B.1 Shared Table Foundation** | All 19 tables share one component with persisted **density**, **column show/hide + reorder**, **refresh**, and **empty-state action**. Every workflow's *Tables* step builds on this instead of re-inventing it. |
| **Stage 1 (stabilization + hotfixes)** | Secure, integrity-guarded base (JWT, visit-delete guard, employer-member consistency, etc.). |

These stay. Workflow steps below **extend** them, never re-do them.

---

## Workflow Priority (your order)

1. **Claims & Approvals** — highest (claim officers + medical reviewers, all day)
2. **Provider Portal** — provider staff, external-facing, high volume
3. **Financial Settlements** — finance staff
4. **Provider Contracts** — pricing/contract admins

Generic/shared UI polish resumes **only after** these four workflows are done.

## Improvement order INSIDE every workflow (your 8 steps)

```
Header → Actions → Filters → Tables → Row Actions → Details → Printing → Responsive
```
Every step: **Analyze → Implement → Build → Self-Review → Browser/API Verify → Fix → Completion Report → STOP** (your manual test & approval before the next step). Each step must **reduce clicks, reduce scrolling, improve readability, increase productivity** — with **no workflow/business-rule redesign**.

The **Stage 2 Hotfix rule stays active**: any Business-Integrity / Financial / Medical / Security / Data-Integrity bug found → stop, classify as hotfix, fix, verify, resume.

---

## Workflow 1 — Claims & Approvals  (pages: `ClaimBatchManagement`, `ClaimBatchEntry`, `ClaimBatchDetail`, `ClaimViewMedicalReview`, `PreApprovalsList`, `EmailPreAuthInbox`, claim reports + `ClaimsFilters`)

| # | Step | Concrete intent (no workflow change) |
|---|---|---|
| **W1.1** | **Header** | Compact, consistent page header: title, key context (employer/provider/period), collapsible KPI summary (your note: KPI cards are too large on operational pages). Less vertical space → more table. |
| **W1.2** | **Actions** | One consistent action bar (search, refresh, export, print, new) — same order + icons everywhere in the workflow. |
| **W1.3** | **Filters** ⭐ | **Build the reusable Enterprise Filter Framework here** (first + heaviest use): collapsible panel + drawer option, saved filters, filter chips, clear-all, remember open/closed + values, **maximum table area**. Migrate the Claims filters to it. |
| **W1.4** | **Tables** | Adopt the shared foundation's `persistKey` toolbar (done) + workflow-specific columns/alignment; unified status badges; aligned LYD financial columns (fixed decimals). |
| **W1.5** | **Row Actions** | Consistent, discoverable per-row actions (view/review/approve/reject/print) — fewer clicks, consistent placement. |
| **W1.6** | **Details** | Claim/review detail readability: clear sections, less scrolling, key numbers surfaced. |
| **W1.7** | **Printing** | Claim statement/print polish (ties into Stage-2 Reports/PDF later): A4, RTL, professional. |
| **W1.8** | **Responsive** | Desktop-first; graceful down to tablet for the review screens. |

## Workflow 2 — Provider Portal  (pages: `ProviderClaimsSubmission`, `ProviderPreApprovalSubmission`, `ProviderEligibilityCheck`, `ProviderVisitLog`, `ProviderDocuments`, provider reports)

Same 8-step order (**W2.1 … W2.8**). Reuses the filter framework + table foundation from W1. Emphasis: the provider's linear flow (eligibility → visit → claim), readability, and tablet-friendliness (front-desk hardware).

## Workflow 3 — Financial Settlements  (pages: `ProviderAccountsList`, `ProviderAccountView`, `ProviderPaymentsList`, `PaymentsManagement`, financial-consolidation + settlement reports)

Same 8-step order (**W3.1 … W3.8**). Emphasis: aligned LYD figures + fixed decimals, clear running-balance readability, fast month-by-month reconciliation, consistent payment row actions.

## Workflow 4 — Provider Contracts  (pages: `ProviderContractsList`, `ProviderContractView`, contract create/edit, pricing import)

Same 8-step order (**W4.1 … W4.8**). Emphasis: contract/pricing readability, consistent actions, filters, and detail layout.

---

## Reusable assets produced along the way (built once, used everywhere)

- **Enterprise Filter Framework** (W1.3) → reused by W2–W4.
- **Consistent Header + Action Bar patterns** (W1.1/W1.2) → reused.
- **Shared Table Foundation** (already done) → reused.
- **Unified status-badge + LYD number formatting helpers** (W1.4) → reused.

This is how "organize around workflows" and "reusable UI" coexist: shared pieces are created **when a real workflow first needs them**, then adopted by the next workflows — never as speculative generic work.

## Guardrails (every step)

- No business-workflow redesign; no new/unnecessary features.
- Reduce clicks / scrolling; improve readability; increase productivity.
- Reuse-first; backward-compatible; desktop-first; all browsers; fast.
- Verify (build + tests + browser/API) and **run the app at the end of each step** for your manual test.
- **STOP after each step** for your approval before the next.

## Definition of "Enterprise Tables complete"

Per your instruction, Enterprise Tables is considered complete only after the **Filter Framework exists and the heaviest operational pages are migrated** — which is exactly **W1.3 (framework) + the Filters/Tables steps of Workflows 1–4**. So the filter work is now delivered *inside* the workflows, on the pages that matter most, rather than as a generic component in isolation.

---

## Requested approval

Please confirm one of:
1. **Approve** → I begin **W1.1 (Claims & Approvals — Header)** following the full lifecycle, and STOP for your test.
2. **Approve but start at a different step/page** (e.g., go straight to **W1.3 Filters** since filters are the biggest pain).
3. **Adjust** the workflow order or the per-step scope.

No implementation begins until you approve.
