/**
 * Centralized Form Validation Utility
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Phase C - Frontend Validation Cleanup
 *
 * PURPOSE:
 * - Unify validation rules across Create, Edit, and Excel Import flows
 * - Normalize payloads (empty string → null, trim text, block negative numbers)
 * - Reduce backend validation errors by catching issues early
 *
 * USAGE:
 * import { normalizePayload, validators, validateField } from 'utils/formValidation';
 *
 * // Before API call
 * const cleanPayload = normalizePayload(formData);
 *
 * // Validate specific field
 * const error = validateField('email', value, { required: true });
 *
 * @version 1.0
 * @since 2026-01-15
 */

// ═══════════════════════════════════════════════════════════════════════════
// PAYLOAD NORMALIZATION
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Normalize a payload object before sending to API
 * - Trims all string values
 * - Converts empty strings to null
 * - Preserves booleans, numbers, arrays, and nested objects
 *
 * @param {Object} obj - The form data object
 * @param {Object} options - Normalization options
 * @param {boolean} options.recursive - Process nested objects (default: true)
 * @param {string[]} options.preserveEmpty - Fields to keep as empty string
 * @returns {Object} Normalized object
 */
export const normalizePayload = (obj, options = {}) => {
  const { recursive = true, preserveEmpty = [] } = options;

  if (obj === null || obj === undefined) {
    return null;
  }

  if (Array.isArray(obj)) {
    return obj.map((item) => normalizePayload(item, options));
  }

  if (typeof obj !== 'object') {
    // Handle primitive values
    if (typeof obj === 'string') {
      const trimmed = obj.trim();
      return trimmed === '' ? null : trimmed;
    }
    return obj;
  }

  // Handle Date objects
  if (obj instanceof Date) {
    return obj;
  }

  const result = {};

  for (const [key, value] of Object.entries(obj)) {
    if (value === undefined) {
      continue; // Skip undefined values entirely
    }

    if (value === null) {
      result[key] = null;
      continue;
    }

    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (preserveEmpty.includes(key)) {
        result[key] = trimmed;
      } else {
        result[key] = trimmed === '' ? null : trimmed;
      }
    } else if (typeof value === 'boolean') {
      result[key] = value;
    } else if (typeof value === 'number') {
      result[key] = value;
    } else if (Array.isArray(value)) {
      result[key] = recursive ? value.map((item) => normalizePayload(item, options)) : value;
    } else if (typeof value === 'object') {
      // Check for dayjs/moment objects
      if (value && typeof value.format === 'function') {
        result[key] = value; // Keep date objects as-is for later formatting
      } else if (recursive) {
        result[key] = normalizePayload(value, options);
      } else {
        result[key] = value;
      }
    } else {
      result[key] = value;
    }
  }

  return result;
};

/**
 * Normalize a single value (for real-time form handling)
 * @param {*} value - The value to normalize
 * @returns {*} Normalized value
 */
export const normalizeValue = (value) => {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    return trimmed === '' ? null : trimmed;
  }
  return value;
};

// ═══════════════════════════════════════════════════════════════════════════
// FIELD VALIDATORS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Validation result type
 * @typedef {Object} ValidationResult
 * @property {boolean} valid - Is the value valid
 * @property {string|null} error - Error message (Arabic) or null
 */

/**
 * Collection of field validators
 * Each returns { valid: boolean, error: string|null }
 */
