/**
 * Frontend Error Reporter (MON-BKP-LOG-1)
 * =======================================
 * Sends user-facing frontend errors to the backend error log so admins can see
 * exactly what a user hit, instead of relying on screenshots.
 *
 * Uses the raw fetch API (NOT the axios instance) so a report never re-triggers
 * the axios error interceptor and never causes a reporting loop. All failures
 * are swallowed — reporting must never surface an error of its own.
 */

const API_BASE = (import.meta.env.VITE_API_URL || '/api/v1').replace(/\/+$/, '');
const REPORT_URL = `${API_BASE}/system-errors/frontend`;

// Throttle: cap total reports per window and drop duplicate signatures.
const WINDOW_MS = 60 * 1000;
const MAX_PER_WINDOW = 10;
const DEDUPE_MS = 15 * 1000;

let windowStart = Date.now();
let windowCount = 0;
const recentSignatures = new Map();

const now = () => Date.now();

const withinRateLimit = () => {
  const ts = now();
  if (ts - windowStart > WINDOW_MS) {
    windowStart = ts;
    windowCount = 0;
  }
  if (windowCount >= MAX_PER_WINDOW) {
    return false;
  }
  windowCount += 1;
  return true;
};

const isDuplicate = (signature) => {
  const ts = now();
  const last = recentSignatures.get(signature);
  if (last && ts - last < DEDUPE_MS) {
    return true;
  }
  recentSignatures.set(signature, ts);
  // Opportunistic cleanup so the map does not grow unbounded.
  if (recentSignatures.size > 100) {
    for (const [key, when] of recentSignatures) {
      if (ts - when > DEDUPE_MS) recentSignatures.delete(key);
    }
  }
  return false;
};

const currentRoute = () => {
  try {
    return window.location.hash ? window.location.hash.replace(/^#/, '') : window.location.pathname;
  } catch {
    return null;
  }
};

/**
 * Report a frontend error. Fire-and-forget; never throws.
 * @param {object} payload
 * @param {string} [payload.severity] INFO|WARN|ERROR|CRITICAL (default ERROR)
 * @param {string} [payload.correlationId] backend trackingId if available
 * @param {string} [payload.userMessage] Arabic message shown to the user
 * @param {string} [payload.technicalMessage]
 * @param {string} [payload.errorCode]
 * @param {number} [payload.statusCode]
 * @param {string} [payload.path] API path that failed
 * @param {string} [payload.stackExcerpt]
 */
export const reportFrontendError = (payload = {}) => {
  try {
    const signature = [payload.errorCode, payload.statusCode, payload.path, payload.userMessage]
      .filter(Boolean)
      .join('|')
      .slice(0, 300);

    if (isDuplicate(signature) || !withinRateLimit()) {
      return;
    }

    const body = JSON.stringify({
      severity: payload.severity || 'ERROR',
      correlationId: payload.correlationId || null,
      userMessage: payload.userMessage || null,
      technicalMessage: payload.technicalMessage || null,
      errorCode: payload.errorCode || null,
      statusCode: payload.statusCode || null,
      path: payload.path || null,
      frontendRoute: currentRoute(),
      stackExcerpt: payload.stackExcerpt ? String(payload.stackExcerpt).slice(0, 4000) : null
    });

    // keepalive lets the request survive a navigation/unload.
    fetch(REPORT_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      keepalive: true,
      body
    }).catch(() => {
      /* swallow — reporting must never surface its own error */
    });
  } catch {
    /* never throw from the reporter */
  }
};

export default reportFrontendError;
