# DASHBOARD-CATEGORIES-NAV-CLEANUP-1

## Status

READY FOR REVIEW (local changes only; no push).

## What was corrected

The previous implementation exposed a separate maintenance menu and a flat launcher that duplicated the old navigation. The launcher is now the single curated navigation surface. The original menu tree remains available to RBAC and route guards, but the horizontal list is no longer rendered.

## Final navigation model

`SystemCategoriesDialog` renders permission-filtered cards in five sections:

- السجلات الأساسية: beneficiaries, employers, providers, contracts, medical services.
- المطالبات والموافقات: claims, batches, pre-authorizations.
- التقارير: report centre, claims/provider reports, medical audit.
- الإعدادات: users/permissions and required system settings.
- أدوات الصيانة: maintenance tools, only when the existing RBAC-filtered menu exposes them.

No new routes were invented. Card destinations use existing menu nodes or existing routes.

## Maintenance and settings cleanup

The maintenance entries remain in the menu data solely as RBAC sources, but are not rendered as a separate sidebar or horizontal menu. They are surfaced only as the maintenance section inside System Categories. Financial coverage, email, AI, and the duplicate operational-settings tab are hidden from the visible System Settings tabs; backend settings and routes were not deleted.

## Medical audit

The visible audit card is under Reports and uses `/reports/medical-audit` with the existing `report_domain_audit` permission. The old settings menu entry was removed earlier. The legacy admin route definition remains guarded for compatibility and is not exposed by the navigation; it should be retired in a later route-cleanup change after consumers are confirmed.

## RBAC

Cards are resolved only from `useRBACSidebar()` output. Consequently, maintenance is absent for non-authorized users and report/settings cards retain their existing permission filtering. No role checks or backend authorization were changed.

## Files changed for this ticket

- `frontend/src/config/dashboardCategories.js`
- `frontend/src/components/dashboard/SystemCategoriesDialog.jsx`
- `frontend/src/layout/Dashboard/Header/HeaderContent/HorizontalNavigation.jsx`
- `frontend/src/layout/SidebarLayout/index.jsx`
- `frontend/src/pages/settings/SystemSettingsPage.jsx`
- this report

Other worktree changes were pre-existing and were not modified or staged.

## Validation

- `git diff --check`: passed (only existing line-ending warnings).
- Frontend lint: passed with existing repository warnings, zero errors.
- Frontend production build: passed (`vite build`, 17,804 modules transformed).
- Backend: unchanged.
- Browser SUPER_ADMIN/non-SUPER_ADMIN walkthrough: requires the running authenticated browser session and is not claimed as automated evidence here.

## Rollback

Revert the five files above. Route definitions and backend data remain intact, so rollback is limited to navigation presentation.

No commit, push, migration, or backend change was performed.
