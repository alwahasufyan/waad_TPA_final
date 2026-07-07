/**
 * EmployerFilterContext
 *
 * Global context for managing employer filter state across the application.
 *
 * Purpose:
 * - Single source of truth for selected employer
 * - Triggers data refresh when employer changes
 * - Integrates with all listing pages (Members, Claims, Contracts, etc.)
 *
 * Security:
 * - Enforces server-side filtering via employerId parameter
 * - Prevents client-side data leakage
 *
 * Usage:
 * ```jsx
 * import { useEmployerFilter } from 'contexts/EmployerFilterContext';
 *
 * function MyComponent() {
 *   const { selectedEmployerId, selectedEmployer, setEmployer, clearFilter } = useEmployerFilter();
 *
 *   // Pass employerId to all API calls
 *   const { data } = useQuery(['claims', selectedEmployerId], () =>
 *     getClaims({ employerId: selectedEmployerId })
 *   );
 * }
 * ```
 */

import { createContext, useContext, useState, useCallback, useEffect } from 'react';
import PropTypes from 'prop-types';

// ============================================================================
// CONTEXT DEFINITION
// ============================================================================

const EmployerFilterContext = createContext({
  selectedEmployerId: null,
  selectedEmployer: null,
  setEmployer: () => {},
  clearFilter: () => {},
  isFilterActive: false
});

// ============================================================================
// CONTEXT PROVIDER
// ============================================================================

export const EmployerFilterProvider = ({ children }) => {
  // State: selected employer ID
  const [selectedEmployerId, setSelectedEmployerId] = useState(null);

  // State: selected employer details (name, etc.)
  const [selectedEmployer, setSelectedEmployer] = useState(null);

  /**
   * Set partner filter
   * @param {Object} partner - Partner object with id and label/name
   */
  const setEmployer = useCallback((partner) => {
    if (!partner) {
      setSelectedEmployerId(null);
      setSelectedEmployer(null);
      return;
    }

    setSelectedEmployerId(partner.id);
    setSelectedEmployer({
      id: partner.id,
      label: partner.label || partner.name,
      name: partner.label || partner.name,
      code: partner.code
    });

    // Save to localStorage for persistence
    try {
      localStorage.setItem('tba_selected_partner_id', partner.id.toString());
      localStorage.setItem(
        'tba_selected_partner',
        JSON.stringify({
          id: partner.id,
          label: partner.label || partner.name,
          name: partner.label || partner.name,
          code: partner.code
        })
      );
    } catch (error) {
      console.warn('[PartnerFilter] Failed to save to localStorage:', error);
    }
  }, []);

  /**
   * Clear partner filter
   */
  const clearFilter = useCallback(() => {
    setSelectedEmployerId(null);
    setSelectedEmployer(null);

    // Clear from localStorage
    try {
      localStorage.removeItem('tba_selected_partner_id');
      localStorage.removeItem('tba_selected_partner');
      // Legacy cleanup
      localStorage.removeItem('tba_selected_employer_id');
      localStorage.removeItem('tba_selected_employer');
    } catch (error) {
      console.warn('[PartnerFilter] Failed to clear localStorage:', error);
    }
  }, []);

  /**
   * Restore partner filter from localStorage on mount
   */
  useEffect(() => {
    try {
      // Try new key first
      let savedId = localStorage.getItem('tba_selected_partner_id');
      let savedData = localStorage.getItem('tba_selected_partner');

      // Fallback to legacy keys for backward compatibility
      if (!savedId) {
        savedId = localStorage.getItem('tba_selected_employer_id');
        savedData = localStorage.getItem('tba_selected_employer');
      }

      if (savedId && savedData) {
        const parsed = JSON.parse(savedData);
        setSelectedEmployerId(parseInt(savedId, 10));
        setSelectedEmployer({
          id: parsed.id,
          label: parsed.label || parsed.name,
          name: parsed.label || parsed.name,
          code: parsed.code
        });
      }
    } catch (error) {
      console.warn('[PartnerFilter] Failed to restore from localStorage:', error);
    }
  }, []);

  // Computed: is filter active?
  const isFilterActive = selectedEmployerId !== null;

  // Context value
  const value = {
    selectedEmployerId,
    selectedEmployer,
    setEmployer,
    clearFilter,
    isFilterActive
  };

  return <EmployerFilterContext.Provider value={value}>{children}</EmployerFilterContext.Provider>;
};

EmployerFilterProvider.propTypes = {
  children: PropTypes.node.isRequired
};

// ============================================================================
// CUSTOM HOOK
// ============================================================================

/**
 * Hook to access employer filter context
 * @returns {Object} Employer filter context
 */
export const useEmployerFilter = () => {
  const context = useContext(EmployerFilterContext);

  if (!context) {
    throw new Error('useEmployerFilter must be used within EmployerFilterProvider');
  }

  return context;
};

export default EmployerFilterContext;
