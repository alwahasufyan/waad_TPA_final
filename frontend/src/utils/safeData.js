/**
 * Safe Data Utilities
 * Defensive coding helpers for handling API responses safely
 *
 * @description Prevents crashes from undefined/null data, handles multiple API response shapes
 * @version 1.0.0
 */

/**
 * Safely extract array data from various API response shapes
 * Handles: { items }, { content }, { data }, direct arrays, undefined/null
 *
 * @param {any} response - API response data (already unwrapped from ApiResponse)
 * @param {string} [preferredKey] - Preferred key to look for first ('items', 'content', 'data')
 * @returns {Array} Always returns an array, empty if no valid data
 *
 * @example
 * // All these return the correct array:
 * safeArray({ items: [1,2,3] }) // [1,2,3]
 * safeArray({ content: [1,2,3] }) // [1,2,3]
 * safeArray([1,2,3]) // [1,2,3]
 * safeArray(undefined) // []
 * safeArray(null) // []
 */
export const safeArray = (response, preferredKey = null) => {
  // Handle null/undefined
  if (response == null) return [];

  // Direct array
  if (Array.isArray(response)) return response;

  // If preferred key specified, try it first
  if (preferredKey && Array.isArray(response[preferredKey])) {
    return response[preferredKey];
  }

  // Try common keys in order
  const keys = ['items', 'content', 'data', 'results', 'list'];
  for (const key of keys) {
    if (Array.isArray(response[key])) {
      return response[key];
    }
  }

  // If response has nested .data that's an array
  if (response.data && Array.isArray(response.data)) {
    return response.data;
  }

  // If response.data has common keys
  if (response.data && typeof response.data === 'object') {
    for (const key of keys) {
      if (Array.isArray(response.data[key])) {
        return response.data[key];
      }
    }
  }

  // Last resort: return empty array
  return [];
};

/**
 * Safely get pagination metadata from response
 *
 * @param {any} response - API response data
 * @param {Object} defaults - Default values
 * @returns {Object} Pagination metadata with safe defaults
 */
export const safePagination = (response, defaults = {}) => {
  const defaultValues = {
    page: 1,
    size: 20,
    total: 0,
    totalPages: 0,
    ...defaults
  };

  if (!response || typeof response !== 'object') {
    return defaultValues;
  }

  return {
    page: response.page ?? response.pageNumber ?? response.currentPage ?? defaultValues.page,
    size: response.size ?? response.pageSize ?? response.limit ?? defaultValues.size,
    total: response.total ?? response.totalElements ?? response.totalCount ?? response.count ?? defaultValues.total,
    totalPages:
      response.totalPages ?? response.pages ?? Math.ceil((response.total || 0) / (response.size || 20)) ?? defaultValues.totalPages
  };
};

/**
 * Safely access nested object properties
 *
 * @param {Object} obj - Object to access
 * @param {string} path - Dot-notation path (e.g., 'user.profile.name')
 * @param {any} defaultValue - Default value if path doesn't exist
 * @returns {any} Value at path or default
 */
export const safeGet = (obj, path, defaultValue = undefined) => {
  if (!obj || typeof path !== 'string') return defaultValue;

  const keys = path.split('.');
  let result = obj;

  for (const key of keys) {
    if (result == null || typeof result !== 'object') {
      return defaultValue;
    }
    result = result[key];
  }

  return result ?? defaultValue;
};

/**
 * Safe map function - always returns array, never crashes
 *
 * @param {any} data - Data to map (will be converted to array safely)
 * @param {Function} mapper - Map function
 * @returns {Array} Mapped array
 */
export const safeMap = (data, mapper) => {
  const arr = safeArray(data);
  if (typeof mapper !== 'function') return arr;
  return arr.map(mapper);
};

/**
 * Check if data has any items
 *
 * @param {any} data - Data to check
 * @returns {boolean} True if data has items
 */
export const hasItems = (data) => {
  return safeArray(data).length > 0;
};

/**
 * Safely render empty state or content
 *
 * @param {any} data - Data to check
 * @param {Function} renderContent - Function to render content if data exists
 * @param {React.ReactNode} emptyState - Component/element to render if empty
 * @returns {React.ReactNode}
 */
export const safeRender = (data, renderContent, emptyState = null) => {
  const items = safeArray(data);
  if (items.length === 0) return emptyState;
  return renderContent(items);
};

/**
 * Get safe selector options from API response
 * Common for dropdowns that need { id, label } format
 *
 * @param {any} response - API response
 * @param {Object} config - Configuration
 * @param {string} config.labelKey - Key for label (default: 'name')
 * @param {string} config.valueKey - Key for value (default: 'id')
 * @param {string} config.labelKeyAlt - Alternative label key
 * @returns {Array} Array of { value, label } objects
 */
export const safeSelectOptions = (response, config = {}) => {
  const { labelKey = 'name', valueKey = 'id', labelKeyAlt = null } = config;

  return safeArray(response).map((item) => ({
    value: item[valueKey],
    label: item[labelKey] || (labelKeyAlt ? item[labelKeyAlt] : '') || `Item ${item[valueKey]}`
  }));
};

export default {
  safeArray,
  safePagination,
  safeGet,
  safeMap,
  hasItems,
  safeRender,
  safeSelectOptions
};
