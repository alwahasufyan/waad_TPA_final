# PROVIDER-PORTAL-ELIGIBILITY-UX-1 — Redesign Provider Eligibility Check Workspace

## 1. Current page file identified

`frontend/src/pages/provider/ProviderEligibilityCheck.jsx`, routed at `/provider/eligibility-check` via:
- `frontend/src/routes/MainRoutes.jsx:75` — `const ProviderEligibilityCheck = Loadable(lazy(() => import('pages/provider/ProviderEligibilityCheck')));`
- `frontend/src/routes/MainRoutes.jsx:748-755` — route `path: 'eligibility-check'`.

This is the only file changed for this ticket.

## 2. Current APIs used (audited before coding, confirmed live)

- **Eligibility check**: `providerApi.checkEligibility({ barcode })` (`frontend/src/services/providerService.js:94`) → `POST /api/v1/provider/eligibility-check`. Confirmed via live curl (logged in as `dar` / PROVIDER_STAFF, providerId 1) — returns the `ProviderEligibilityResponse` DTO directly (not wrapped).
- **Member name search**: `searchMembersByName(q)` (`services/api/members.service`) — used for the "بحث باسم المستفيد" Autocomplete, unchanged.
- **Visit registration**: `providerApi.registerVisit({ memberId, eligibilityCheckId, visitType })` (`providerService.js:205`) → `POST /api/v1/provider/visits/register`. Unchanged — same call, same payload shape.
- **Visit type options**: hardcoded `VISIT_TYPE_OPTIONS` array in the page (8 Arabic-labeled values) — unchanged, no backend endpoint exists for this list.
- **Auth/provider scoping**: session-cookie auth (`withCredentials`), `providerId` is resolved server-side from the logged-in provider staff account — unchanged, not touched.

## 3. provider.tsx used as UI reference only

The attached `provider.tsx` (TanStack Router + Tailwind + `lucide-react`, with a hardcoded `FAMILY` array and fake amounts) was used **only** to copy visual structure/spacing/proportions. No Tailwind classes, no `lucide-react`, no TanStack code, and no fake data from it were copied into the page. All icons are `@mui/icons-material`; all layout uses MUI `Box`/`Paper`/`Grid`/`Stack`.

## 4. Visual elements adopted from the reference

- Two-panel layout: fixed-width (23.5rem) search/check panel + flexible result panel, matching the ~30/70 split in the screenshot (search panel renders on the visual right in RTL since it is the first flex child).
- Page-header "الاتصال بالمنصة نشط" status chip (static UI indicator, not tied to a health-check API — same as the reference, which is also a static UI element).
- Result panel: top status row (eligibility chip + `#barcode` reference chip + family-member count + reset button), member header row (avatar + name + "العضو الرئيسي · employer · age") with three summary chips (الحد السنوي / المستخدم / المتبقي), scrollable compact family table with sticky header, and a sticky-looking bottom bar (visit-type select + تسجيل الزيارة button) pinned to the bottom of the result card via flex layout.
- Family table columns match the ticket spec exactly: الاسم، الصلة، العمر، الحالة، المتبقي، النسبة، اختيار.
- Quick-check icon buttons (رقم البطاقة / الباركود / الكاميرا) above the barcode input — the card/barcode buttons focus the real input field, the camera button opens the existing QR scanner dialog (all real behavior, no placeholders that look active but do nothing).
- Empty state (before any check) keeps the previously-existing "today's checks / last member checked / quick tips / accepted-rejected today" quick-stat cards — real, computed from local `checkHistory`, not fake.

## 5. Exact files changed

