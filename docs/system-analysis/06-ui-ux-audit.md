# 06 — UI/UX Audit

> Scope: `frontend/src/` — React 19, MUI 7, Vite 7. All suggestions in this document are **presentation/ergonomics only** — none propose changing what a workflow does, who can do it, or in what order (per the mission constraint: "Suggest improvements WITHOUT changing business workflows").

---

## 1. Navigation

Single static menu array (`menu-items/components.jsx`, 693 lines), filtered per role via `config/roleAccessMap.js`'s `ROLE_RESOURCE_ACCESS` map. Structure:

- **لوحة المعلومات / Dashboard**
- **المستفيدين / Insured** (Members)
- **بوابة مقدم الخدمة / Provider Portal** (gated by resource + feature flag) — Eligibility Check, Visit Log, Documents, Claims/Pre-Auth/Visits Reports
- **جهات العمل / Employers** — Employers List, Benefit Policies
- **مقدمو الخدمات / Providers** — Providers List, Provider Contracts
- **المطالبات والموافقات / Claims & Approvals** — Batch System, Email Pre-Auth Inbox, Claims Report
- **التسويات المالية / Financial Settlement** — Provider Settlement, Provider Payments, Payments Management, Financial Consolidation, Accountant Profit Report
- **الوثائق / Documents** — flagged `__hidden_documents`, **explicitly hidden from all roles per an in-code comment ("Hidden per user request")** despite the page being fully built (906 lines) and routed — reachable only by direct URL.
- **إعدادات النظام / System Settings** — Users, Categories, System Config, Kinship Mismatch, Facility Price Prep (marked "تجريبي"/experimental), Medical Audit Logs, Member Duplicates Resolver

**Assessment:** The menu correctly mirrors the business's mental model (Employer → Provider → Claims → Settlement) and role-filters cleanly. The "hidden documents" entry is a loose end — either restore it to the menu or remove the dead page/route.

## 2. Menus

Menu titles are hardcoded bilingually inline (`title`/`titleEn` per entry) rather than sourced from the `locales/` i18n files that otherwise exist in the project — meaning the navigation text is **not actually locale-file-driven**, despite the infrastructure being present. This doesn't break anything today (the app is Arabic-first, English is a secondary/admin convenience), but it means adding a third language, or even just correcting a translation, requires editing code rather than a translation file.

**Non-disruptive improvement:** Migrate menu label text into `locales/ar.js`/`en.js` as string keys, resolved at render time — same visual output, easier maintenance, no workflow change.

## 3. Dashboards

`pages/dashboard/` consumes `DashboardController`'s 8 endpoints (summary, monthly-trends, members-growth, cost-by-provider, service-distribution, recent-activities, plus 2 legacy). Broadly accessible to all authenticated roles.

**Observation:** Because every role sees essentially the same dashboard shape, a `PROVIDER_STAFF` user and a `SUPER_ADMIN` likely see the same KPI cards even though most are financially/organizationally irrelevant to a provider-portal user (who has their own dedicated portal reports instead). Not confirmed from this pass whether the dashboard component itself further filters by role client-side — worth a targeted UI walkthrough to confirm the provider-portal path never surfaces the admin dashboard.

## 4. Forms

Formik 2 + Yup power form state/validation; a dedicated `schemas/` directory suggests shared validation schemas exist separately from page components (not deep-read in this pass — recommend a follow-up specifically to check whether the same business rule, e.g. "coverage percent must be 0–100," is validated identically on the frontend (Yup), backend (Bean Validation), and database (CHECK constraint) — the backend/DB layers are confirmed consistent per `05-database-analysis.md` §5, but a three-way frontend/backend/DB drift on validation ranges is a plausible, easy-to-miss inconsistency worth a dedicated audit).

**The largest frontend files are almost all forms co-located with tables and submission logic** — `ProviderClaimsSubmission.jsx` (2,561 lines), `ClaimBatchEntry.jsx` (2,085 lines), `BenefitPolicyRulesTab.jsx` (1,840 lines), `ProviderContractView.jsx` (1,674 lines). This is a maintainability concern (see `08-technical-debt.md`) but also a **direct UX concern**: a single 2,561-line component handling an entire claims-submission workflow is much harder to keep visually and behaviorally consistent (spacing, error placement, loading states) than a set of composed sub-components, because every tweak risks touching unrelated logic in the same file.

**Non-disruptive improvement:** Extract the visual sections of the largest form pages (e.g., "line items table," "attachment uploader," "financial summary panel") into presentational sub-components with the same props/behavior — pure refactor, zero workflow change, immediately improves consistency and testability.

## 5. Dialogs

No dedicated audit of dialog/modal patterns was performed at the component level in this pass (recommend follow-up: check `components/` for a shared `ConfirmDialog`/`FormDialog` wrapper vs. ad hoc `<Dialog>` usage per page — the existence of `components/GenericDataTable`, `UnifiedDataTable`, and domain-grouped component folders suggests the team *does* generally invest in shared components, so a shared dialog wrapper is plausible but unconfirmed).

