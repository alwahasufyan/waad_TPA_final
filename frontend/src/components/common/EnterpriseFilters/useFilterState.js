import { useState, useCallback } from 'react';

/**
 * useFilterState — remembered filter values (Stage 2 / W1.1 Enterprise Filter Framework).
 *
 * A drop-in replacement for `useState(defaultValues)` that persists the filter
 * values to localStorage under a stable key, so a user returning to a page finds
 * their filters as they left them. Merges persisted values over the defaults, so
 * newly-added filter fields still get their default.
 *
 * @param {string} filterKey  stable id for this page's filters (e.g. 'claims-report-filters')
 * @param {object} defaultValues  the default filter values
 * @returns {[object, function]} [values, setValues] — setValues accepts a value or updater fn
 */
export default function useFilterState(filterKey, defaultValues) {
  const storageKey = filterKey ? `waad:flt:${filterKey}:values` : null;

  const [values, setValuesState] = useState(() => {
    if (!storageKey) return defaultValues;
    try {
      const raw = localStorage.getItem(storageKey);
      return raw ? { ...defaultValues, ...JSON.parse(raw) } : defaultValues;
    } catch {
      return defaultValues;
    }
  });

  const setValues = useCallback((next) => {
    setValuesState((prev) => {
      const resolved = typeof next === 'function' ? next(prev) : next;
      try {
        if (storageKey) localStorage.setItem(storageKey, JSON.stringify(resolved));
      } catch {
        /* storage unavailable — ignore */
      }
      return resolved;
    });
  }, [storageKey]);

  return [values, setValues];
}