export const validators = {
  /**
   * Required field validator
   */
  required: (value, fieldLabel = 'هذا الحقل') => {
    const isEmpty = value === null || value === undefined || (typeof value === 'string' && value.trim() === '');
    return {
      valid: !isEmpty,
      error: isEmpty ? `${fieldLabel} مطلوب` : null
    };
  },

  /**
   * Email format validator
   */
  email: (value) => {
    if (!value || value.trim() === '') {
      return { valid: true, error: null }; // Empty is OK (use required separately)
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    const isValid = emailRegex.test(value.trim());
    return {
      valid: isValid,
      error: isValid ? null : 'صيغة البريد الإلكتروني غير صحيحة'
    };
  },

  /**
   * Phone number validator (Saudi format)
   */
  phone: (value) => {
    if (!value || value.trim() === '') {
      return { valid: true, error: null };
    }
    // Saudi phone: 05xxxxxxxx or +9665xxxxxxxx
    const phoneRegex = /^(05\d{8}|(\+?966)?5\d{8})$/;
    const cleaned = value.trim().replace(/[\s-]/g, '');
    const isValid = phoneRegex.test(cleaned);
    return {
      valid: isValid,
      error: isValid ? null : 'رقم الهاتف غير صحيح (مثال: 0512345678)'
    };
  },

  /**
   * National ID validator (Saudi format - 10 digits starting with 1 or 2)
   */
  nationalNumber: (value) => {
    if (!value || value.trim() === '') {
      return { valid: true, error: null };
    }
    const idRegex = /^[12]\d{9}$/;
    const isValid = idRegex.test(value.trim());
    return {
      valid: isValid,
      error: isValid ? null : 'رقم الهوية يجب أن يكون 10 أرقام تبدأ بـ 1 أو 2'
    };
  },

  /**
   * Positive number validator (blocks negative and zero)
   */
  positiveNumber: (value, fieldLabel = 'القيمة') => {
    if (value === null || value === undefined || value === '') {
      return { valid: true, error: null };
    }
    const num = typeof value === 'string' ? parseFloat(value) : value;
    if (isNaN(num)) {
      return { valid: false, error: `${fieldLabel} يجب أن يكون رقماً` };
    }
    if (num <= 0) {
      return { valid: false, error: `${fieldLabel} يجب أن يكون أكبر من صفر` };
    }
    return { valid: true, error: null };
  },

  /**
   * Non-negative number validator (allows zero, blocks negative)
   */
  nonNegativeNumber: (value, fieldLabel = 'القيمة') => {
    if (value === null || value === undefined || value === '') {
      return { valid: true, error: null };
    }
    const num = typeof value === 'string' ? parseFloat(value) : value;
    if (isNaN(num)) {
      return { valid: false, error: `${fieldLabel} يجب أن يكون رقماً` };
    }
    if (num < 0) {
      return { valid: false, error: `${fieldLabel} لا يمكن أن يكون سالباً` };
    }
    return { valid: true, error: null };
  },

  /**
   * Percentage validator (0-100)
   */
  percentage: (value, fieldLabel = 'النسبة') => {
    if (value === null || value === undefined || value === '') {
      return { valid: true, error: null };
    }
    const num = typeof value === 'string' ? parseFloat(value) : value;
    if (isNaN(num)) {
      return { valid: false, error: `${fieldLabel} يجب أن يكون رقماً` };
    }
    if (num < 0 || num > 100) {
      return { valid: false, error: `${fieldLabel} يجب أن يكون بين 0 و 100` };
    }
    return { valid: true, error: null };
  },

  /**
   * Max length validator
   */
  maxLength: (value, maxLen, fieldLabel = 'هذا الحقل') => {
    if (!value || typeof value !== 'string') {
      return { valid: true, error: null };
    }
    const isValid = value.trim().length <= maxLen;
    return {
      valid: isValid,
      error: isValid ? null : `${fieldLabel} يجب ألا يتجاوز ${maxLen} حرف`
    };
  },

  /**
   * Min length validator
   */
  minLength: (value, minLen, fieldLabel = 'هذا الحقل') => {
    if (!value || typeof value !== 'string') {
      return { valid: true, error: null };
    }
    const isValid = value.trim().length >= minLen;
    return {
      valid: isValid,
      error: isValid ? null : `${fieldLabel} يجب أن يكون ${minLen} أحرف على الأقل`
    };
  },

  /**
   * Date range validator (start must be before or equal to end)
   */
  dateRange: (startDate, endDate) => {
    if (!startDate || !endDate) {
      return { valid: true, error: null };
    }

    // Handle dayjs objects
    const start = startDate.toDate ? startDate.toDate() : new Date(startDate);
    const end = endDate.toDate ? endDate.toDate() : new Date(endDate);

    const isValid = start <= end;
    return {
      valid: isValid,
      error: isValid ? null : 'تاريخ البداية يجب أن يكون قبل تاريخ النهاية'
    };
  },

  /**
   * Future date validator
   */
  futureDate: (value, fieldLabel = 'التاريخ') => {
    if (!value) {
      return { valid: true, error: null };
    }
    const date = value.toDate ? value.toDate() : new Date(value);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const isValid = date >= today;
    return {
      valid: isValid,
      error: isValid ? null : `${fieldLabel} يجب أن يكون في المستقبل`
    };
  },

  /**
   * Past date validator
   */
  pastDate: (value, fieldLabel = 'التاريخ') => {
    if (!value) {
      return { valid: true, error: null };
    }
    const date = value.toDate ? value.toDate() : new Date(value);
    const today = new Date();
    today.setHours(23, 59, 59, 999);
    const isValid = date <= today;
    return {
      valid: isValid,
      error: isValid ? null : `${fieldLabel} يجب أن يكون في الماضي أو اليوم`
    };
  },

  /**
   * Code/identifier pattern validator (alphanumeric with dashes)
   */
  code: (value, fieldLabel = 'الرمز') => {
    if (!value || value.trim() === '') {
      return { valid: true, error: null };
    }
    const codeRegex = /^[A-Za-z0-9\-_]+$/;
    const isValid = codeRegex.test(value.trim());
    return {
      valid: isValid,
      error: isValid ? null : `${fieldLabel} يجب أن يحتوي على أحرف وأرقام فقط`
    };
  }
};

// ═══════════════════════════════════════════════════════════════════════════
// FORM VALIDATION HELPERS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Validate a single field with multiple rules
 *
 * @param {string} fieldType - Type of validation (email, phone, etc.)
 * @param {*} value - The value to validate
 * @param {Object} options - Validation options
 * @param {boolean} options.required - Is field required
 * @param {string} options.label - Field label for error messages
 * @param {number} options.maxLength - Maximum length
 * @param {number} options.minLength - Minimum length
 * @returns {string|null} Error message or null if valid
 */
export const validateField = (fieldType, value, options = {}) => {
  const { required = false, label = 'هذا الحقل', maxLength, minLength } = options;

  // Check required first
  if (required) {
    const reqResult = validators.required(value, label);
    if (!reqResult.valid) return reqResult.error;
  }

  // Check min/max length for strings
  if (typeof value === 'string' && value.trim()) {
    if (minLength) {
      const minResult = validators.minLength(value, minLength, label);
      if (!minResult.valid) return minResult.error;
    }
    if (maxLength) {
      const maxResult = validators.maxLength(value, maxLength, label);
      if (!maxResult.valid) return maxResult.error;
    }
  }

  // Type-specific validation
  if (validators[fieldType]) {
    const result = validators[fieldType](value, label);
    if (!result.valid) return result.error;
  }

  return null;
};

/**
 * Validate an entire form using a schema
 *
 * @param {Object} formData - The form data
 * @param {Object} schema - Validation schema
 * @returns {Object} Object with field -> error message mapping
 *
 * @example
 * const schema = {
 *   email: { type: 'email', required: true, label: 'البريد الإلكتروني' },
 *   phone: { type: 'phone', label: 'الهاتف' },
 *   amount: { type: 'nonNegativeNumber', required: true, label: 'المبلغ' }
 * };
 * const errors = validateForm(formData, schema);
 */
export const validateForm = (formData, schema) => {
  const errors = {};

  for (const [field, rules] of Object.entries(schema)) {
    const value = formData[field];
    const error = validateField(rules.type || 'text', value, {
      required: rules.required,
      label: rules.label,
      maxLength: rules.maxLength,
      minLength: rules.minLength
    });

    if (error) {
      errors[field] = error;
    }
  }

  return errors;
};

/**
 * Check if form has any errors
 * @param {Object} errors - Error object from validateForm
 * @returns {boolean}
 */
export const hasErrors = (errors) => {
  return Object.keys(errors).length > 0;
};

// ═══════════════════════════════════════════════════════════════════════════
// EXCEL IMPORT HELPERS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Normalize Excel row data before processing
 * Handles common Excel import issues:
 * - Trailing/leading spaces
 * - Empty cells as various types
 * - Number formatting issues
 *
 * @param {Object} row - Raw Excel row data
 * @returns {Object} Normalized row
 */
export const normalizeExcelRow = (row) => {
  const normalized = {};

  for (const [key, value] of Object.entries(row)) {
    // Skip null/undefined
    if (value === null || value === undefined) {
      normalized[key] = null;
      continue;
    }

    // Handle strings
    if (typeof value === 'string') {
      const trimmed = value.trim();
      normalized[key] = trimmed === '' ? null : trimmed;
      continue;
    }

    // Handle numbers
    if (typeof value === 'number') {
      // Excel sometimes gives NaN for empty numeric cells
      normalized[key] = isNaN(value) ? null : value;
      continue;
    }

    // Handle booleans
    if (typeof value === 'boolean') {
      normalized[key] = value;
      continue;
    }

    // Handle dates (Excel date serial numbers)
    if (value instanceof Date) {
      normalized[key] = isNaN(value.getTime()) ? null : value;
      continue;
    }

    // Default: keep as-is
    normalized[key] = value;
  }

  return normalized;
};

/**
 * Validate Excel row against schema
 * @param {Object} row - Excel row data
 * @param {Object} schema - Validation schema
 * @param {number} rowNumber - Row number for error messages
 * @returns {Object} { valid: boolean, errors: string[] }
 */
export const validateExcelRow = (row, schema, rowNumber) => {
  const errors = [];

  for (const [field, rules] of Object.entries(schema)) {
    const value = row[field];
    const error = validateField(rules.type || 'text', value, {
      required: rules.required,
      label: rules.label || field,
      maxLength: rules.maxLength,
      minLength: rules.minLength
    });

    if (error) {
      errors.push(`صف ${rowNumber}: ${error}`);
    }
  }

  return {
    valid: errors.length === 0,
    errors
  };
};

// ═══════════════════════════════════════════════════════════════════════════
// ENTITY-SPECIFIC VALIDATION SCHEMAS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Member validation schema
 * Used by: UnifiedMemberCreate, UnifiedMemberEdit, Excel Import
 */
export const memberValidationSchema = {
  fullName: {
    type: 'text',
    required: true,
    label: 'الاسم الكامل',
    maxLength: 255
  },
  nationalNumber: {
    type: 'nationalNumber',
    required: false,
    label: 'رقم الهوية'
  },
  email: {
    type: 'email',
    required: false,
    label: 'البريد الإلكتروني'
  },
  phone: {
    type: 'phone',
    required: false,
    label: 'رقم الهاتف'
  },
  birthDate: {
    type: 'pastDate',
    required: true,
    label: 'تاريخ الميلاد'
  },
  gender: {
    type: 'text',
    required: true,
    label: 'الجنس'
  }
};

/**
 * Provider validation schema
 * Used by: ProviderCreate, ProviderEdit, Excel Import
 */
export const providerValidationSchema = {
  name: {
    type: 'text',
    required: true,
    label: 'اسم مقدم الخدمة',
    maxLength: 255
  },
  licenseNumber: {
    type: 'text',
    required: true,
    label: 'رقم الترخيص'
  },
  providerType: {
    type: 'text',
    required: true,
    label: 'نوع المزود'
  },
  email: {
    type: 'email',
    required: false,
    label: 'البريد الإلكتروني'
  },
  phone: {
    type: 'phone',
    required: false,
    label: 'رقم الهاتف'
  },
  defaultDiscountRate: {
    type: 'percentage',
    required: false,
    label: 'نسبة الخصم'
  }
};

/**
 * Medical Service validation schema
 * Used by: MedicalServiceCreate, MedicalServiceEdit, Excel Import
 */
export const medicalServiceValidationSchema = {
  code: {
    type: 'code',
    required: true,
    label: 'رمز الخدمة',
    maxLength: 50
  },
  name: {
    type: 'text',
    required: true,
    label: 'اسم الخدمة',
    maxLength: 255
  },
  categoryId: {
    type: 'text',
    required: true,
    label: 'التصنيف'
  },
  basePrice: {
    type: 'nonNegativeNumber',
    required: false,
    label: 'السعر الأساسي'
  }
};

/**
 * Claim validation schema
 * Used by: ClaimCreate, ClaimEdit
 */
export const claimValidationSchema = {
  memberId: {
    type: 'text',
    required: true,
    label: 'العضو'
  },
  providerId: {
    type: 'text',
    required: true,
    label: 'مقدم الخدمة'
  },
  serviceDate: {
    type: 'pastDate',
    required: true,
    label: 'تاريخ الخدمة'
  },
  totalAmount: {
    type: 'positiveNumber',
    required: true,
    label: 'المبلغ الإجمالي'
  }
};

// ═══════════════════════════════════════════════════════════════════════════
// EXPORTS
// ═══════════════════════════════════════════════════════════════════════════

export default {
  // Normalization
  normalizePayload,
  normalizeValue,
  normalizeExcelRow,

  // Validators
  validators,
  validateField,
  validateForm,
  validateExcelRow,
  hasErrors,

  // Schemas
  memberValidationSchema,
  providerValidationSchema,
  medicalServiceValidationSchema,
  claimValidationSchema
};
