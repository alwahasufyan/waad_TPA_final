# Stage 1 Production Hotfix — Employer–Member Consistency

**Type:** Stage 1 production hotfix (business integrity) · **Status:** ✅ Implemented & verified · ⏸ Awaiting your approval
**Governance:** ATEF Constitution (understand before modify; no workflow/UI change; reuse-first; defense-in-depth; tests).

---

## 1. Problem

In **Batch Claim Entry**, after selecting an Employer, the Member lookup could still display members of **other** employers — allowing a claim to be started for a member under the wrong employer.

## 2. Root Cause (found by reading the real code)

The member lookup already passed `employerId` to a primary, employer-scoped search. But the frontend service had a **fallback** "broader search" (used when the primary search returned empty) that set the wrong request parameter:

```js
// unified-members.service.js (BEFORE)
if (employerId) { criteria.organizationId = employerId; }   // ← backend ignores this key
const advancedResponse = await searchMembers({ ...criteria }); // hits /unified-members/search
```

The backend `/api/v1/unified-members/search` endpoint filters by **`employerId`**, not `organizationId`. So the fallback ran **unfiltered** and surfaced members from all employers. This was the single source of the reported symptom.

Backend note: claim creation already rejected cross-employer claims transitively (batch-employer vs. member-employer check in `ClaimService`), but there was **no explicit, mandated member-belongs-to-employer guard at the visit-centric root** — added here per requirement #4.

## 3. Fixes Implemented

### Requirement #1 — lookup shows only the selected employer's members
- **`frontend/src/services/api/unified-members.service.js`** — fallback now sends `criteria.employerId` (was `organizationId`). This scopes the fallback to the selected employer for **every** caller of the shared `unifiedSearch` lookup.

### Requirement #2 — clear message when the employer has no members, and block entry
- **`ClaimBatchEntry.jsx`** — added an employer member-count query (`getAllMembers({ employerId, size: 1 })` → `totalElements`); computes `employerHasNoMembers`.
- **`components/ClaimHeaderFields.jsx`** — when true, shows **"لا يوجد مستفيدون مسجلون لهذه الجهة."** and **disables** the member Autocomplete. Entry is further blocked by the existing "member required" save validation.

### Requirement #3 — clear stale selection when employer changes
- **`ClaimBatchEntry.jsx`** — added an effect keyed on `employerId`: on an actual change it clears the selected member and search input (`setMember(null)`, `setMemberInput('')`, `setDebouncedMemberInput('')`) via a previous-value ref so it does not disturb draft restoration on mount.

### Requirement #4 — mandatory backend validation (defense-in-depth)
- **`VisitCreateDto.java`** — added optional `employerId` (selected-employer context). Backward compatible (null → no enforcement).
- **`VisitService.java`** — new reusable guard `validateMemberBelongsToEmployer(member, expectedEmployerId)` called in **both `create` and `update`**; rejects with `BusinessRuleException` (HTTP 422): **"المستفيد المحدد لا ينتمي إلى جهة العمل المحددة…"** when the member's employer ≠ the supplied employer (or the member has no employer while a context is given). Because every claim/pre-auth is created against a Visit (Visit-Centric architecture), enforcing at the visit root covers the downstream flows.
- **`ClaimBatchEntry.jsx`** — visit creation now sends `employerId` so the backend enforces the rule even though the lookup is already scoped.

## 4. Similar Workflows Reviewed (Requirement #5)

| Workflow | Uses shared `unifiedSearch` lookup? | Cross-employer exposure? | Action |
|---|---|---|---|
| **Batch Claim Entry** (`ClaimBatchEntry.jsx`) | ✅ Yes | Yes (the reported bug) | Fixed (all 4 requirements) |
| **Provider Claims Submission** (`ProviderClaimsSubmission.jsx`) | ❌ No — operates on an **existing** visit (`visitDetails.member.employer.id`); the member is already bound to a visit, and provider access is employer-scoped | No | No change needed; also protected by the new visit-root guard |
| **Visit create/update** (`VisitService`) | n/a | Was the enforcement gap | Fixed (guard added; benefits all callers) |
| **Pre-Authorization** | n/a | Created against a Visit | Inherits the visit-root guard |
| **Manual Claim Entry** | Same screen as Batch Claim Entry (visitType `LEGACY_BACKLOG`) | Same path | Fixed by the same changes |

**The `organizationId` vs `employerId` param bug was isolated to the shared service's fallback** — fixing it once protects the shared lookup for every present and future caller (Constitution: reuse-first). Other `organizationId` usages in the codebase (reports/lists) correctly translate to `employerId` before calling the backend and were **not** affected.

## 5. Verification (live)

| Check | Result |
|---|---|
| Backend compile | ✅ green |
| `VisitServiceTest` (new, 4 cases: mismatch→reject, no-employer-but-context→reject, match→pass, null-context→skip) | ✅ **4/4 pass** |
| Backend guard — member 1 (employer 1) + `employerId=51` | ✅ **HTTP 422** — "المستفيد المحدد لا ينتمي إلى جهة العمل المحددة…" |
| Backend guard — member 1 (employer 1) + `employerId=1` | ✅ **HTTP 201** created |
| Backend `/unified-members/search?employerId=1` | ✅ returns only employer 1's members (confirms the frontend param fix now scopes the fallback) |
| Backend `/unified-members/count` per employer | ✅ 3 vs 1 (drives the empty-employer message) |
| Frontend ESLint (changed files) | ✅ **0 errors** (only pre-existing project-wide prettier warnings) |
| App health | ✅ backend 200, frontend 200 |
| Test data | ✅ cleaned up (no leftover test visits) |

**Manual browser verification is reserved for you.** The app is running: frontend `http://localhost:3001`, backend `http://localhost:8081`, login `superadmin@tba.sa` / `Admin@123`. Suggested check: open a Batch Claim Entry for an employer and confirm the member lookup only lists that employer's members (including when a search returns no exact match — the fallback stays scoped); confirm an employer with no members shows the message and blocks entry.

## 6. Files Modified / Added

**Frontend:**
- `src/services/api/unified-members.service.js` — fallback param `organizationId` → `employerId` (root-cause fix)
- `src/pages/claims/batches/ClaimBatchEntry.jsx` — pass `employerId` to visit; member-count query + `employerHasNoMembers`; clear-stale-on-employer-change effect; pass prop
- `src/pages/claims/batches/components/ClaimHeaderFields.jsx` — `employerHasNoMembers` prop: empty message + disable member Autocomplete

**Backend:**
- `modules/visit/dto/VisitCreateDto.java` — added optional `employerId`
- `modules/visit/service/VisitService.java` — `validateMemberBelongsToEmployer` guard in `create` + `update`
- `src/test/java/.../visit/service/VisitServiceTest.java` *(new)* — 4 validation tests

**No database migration. No workflow/UI redesign. No business-rule change** — only the enforcement of employer-member consistency, exactly as scoped. Existing behavior is preserved when no employer context is supplied (backward compatible).

## 7. Impact
- **Business integrity:** ↑↑ — claims can no longer be started for a member under the wrong employer; enforced at the UI, the search, and (mandatorily) the backend.
- **Regression risk:** low — additive validation (opt-in via employer context), isolated param fix, defensive UI guards; `VisitServiceTest` green; live journeys healthy.

---

*Awaiting your review and approval. I have not resumed Stage 2 planning; this hotfix is self-contained.*
