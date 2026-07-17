import { useState, useEffect, useCallback, useMemo } from 'react';
import preAuthDashboardService from 'services/api/preauth-dashboard.service';

/**
 * useDailyWorkItems — presentation/mapping hook for the operational
 * "Daily Work Box" on the home dashboard.
 *
 * Data sources (verified, real API only — NO financial/settlement data):
 *  - openClaims: passed in from the page's existing useDashboardStats()
 *    (avoids a duplicate /api/dashboard/summary request).
 *  - Pre-authorizations: preAuthDashboardService.getStats().pendingCount
 *    and getExpiringSoon() length — a single extra source used nowhere else
 *    on this page.
 *
 * Behaviour:
 *  - 403 from PreAuth  => user not authorised for pre-auth: hide those items
 *    silently (no error card, no empty card).
 *  - Non-403 error     => quiet "load failed" flag for the pre-auth portion;
 *    the rest of the box (claims) keeps working.
 *  - Zero counts       => item is not shown as a warning. If every supported
 *    item is zero, `allClear` is true (page shows a positive empty state).
 *
 * @param {Object}  params
 * @param {number}  params.openClaims          claims under review (from summary)
 * @param {boolean} params.enabled             whether to fetch pre-auth
 */
export const useDailyWorkItems = ({ openClaims = 0, enabled = true } = {}) => {
  const [preAuth, setPreAuth] = useState({ pending: 0, expiring: 0 });
  const [preAuthLoading, setPreAuthLoading] = useState(enabled);
  const [preAuthForbidden, setPreAuthForbidden] = useState(false);
  const [preAuthError, setPreAuthError] = useState(false);

  // Defensive unwrap for ApiResponse-wrapped or raw payloads.
  const unwrap = (raw) => (raw && typeof raw === 'object' && 'data' in raw ? raw.data : raw);

  const fetchPreAuth = useCallback(async () => {
    if (!enabled) {
      setPreAuthLoading(false);
      return;
    }
    setPreAuthLoading(true);
    setPreAuthForbidden(false);
    setPreAuthError(false);
    try {
      const [statsRaw, expiringRaw] = await Promise.all([
        preAuthDashboardService.getStats(),
        preAuthDashboardService.getExpiringSoon(7, 50)
      ]);
      const stats = unwrap(statsRaw) || {};
      const expiringList = unwrap(expiringRaw);
      const pending = Number(stats.pendingCount ?? stats.pending ?? 0) || 0;
      const expiring = Array.isArray(expiringList) ? expiringList.length : Number(expiringList?.count ?? 0) || 0;
      setPreAuth({ pending, expiring });
    } catch (err) {
      const status = err?.response?.status;
      if (status === 403) {
        setPreAuthForbidden(true); // not authorised — hide pre-auth items silently
      } else {
        setPreAuthError(true); // real technical error — quiet, non-fatal
      }
    } finally {
      setPreAuthLoading(false);
    }
  }, [enabled]);

  useEffect(() => {
    fetchPreAuth();
  }, [fetchPreAuth]);

  // Candidate items (all real, operational). `to` = a verified existing route.
  const candidates = useMemo(() => {
    const list = [
      {
        id: 'claims-review',
        // Operational claims review. NOTE: /claims/batches is the batch system,
        // not an operational claims list; this system has no dedicated claims
        // list route, so we use the real claims-review report route instead.
        label: 'مطالبات قيد المراجعة',
        count: Number(openClaims) || 0,
        color: 'warning',
        iconKey: 'claims',
        to: '/reports/claims',
        supported: true
      }
    ];
    if (enabled && !preAuthForbidden && !preAuthError) {
      list.push({
        id: 'preauth-pending',
        label: 'موافقات مسبقة معلّقة',
        count: preAuth.pending,
        color: 'info',
        iconKey: 'preauth',
        to: '/pre-approvals',
        supported: true
      });
      list.push({
        id: 'preauth-expiring',
        label: 'موافقات تنتهي قريبًا',
        count: preAuth.expiring,
        color: 'pending',
        iconKey: 'time',
        to: '/pre-approvals',
        supported: true
      });
    }
    return list;
  }, [openClaims, preAuth, enabled, preAuthForbidden, preAuthError]);

  const items = useMemo(() => candidates.filter((c) => c.count > 0), [candidates]);
  const allClear = useMemo(() => candidates.length > 0 && candidates.every((c) => c.count === 0), [candidates]);

  return {
    items,
    allClear,
    loading: preAuthLoading,
    preAuthForbidden,
    preAuthError,
    refresh: fetchPreAuth
  };
};

export default useDailyWorkItems;
