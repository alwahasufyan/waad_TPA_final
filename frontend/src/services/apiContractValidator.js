/**
 * API Contract Validator
 * =======================
 *
 * PRODUCTION READINESS (2026-01-13):
 * - Validates API responses against expected schemas
 * - Warns on contract violations without crashing
 * - Helps detect DTO changes early
 * - Development-only validation (disabled in production)
 */

// ==============================|| CONTRACT DEFINITIONS ||============================== //

/**
 * API Response contracts - Expected response structures
 * Update these when DTOs change intentionally
 */
export const API_CONTRACTS = {
  // ============== AUTH ==============
  'auth/session/me': {
    success: {
      status: 'string',
      data: {
        id: 'number',
        username: 'string',
        roles: 'array'
      }
    }
  },

  'auth/session/login': {
    success: {
      status: 'string',
      data: {
        id: 'number',
        username: 'string',
        roles: 'array'
      }
    }
  },

  // ============== MEMBERS ==============
  members: {
    list: {
      items: 'array',
      totalItems: 'number',
      totalPages: 'number',
      currentPage: 'number'
    },
    single: {
      id: 'number',
      name: 'string',
      nationalNumber: 'string?',
      status: 'string'
    }
  },

  // ============== CLAIMS ==============
  claims: {
    list: {
      content: 'array',
      totalElements: 'number',
      totalPages: 'number',
      number: 'number'
    },
    single: {
      id: 'number',
      claimNumber: 'string?',
      status: 'string',
      submissionDate: 'string?'
    }
  },

  // ============== EMPLOYERS ==============
  employers: {
    list: {
      content: 'array',
      totalElements: 'number'
    },
    single: {
      id: 'number',
      name: 'string',
      status: 'string'
    }
  },

  // ============== PROVIDERS ==============
  providers: {
    list: {
      content: 'array',
      totalElements: 'number'
    },
    single: {
      id: 'number',
      name: 'string',
      status: 'string'
    }
  },

  // ============== DASHBOARD ==============
  'dashboard/summary': {
    success: {
      totalMembers: 'number',
      activeMembers: 'number',
      totalClaims: 'number',
      openClaims: 'number'
    }
  },

  // ============== USERS ==============
  users: {
    list: {
      content: 'array',
      totalElements: 'number'
    },
    single: {
      id: 'number',
      username: 'string',
      roles: 'array'
    }
  },

  // ============== ROLES ==============
  roles: {
    list: 'array',
    single: {
      id: 'number',
      name: 'string',
      permissions: 'array?'
    }
  }
};

// ==============================|| TYPE VALIDATORS ||============================== //

const typeValidators = {
  string: (v) => typeof v === 'string',
  'string?': (v) => v === null || v === undefined || typeof v === 'string',
  number: (v) => typeof v === 'number' && !isNaN(v),
  'number?': (v) => v === null || v === undefined || (typeof v === 'number' && !isNaN(v)),
  boolean: (v) => typeof v === 'boolean',
  'boolean?': (v) => v === null || v === undefined || typeof v === 'boolean',
  array: (v) => Array.isArray(v),
  'array?': (v) => v === null || v === undefined || Array.isArray(v),
  object: (v) => v !== null && typeof v === 'object' && !Array.isArray(v),
  'object?': (v) => v === null || v === undefined || (typeof v === 'object' && !Array.isArray(v))
};

// ==============================|| VALIDATION FUNCTIONS ||============================== //

/**
 * Validate a value against a schema
 * @param {any} value - Value to validate
 * @param {object|string} schema - Schema to validate against
 * @param {string} path - Current path for error reporting
 * @returns {string[]} Array of validation errors
 */
const validateValue = (value, schema, path = 'root') => {
  const errors = [];

  // If schema is a string type
  if (typeof schema === 'string') {
    const validator = typeValidators[schema];
    if (validator && !validator(value)) {
      errors.push(`${path}: expected ${schema}, got ${typeof value}`);
    }
    return errors;
  }

  // If schema is an object, validate each field
  if (typeof schema === 'object' && schema !== null) {
    // Check if value is an object
    if (value === null || typeof value !== 'object') {
      errors.push(`${path}: expected object, got ${typeof value}`);
      return errors;
    }

    // Validate each field in schema
    for (const [key, fieldSchema] of Object.entries(schema)) {
      const fieldValue = value[key];
      const fieldPath = `${path}.${key}`;

      // Check if optional (ends with ?)
      const isOptional = typeof fieldSchema === 'string' && fieldSchema.endsWith('?');

      if (fieldValue === undefined && !isOptional) {
        errors.push(`${fieldPath}: missing required field`);
      } else if (fieldValue !== undefined) {
        errors.push(...validateValue(fieldValue, fieldSchema, fieldPath));
      }
    }
  }

  return errors;
};

/**
 * Get contract key from URL
 * @param {string} url - API URL
 * @returns {string|null} Contract key or null
 */
const getContractKey = (url) => {
  if (!url) return null;

  // Remove /api prefix and query params
  let cleanUrl = url.replace(/^\/api\//, '').split('?')[0];

  // Remove trailing ID (e.g., /members/123 -> members)
  cleanUrl = cleanUrl.replace(/\/\d+$/, '');

  return cleanUrl;
};

/**
 * Get appropriate schema from contract
 * @param {object} contract - Contract definition
 * @param {any} response - Response data
 * @returns {object|null} Schema to validate against
 */
const getSchemaForResponse = (contract, response) => {
  if (!contract) return null;

  // Check if it's a list response
  if (response?.content || response?.items) {
    return contract.list || contract.success;
  }

  // Check if it's a success wrapper
  if (response?.status === 'success') {
    return contract.success;
  }

  // Single item
  return contract.single || contract.success || contract;
};

// ==============================|| MAIN VALIDATION FUNCTION ||============================== //

/**
 * Validate API response against contract
 * ONLY validates in development mode
 *
 * @param {string} url - API URL
 * @param {any} response - Response data
 * @returns {{ valid: boolean, errors: string[] }}
 */
export const validateApiResponse = (url, response) => {
  // Skip validation in production
  if (import.meta.env.PROD) {
    return { valid: true, errors: [] };
  }

  // Skip for null/undefined responses (may be permission skipped)
  if (response === null || response === undefined) {
    return { valid: true, errors: [] };
  }

  const contractKey = getContractKey(url);
  if (!contractKey) {
    return { valid: true, errors: [] };
  }

  const contract = API_CONTRACTS[contractKey];
  if (!contract) {
    // No contract defined - skip validation
    return { valid: true, errors: [] };
  }

  const schema = getSchemaForResponse(contract, response);
  if (!schema) {
    return { valid: true, errors: [] };
  }

  const errors = validateValue(response, schema, contractKey);

  if (errors.length > 0) {
    console.warn(`⚠️ [API Contract] Validation warnings for ${url}:`, errors);
  }

  return {
    valid: errors.length === 0,
    errors
  };
};

// ==============================|| AXIOS INTEGRATION ||============================== //

/**
 * Create axios response interceptor for contract validation
 * Add this to axios instance for automatic validation
 */
export const createContractValidationInterceptor = () => {
  return (response) => {
    // Validate response
    const validation = validateApiResponse(response.config?.url, response.data?.data || response.data);

    // Attach validation result to response (for debugging)
    response._contractValidation = validation;

    return response;
  };
};

// ==============================|| EXPORTS ||============================== //

export default {
  API_CONTRACTS,
  validateApiResponse,
  createContractValidationInterceptor
};