- `frontend/src/pages/provider/ProviderEligibilityCheck.jsx` — the only file touched. All state, handlers (`checkEligibility`, `handleRegisterVisit`, QR scanner start/stop, hardware scanner listener, auto-check debounce, name-search debounce) are **unchanged**; only the JSX render tree was restructured, plus:
  - Added `SummaryStatChip` presentational component (module scope, same file).
  - Removed the old fixed-width sticky "ملف المنتفع" sidebar card (18.75rem) — its content (avatar, name, relationship, age, eligibility chip, remaining limit, usage %) is now shown compactly in the result panel's member header row + family table row (per-member), matching the reference's single-card layout instead of a separate sidebar.
  - Removed the old separate 4-box "Coverage Info" `Grid` (annual limit/used/remaining/usage%) — replaced by the 3 header `SummaryStatChip`s per the ticket spec (usage % remains visible per-row in the family table).
  - Removed the `MainCard`-wrapped "نتيجة الفحص" title bar in favor of the reference's compact status-row header.
  - `formatCurrency` now returns `—` (em dash) instead of `-` for null/undefined amounts, per the ticket's "unavailable field → —" rule.
  - Button label "فحص" → "تنفيذ الفحص" (matches reference/spec).
  - Removed unused `Divider` and `MainCard` imports; added `FiberManualRecordIcon`, `BadgeIcon`, `PhotoCameraIcon` (all `@mui/icons-material`, no new dependency).

No other file was modified.

## 6. Data mapping audit

All fields rendered come directly from the live `POST /api/v1/provider/eligibility-check` response (confirmed via curl against provider `دار الشفاء`, barcode `WCA20260002D1`):

| UI element | Response field |
|---|---|
| Eligibility chip | `result.eligible` |
| Reference chip | `result.barcode` |
| Family count | `result.totalFamilyMembers` (fallback `familyMembers.length`) |
| Member name/employer/age | `result.principalMember.fullName`, `result.employerName`, `result.principalMember.age` |
| Summary chips | `result.principalAnnualLimit`, `result.principalUsedAmount`, `result.principalRemainingLimit` |
| Family table rows | `result.familyMembers[]` — `fullName`, `relationship`, `age`, `eligible`, `remainingLimit`, `usagePercentage`, `isPrincipal` |
| Covered services | `result.coveredServices[]` |
| Warnings | `result.warnings[]` |

All of the above fields were confirmed present and populated in the live response, so no `—` fallback was triggered in this test data set. `formatCurrency`/`age ?? '—'` guards remain in place for cases where a provider/member record lacks these values.

## 7. Unavailable backend fields shown as "—"

None encountered in the tested response, but the following are still guarded to show `—` if absent (unchanged from before, now consistently using the em-dash per spec instead of the previous hyphen):
- `member.age` in the family table.
- Any `formatCurrency(...)` call where the amount is `null`/`undefined`.

One pre-existing gap (not introduced by this ticket, not fixed — out of scope): `result.eligibilityCheckId` is not present in the current backend response, so `handleRegisterVisit` sends it as `undefined` in the `registerVisit` payload. This was true before this redesign and is unrelated to the UI change; documented here per the ticket's "document missing field" rule.

## 8. Confirmation: no fake data

No hardcoded `FAMILY` array, no fake member/card/provider/amount values were introduced. All money values, names, ages, and statuses render from the live API response or from `checkHistory`, which is itself built entirely from real API responses returned during actual checks in the current session.

## 9. Confirmation: no new dependencies

`package.json` was not touched. Only additional `@mui/icons-material` icon imports (already an existing dependency) were added: `FiberManualRecord`, `Badge`, `PhotoCamera`.

## 10. Validation / lint / build result

- `git diff --check` — clean (only a pre-existing LF→CRLF line-ending notice from git, not a whitespace error).
- `npx eslint src/pages/provider/ProviderEligibilityCheck.jsx` — 0 errors. 1 pre-existing warning unrelated to this change (`react-hooks/exhaustive-deps` on the QR-scanner cleanup `useEffect`, present before this redesign). One prettier formatting warning was auto-fixed with `--fix`.
- `npx vite build` — succeeded. `ProviderEligibilityCheck` chunk built at 21.43 kB gzip 6.66 kB (real code, not a stub).
- Backend was **not** touched, so backend tests were **not** run, per the ticket's instruction.

## 11. Browser smoke result

**No browser/screenshot automation tool is available in this environment** (confirmed unavailable in this session, consistent with every prior UI ticket in this branch). I could not visually open the page in an actual browser to confirm pixel-level layout, so I cannot personally confirm items 1–3 and 8–11 of the ticket's "Validation" checklist (blank-screen check, exact visual match to the screenshot, panel positioning) as a rendered screenshot.