## 6. Tables

Three separate "generic/unified" table components coexist: `components/GenericDataTable/GenericDataTable.jsx` (515 lines, has its own README — a good practice signal), `components/common/UnifiedDataTable.jsx` (477 lines), `components/common/UnifiedMedicalTable.jsx`. Direct `material-react-table` usage in pages is effectively zero despite being a declared dependency; `@mui/x-data-grid` appears directly in only 2 files — the team has clearly invested in in-house table abstractions rather than using the off-the-shelf libraries directly in page code (the libraries are likely wrapped *inside* these shared components).

**Assessment:** Having three parallel "unified" table components, each apparently grown for a different domain need, is itself a **partial-consolidation** signal — the instinct to build a shared abstraction was right, but it didn't fully converge into one. This is a real but low-urgency inconsistency: it doesn't break anything for users today, but it means a UX improvement made to one ("add sticky headers," "add row density toggle") won't automatically propagate to the others.

**Non-disruptive improvement:** A future consolidation pass (not proposed here as urgent) could converge `GenericDataTable`/`UnifiedDataTable`/`UnifiedMedicalTable` into one component with domain-specific column/action configs passed in as props — purely internal, no visible workflow change if done carefully.

## 7. Filters / Search / Pagination

Bulk-workflow support is genuinely good: `components/ExcelImport/DataImportWizard.jsx` + `components/members/MembersBulkUploadDialog.jsx` for bulk member import, `contexts/GlobalImportProgressContext.jsx` giving a global progress indicator for long-running imports (a real UX plus — users aren't left guessing whether a large import is still running). `html5-qrcode` barcode/QR scanning is integrated in both the back-office eligibility page and the provider-portal eligibility page, letting front-desk/provider staff scan a member card instead of typing a search query — a genuine efficiency feature well-suited to a high-volume clinical front desk.

No dedicated per-page filter/pagination audit was performed (would require opening each list page individually); the presence of the shared table components suggests filter/pagination behavior is likely consistent *within* whichever unified table a given page uses, but potentially inconsistent *across* the three different table implementations noted above.

## 8. Printing / PDF

- **PDF generation** (backend-rendered): OpenPDF + Flying Saucer render Thymeleaf HTML to PDF, with the **Cairo font embedded via `IDENTITY_H`** for correct Arabic glyph shaping — the right technical choice for Arabic PDF output. The one confirmed risk (see `03-module-catalog.md` §report) is a silent-failure mode if the font resource can't be loaded — worth a smoke test in the target deployment environment to confirm the font always loads correctly, since a failure here degrades silently into broken Arabic glyphs rather than an obvious error.
- **Frontend export**: `react-to-print`, `exceljs`, and `xlsx` are all declared dependencies, implying client-side print/Excel-export capability exists in the reports pages (`FinancialReports.jsx`, `BeneficiariesReports.jsx`, etc.) — not individually verified per page in this pass.

**Non-disruptive improvement:** Add an explicit startup health-check (or CI smoke test) that renders one sample Arabic PDF and asserts the font actually embedded, rather than discovering a broken font at first customer complaint.

## 9. Arabic RTL

This is one of the frontend's genuine strengths. `components/RTLLayout.jsx` centralizes the RTL switch: one `@emotion/cache` + `stylis-plugin-rtl` pairing, direction derived **from the active language** (not independently toggleable — a sound consistency guarantee that prevents a user ending up with Arabic text in an LTR layout or vice versa), and `document.documentElement.dir`/`document.body.dir` kept in sync.

**Two issues found, both minor:**
1. `pages/under-development/index.jsx` hardcodes `textAlign: 'left'` on an info box — in RTL this would misalign against the surrounding Arabic-first layout (should use logical `start` or simply omit it to inherit the RTL default).
2. **Two parallel i18n data sources** exist: `locales/ar.js`/`en.js` and a second, apparently redundant `utils/locales/ar.json`/`en.json` — a duplication risk if a translation is updated in one but not the other.

**Non-disruptive improvements:** Fix the one hardcoded `textAlign: 'left'`; consolidate the two locale-data sources into one (pure internal refactor, no visible change if the merged content is correct).

## 10. Libyan Usability

The system is built Arabic-first with Libyan business context baked into the domain model itself (Employer as the top-level entity rather than a generic "policy," `cr_number`/tax-number fields, Arabic category names matching a real reference price list per the seed-data migrations). This is a strength, not a gap — the domain vocabulary in both the UI and the database genuinely reflects how a Libyan TPA operator thinks about the business (employer-purchased group coverage, provider network contracts, category-based coverage rules), rather than being a generic international insurance template with Arabic labels bolted on.

One usability question worth flagging (not confirmed as a defect): the `FacilityPricePreparationPage` is explicitly marked "تجريبي" (experimental) in the menu — worth confirming with the product owner whether this label is still accurate or whether the feature has since graduated to production-ready, since a permanently-experimental-looking feature can erode user trust in an otherwise mature module.

## 11. Keyboard Efficiency

No global keyboard-shortcut system was found (no hotkey library in the dependency list). Efficiency is instead achieved through **bulk operations and barcode scanning** rather than power-user keyboard shortcuts — a reasonable choice for the actual user base (front-desk/clinical staff, back-office reviewers) who are more likely to benefit from "scan a card" or "upload 500 rows" than from `Ctrl+Shift+N`-style shortcuts common in developer tools.

**Non-disruptive improvement (optional, low priority):** For the highest-frequency repetitive actions in the batch-claims-entry workflow specifically (the single most form-intensive page in the app), consider row-level keyboard navigation (Tab/Enter to move between claim-line fields without reaching for the mouse) — this is an additive enhancement, not a workflow change, and would specifically help the accountant/data-entry persona who processes many claim lines per session.

## 12. Workflow Efficiency

- `contexts/TableRefreshContext.jsx` centralizes the "refresh the list after a mutation" pattern so CRUD pages don't each reinvent it — good cross-cutting UX consistency infrastructure.
- `notistack` toast notifications are used in at least 26 files directly, plus a `GlobalApiErrorToaster.jsx` for API-error-driven toasts — but adoption isn't universal across every list/detail page (not every page showed up in the direct-usage grep), meaning some pages may fail more silently than others. Worth a targeted audit: does every mutating action (create/update/delete/approve/reject) in every module surface a toast on both success and failure?
- The **Provider Portal is architecturally and visually separated** from the admin console (`pages/provider/` vs. the rest of `pages/`) — this is a good design decision for workflow efficiency: a provider-portal user is never shown irrelevant admin chrome, and the portal's own eligibility/visit/claims-submission pages are purpose-built for that single persona's linear workflow (check eligibility → register visit → submit claim).
- **Possible duplication**: eligibility-check UI logic appears independently implemented in both `pages/eligibility/EligibilityCheckPage.jsx` (back-office) and `pages/provider/ProviderEligibilityCheck.jsx` (provider portal) — likely intentional given the two different personas and layouts, but worth a spot-check to ensure the underlying business-rule display (coverage %, remaining limit, warnings) hasn't drifted between the two copies over time, since a divergence here would mean two personas seeing different eligibility information for the same member.

## 13. Dead / Under-Development UI Surfaces

- `pages/under-development/index.jsx` — a generic bilingual placeholder for unfinished routes (reasonable pattern to avoid broken links).
- `pages/test/LandingPageTest.jsx` — appears to be a dev-only scratch page; recommend confirming it's excluded from any production route/menu path.
- `pages/provider-contracts/data/providerContracts.mock.js` (657 lines) — a mock-data fixture still present in the `src` tree alongside the live service call; worth confirming it isn't accidentally imported anywhere in the production build.
- Two parallel layout/drawer implementations (`layout/Dashboard/*`, actively used, vs. `layout/Component/*`, appears to be a leftover from the underlying admin-template scaffold this project was built on) — the unused one is pure dead weight in the source tree, not a runtime risk, but worth removing to reduce future-maintainer confusion about which layout is "real."
- A stray editor swap file (`layout/Component/.index.jsx.swp`) — should simply be deleted and added to `.gitignore`.

## 14. Summary of Non-Disruptive UI/UX Improvements

*(None of these change any business workflow, permission, or data flow — all are presentation, consistency, or dead-code cleanup.)*

| # | Improvement | Effort | Value |
|---|---|---|---|
| 1 | Fix hardcoded `textAlign: 'left'` in `pages/under-development` for RTL correctness | Trivial | Low but real (visible in Arabic UI) |
| 2 | Consolidate duplicate i18n data sources (`locales/` vs `utils/locales/`) | Small | Prevents future translation drift |
| 3 | Migrate hardcoded bilingual menu labels into locale files | Small | Easier maintenance, no visual change |
| 4 | Remove the leftover `layout/Component/*` template scaffold and stray `.swp` file | Trivial | Reduces future-maintainer confusion |
| 5 | Confirm and re-expose (or formally retire) the hidden Documents Library menu entry | Trivial | Removes an inconsistent "reachable but not navigable" page |
| 6 | Add a startup/CI smoke test asserting the Arabic PDF font loads successfully | Small | Prevents silent Arabic-PDF breakage |
| 7 | Extract sub-sections of the largest form pages into presentational components | Medium | Meaningfully improves change-safety and visual consistency over time |
| 8 | Audit toast-notification coverage across all mutating actions for consistency | Medium | Ensures uniform user feedback on success/failure everywhere |
| 9 | Spot-check the two independent eligibility-check UIs for business-rule display parity | Small | Confirms two personas see identical coverage information |
| 10 | Confirm `FacilityPricePreparationPage`'s "experimental" label still reflects its actual status | Trivial | Product-owner confirmation, not a code change |

---

*Continue to [`07-domain-analysis.md`](./07-domain-analysis.md) for the domain-driven-design perspective on the same system.*
