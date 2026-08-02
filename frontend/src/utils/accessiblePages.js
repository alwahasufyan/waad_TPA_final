/**
 * WAAD-RBAC-PER-USER-LANDING-PAGE-1
 *
 * Single source of truth for "which pages exist and what permission do they
 * need" is the sidebar menu definition (menu-items/components.jsx) — this
 * module does NOT duplicate that catalog, it only flattens it into a plain
 * list and filters it against a set of effective permission codes. Used by:
 *   - UserEdit.jsx, to populate the "default landing page" dropdown for a
 *     target user (filtered by THEIR effective permissions)
 *   - utils/roleRoutes.js's resolveLandingRoute(), to pick the actual
 *     post-login route for the CURRENTLY authenticated user
 *
 * Pure functions only — no React, no network calls, easy to unit test.
 */

import menuItem from 'menu-items/components';

/**
 * Flatten the (possibly nested group/collapse) menu tree into a flat list of
 * leaf pages. Only `type: 'item'` entries with both a `url` and a
 * `permission` are eligible — items with no permission mapped yet (a handful
 * of role-only-gated pages) can't be validated against effective permissions,
 * so they're deliberately excluded from "default landing page" candidates.
 *
 * @param {Array} items
 * @param {string|null} category - Arabic title of the enclosing top-level group
 * @returns {Array<{id:string, url:string, titleAr:string, titleEn:string, permission:string, resource:string, category:string}>}
 */
export const flattenPages = (items = menuItem, category = null) => {
  const pages = [];

  items.forEach((item) => {
    if (item.type === 'item' && item.url && item.permission) {
      pages.push({
        id: item.id,
        url: item.url,
        titleAr: item.title,
        titleEn: item.titleEn,
        permission: item.permission,
        resource: item.resource,
        category: category || item.title
      });
    }

    if (Array.isArray(item.children)) {
      const nextCategory = item.type === 'group' ? item.title : category;
      pages.push(...flattenPages(item.children, nextCategory));
    }
  });

  return pages;
};

/** The full page catalog, computed once at module load (menu tree is static). */
export const ALL_PAGES = flattenPages();

/**
 * Pages a user with the given effective permissions can currently reach.
 * SUPER_ADMIN bypasses the permission catalog entirely (matches
 * PermissionGuard/filterMenuItemsByRole's own SUPER_ADMIN short-circuit).
 *
 * @param {Array<string>} effectivePermissions
 * @param {boolean} [isSuperAdmin]
 * @returns {Array} subset of ALL_PAGES
 */
export const getAccessiblePages = (effectivePermissions, isSuperAdmin = false) => {
  if (isSuperAdmin) return ALL_PAGES;
  const set = new Set(Array.isArray(effectivePermissions) ? effectivePermissions : []);
  return ALL_PAGES.filter((page) => set.has(page.permission));
};

/** True if `route` is one of the currently-accessible pages. */
export const isPageAccessible = (route, effectivePermissions, isSuperAdmin = false) => {
  if (!route) return false;
  if (isSuperAdmin) return true;
  const set = new Set(Array.isArray(effectivePermissions) ? effectivePermissions : []);
  const page = ALL_PAGES.find((p) => p.url === route);
  return Boolean(page && set.has(page.permission));
};

/** Look up the permission code a given route requires (null if unknown). */
export const getPermissionForRoute = (route) => {
  const page = ALL_PAGES.find((p) => p.url === route);
  return page ? page.permission : null;
};

export default {
  flattenPages,
  ALL_PAGES,
  getAccessiblePages,
  isPageAccessible,
  getPermissionForRoute
};