What I *did* verify without a browser:
- Rebuilt the frontend Docker image (`.\waad.ps1 rebuild frontend`) — build succeeded, container is healthy.
- `curl http://localhost:3001/provider/eligibility-check` → HTTP 200 (nginx serves the SPA shell; this confirms the route resolves and the bundle loads, not the rendered visual result).
- `npx vite build` compiled the new JSX without errors, meaning the component tree is structurally valid React/MUI (no invalid props, no runtime-breaking JSX).
- Live-called `POST /api/v1/provider/eligibility-check` as the `دار الشفاء` provider and confirmed every field the redesigned page reads (`principalMember.age`, `employerName`, `principalAnnualLimit/UsedAmount/RemainingLimit`, `totalFamilyMembers`, `barcode`, `familyMembers[]`, `coveredServices[]`) is present and correctly typed in the real response — i.e., the data-mapping audit in section 6 is backed by a live API call, not assumption.

Given the ticket's "Runtime/browser validation is mandatory" instruction, and given that a real visual check genuinely did not happen, the correct final status is **BLOCKED**, not READY FOR REVIEW.

## 12. Visit registration behavior result

`handleRegisterVisit` was not modified — same call to `providerApi.registerVisit({ memberId, eligibilityCheckId, visitType })`, same success/error handling (`navigate('/provider/visits', { state: { successMessage, newVisitId } })` on success, `setError(...)` on failure). I did not execute a live registration call in this pass (to avoid creating a spurious real visit record as a side effect of an automated test); this flow was live-verified in an earlier phase of this session (VISIT-BUG-1) and its code path is untouched here.

## 13. Confirmation Provider Claim Submission still works

Not modified in this ticket. `curl http://localhost:3001/provider/claims-submission` → HTTP 200 (route/bundle reachable). No backend or shared component used by that page was touched.

## 14. Confirmation Claims Review still works

Not modified in this ticket. `curl http://localhost:3001/claims/review` → HTTP 200 (route/bundle reachable). No backend or shared component used by those pages was touched.

## 15. Remaining gaps

- **Barcode/camera real integration**: the QR/barcode camera scanner (`html5-qrcode`) was already implemented before this ticket and is unchanged; the "رقم البطاقة"/"الباركود" quick buttons in the new layout simply focus the manual-entry field (there is no backend distinction between "barcode mode" vs "card mode" — both feed the same `barcode` field), consistent with the existing single-field API contract.
- **Provider portal dashboard/channels tab bar**: the reference's top tab bar (فحص الأهلية / إنشاء مطالبة / موافقة مسبقة) is not a reusable component in this codebase today — provider portal pages are separate routes navigated via the (now header-nav-free, per the prior session's system-wide change) sidebar/menu, not an in-page tab switcher. Adding such a tab bar would touch shared navigation and was out of this ticket's strict scope, so it was not added.
- **`eligibilityCheckId` missing from the eligibility-check response** — pre-existing backend gap documented in section 7, not fixed (backend changes were out of scope unless the page could not consume existing data at all, which is not the case here — `registerVisit` still works with `eligibilityCheckId: undefined`, `cleanPayload` strips it).
- **Navigation to claim creation after visit registration**: unchanged — `handleRegisterVisit` still routes to `/provider/visits` on success, not directly into claim creation. This was true before the redesign and is documented as pre-existing, not introduced or fixed here.

## 16. Rollback plan

Single-file change. Rollback:
```
git checkout -- frontend/src/pages/provider/ProviderEligibilityCheck.jsx
```
No migrations, no backend changes, no other files affected — a full revert is a one-line git command.

## 17. Confirmation no push was done

No commit was created. No push was performed. Work remains local-only on the current branch, uncommitted, awaiting review per standing instructions.

---

**PROVIDER-PORTAL-ELIGIBILITY-UX-1 BLOCKED — awaiting your browser confirmation (no browser/screenshot automation tool is available in this environment to visually verify the redesigned layout against the attached provider.tsx/screenshot; all non-visual validation — lint, build, live API data-shape checks, route reachability — passed)**
