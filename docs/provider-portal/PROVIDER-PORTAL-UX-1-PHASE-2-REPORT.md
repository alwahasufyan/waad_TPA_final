# PROVIDER-PORTAL-UX-1 — Phase 2 Report: Navigation Simplification (System Categories only) (Revision 1 — correction)

**Status:** Implemented locally. **Not committed, not pushed** — awaiting your review.
**Scope:** D7 only (navigation duplication for provider users). No other Phase touched.

**Correction (you reported the fix didn't take effect — you were right):** my first pass edited `frontend/src/layout/Dashboard/Header/HeaderContent/index.jsx`, which is **dead code** — nothing in `frontend/src/routes` imports `layout/Dashboard` at all. The layout actually mounted by `MainRoutes` is `layout/SidebarLayout/index.jsx`, which has its **own**, separate inline top bar (categories button + `displayGroups.map(...)` nav buttons, ~L566–584) — that's the real duplication you were seeing, untouched by my first edit. Fixed below, in the right file this time, and verified the wrong file really is unreferenced before touching it again.

**Second bug found while fixing this:** `isProvider` in both `HeaderContent/index.jsx` and `SidebarLayout/index.jsx` was computed as `user?.roles?.includes('PROVIDER')` — but the actual role string in this codebase is `'PROVIDER_STAFF'` (see `constants/rbac.js`), never the bare string `'PROVIDER'`. So `isProvider` was **always `false`**, for every provider user, everywhere it was used. This is why even a correct conditional would have done nothing. Replaced both with `useRBAC().isProviderRole`, the existing, already-correct RBAC helper (`isProviderRole = (role) => role === SystemRole.PROVIDER_STAFF`) — a pre-existing bug, not something Phase 1/2 introduced, but it directly blocked this fix so it's in scope to correct.

---

## 1. Confirmed root cause (in the actually-mounted layout)

`frontend/src/layout/SidebarLayout/index.jsx` (mounted by `MainRoutes` as the app's top-level `element`) renders, in its `TopBar`'s center section (~L566–584):
1. The **System Categories** launcher button (`AppsIcon` → opens `SystemCategoriesDialog`, backed by `useRBACSidebar().sidebarItems` — RBAC-filtered, flattened tiles), then
2. `displayGroups.map((group) => <DesktopNavGroupButton .../>)` — a horizontal group-nav bar, backed by `useRBACSidebar().sidebarGroups` — the **same** RBAC-filtered menu, just grouped instead of flattened.

Both were rendered unconditionally for every authenticated user, so a `PROVIDER_STAFF` user saw the identical set of authorized pages reachable two ways at once in the top bar — confirmed duplication, not just a visual impression. No permanent vertical drawer competes on desktop (`<Drawer variant="temporary">` is mobile-only), so this was strictly the two-surfaces-in-the-topbar problem you reported.

## 2. Fix applied

```diff
- <Stack direction="row" alignItems="center" spacing={1} sx={{ overflowX: 'auto', minWidth: 0 }}>
-   {displayGroups.map((group) => (
-     <DesktopNavGroupButton key={group.id} group={group} />
-   ))}
- </Stack>
+ {!isProvider && (
+   <Stack direction="row" alignItems="center" spacing={1} sx={{ overflowX: 'auto', minWidth: 0 }}>
+     {displayGroups.map((group) => (
+       <DesktopNavGroupButton key={group.id} group={group} />
+     ))}
+   </Stack>
+ )}
```
Plus the `isProvider` bug fix described above (`useRBAC().isProviderRole` instead of the always-false `user?.roles?.includes('PROVIDER')`), in both `SidebarLayout/index.jsx` and (for consistency, though unused) `Dashboard/Header/HeaderContent/index.jsx`.

**Result for provider users:** top bar = branding → System Categories launcher only (single navigation surface, RBAC-filtered, identical link set to before) → profile. Non-provider users are unaffected — their group nav still renders exactly as before.

## 3. What this does NOT touch
- `SystemCategoriesDialog` itself — untouched, it was already correct and RBAC-aware.
- Mobile navigation (mobile categories `IconButton`, mobile `Drawer`) — untouched, no duplication was found there.
- Any provider page content, routing, or RBAC rules.
- `layout/Dashboard/Header/HeaderContent/index.jsx` — confirmed dead code (not imported by any route); left the `isProvider` correctness fix there since it's harmless and correct, but it has no runtime effect either way.

## 4. Verification
- `npx eslint src/layout/SidebarLayout/index.jsx src/layout/Dashboard/Header/HeaderContent/index.jsx` — 0 errors (3 pre-existing unrelated warnings, unchanged).
- `npx vite build` — succeeds (`✓ built in 25.74s`), no new warnings.
- Confirmed `layout/Dashboard` has zero references anywhere under `frontend/src/routes` before relying on `SidebarLayout` as the real target this time.
- **Still not manually click-tested in a live authenticated browser session** (no browser automation available here) — this is exactly the kind of gap that caused the first miss, so please treat this one as unverified until you (or a live run) confirms a `PROVIDER_STAFF` login shows the System Categories launcher only, with no adjacent group-nav buttons, in the actual browser.

## 5. Regression risk
Minimal — one conditional render change plus a `roles.includes('PROVIDER')` → `isProviderRole` correctness fix (same semantics as the code already used everywhere else in RBAC, just fixing the literal string). No state, no route, no RBAC-data changes.

---

**PROVIDER-PORTAL-UX-1 PHASE 2 — READY FOR REVIEW (fix corrected, real file).** Given the first miss, please specifically confirm this one visually before approving — I'd rather you catch a second wrong-file mistake than assume it's right.
