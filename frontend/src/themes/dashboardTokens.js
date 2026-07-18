// ============================================================================
// Dashboard Design Tokens (scoped to the operational dashboard only)
// ----------------------------------------------------------------------------
// These tokens style the operational home dashboard + System Categories dialog.
// They do NOT modify the global MUI palette or the company primaryColor. The
// dashboard primary follows CompanySettingsContext.primaryColor with a safe
// fallback, so a company-specific colour keeps working without breaking pages.
// Reference palette (MED UI/UX brief): calm medical teal + soft surfaces.
// ============================================================================

// Safe fallback primary (used only when no company primaryColor is provided)
export const DASHBOARD_PRIMARY_FALLBACK = '#147D75';

// Semantic status colours — shown only in icons, borders and badges.
export const dashboardStatus = Object.freeze({
  success: '#2E7D52',
  warning: '#C58A16',
  error: '#B64B43',
  info: '#3B6F91',
  pending: '#6C63A8'
});

// Neutral surfaces / text for the light operational dashboard.
export const dashboardNeutral = Object.freeze({
  pageBg: '#F5FAF9', // very light teal-tinted background
  surface: '#FFFFFF',
  textPrimary: '#162625',
  textMuted: '#667573',
  border: 'rgba(20, 125, 117, 0.12)'
});

// Dark surfaces for the System Categories dialog (calm, near-uniform).
export const dashboardDark = Object.freeze({
  overlay: 'rgba(9, 20, 22, 0.72)',
  container: '#0E1F22',
  card: '#12262A',
  cardHover: '#163037',
  border: 'rgba(255, 255, 255, 0.08)',
  borderStrong: 'rgba(255, 255, 255, 0.14)',
  text: '#E7F0EE',
  textMuted: '#8FA6A2'
});

// Shared shape / motion tokens (soft, professional, WCAG-friendly).
export const dashboardShape = Object.freeze({
  radius: 12,
  radiusSm: 8,
  shadowSoft: '0 1px 2px rgba(22,38,37,0.04), 0 4px 16px rgba(22,38,37,0.06)',
  shadowHover: '0 2px 4px rgba(22,38,37,0.06), 0 8px 24px rgba(22,38,37,0.10)',
  transition: '150ms cubic-bezier(0.4, 0, 0.2, 1)'
});

/**
 * Resolve the dashboard "primary" from the company setting with a safe fallback.
 * Never mutates the theme; consumers read this per-render.
 */
export const resolveDashboardPrimary = (companyPrimaryColor) => {
  const c = typeof companyPrimaryColor === 'string' ? companyPrimaryColor.trim() : '';
  return c || DASHBOARD_PRIMARY_FALLBACK;
};

/**
 * Map a semantic key to its status colour (fallback: info).
 */
export const statusColor = (key) => dashboardStatus[key] || dashboardStatus.info;
