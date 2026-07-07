/**
 * API Field Validators
 * Common validation functions for API data
 */

/**
 * Validate Claim Number format
 * Expected format: CLM-YYYYMMDD-XXXX
 * Example: CLM-20260101-0001
 *
 * @param {string} claimNumber - Claim number to validate
 * @returns {boolean} True if valid
 * @throws {Error} If invalid format
 */
export const validateClaimNumber = (claimNumber) => {
  if (!claimNumber || typeof claimNumber !== 'string') {
    throw new Error('رقم المطالبة مطلوب');
  }

  const pattern = /^CLM-\d{8}-\d{4}$/;
  if (!pattern.test(claimNumber)) {
    throw new Error('رقم المطالبة غير صحيح. الصيغة المطلوبة: CLM-YYYYMMDD-XXXX');
  }

  // Validate date part (YYYYMMDD)
  const datePart = claimNumber.substring(4, 12);
  const year = parseInt(datePart.substring(0, 4), 10);
  const month = parseInt(datePart.substring(4, 6), 10);
  const day = parseInt(datePart.substring(6, 8), 10);

  if (year < 2020 || year > 2100) {
    throw new Error('سنة المطالبة غير صحيحة');
  }
  if (month < 1 || month > 12) {
    throw new Error('شهر المطالبة غير صحيح');
  }
  if (day < 1 || day > 31) {
    throw new Error('يوم المطالبة غير صحيح');
  }

  return true;
};

/**
 * Validate Policy Code format
 * Expected format: POL-YYYY-XXX
 * Example: POL-2025-001
 *
 * @param {string} policyCode - Policy code to validate
 * @returns {boolean} True if valid
 * @throws {Error} If invalid format
 */
export const validatePolicyCode = (policyCode) => {
  if (!policyCode || typeof policyCode !== 'string') {
    throw new Error('رمز السياسة مطلوب');
  }

  const pattern = /^POL-\d{4}-\d{3}$/;
  if (!pattern.test(policyCode)) {
    throw new Error('رمز السياسة غير صحيح. الصيغة المطلوبة: POL-YYYY-XXX');
  }

  const year = parseInt(policyCode.substring(4, 8), 10);
  if (year < 2020 || year > 2100) {
    throw new Error('سنة السياسة غير صحيحة');
  }

  return true;
};

/**
 * Validate Employer Code format
 * Expected format: EMP-XX or custom format
 *
 * @param {string} employerCode - Employer code to validate
 * @returns {boolean} True if valid
 */
export const validateEmployerCode = (employerCode) => {
  if (!employerCode || typeof employerCode !== 'string') {
    return false;
  }

  // Allow flexible format (at least 3 characters)
  return employerCode.trim().length >= 3;
};

/**
 * Validate Card Number format
 * Expected format: WAAD|MEMBER|{TIMESTAMP}
 * Example: WAAD|MEMBER|1735234859123
 *
 * @param {string} cardNumber - Card number to validate
 * @returns {boolean} True if valid
 */
export const validateCardNumber = (cardNumber) => {
  if (!cardNumber || typeof cardNumber !== 'string') {
    return false;
  }

  const pattern = /^WAAD\|MEMBER\|\d+$/;
  return pattern.test(cardNumber);
};

/**
 * Validate date is not in the future
 *
 * @param {string|Date} date - Date to validate
 * @param {string} fieldName - Field name for error message
 * @returns {boolean} True if valid
 * @throws {Error} If date is in the future
 */
export const validateNotFutureDate = (date, fieldName = 'التاريخ') => {
  const dateObj = typeof date === 'string' ? new Date(date) : date;
  const now = new Date();

  if (dateObj > now) {
    throw new Error(`${fieldName} لا يمكن أن يكون في المستقبل`);
  }

  return true;
};

/**
 * Validate date range (start < end)
 *
 * @param {string|Date} startDate - Start date
 * @param {string|Date} endDate - End date
 * @param {string} startFieldName - Start field name for error
 * @param {string} endFieldName - End field name for error
 * @returns {boolean} True if valid
 * @throws {Error} If range is invalid
 */
export const validateDateRange = (startDate, endDate, startFieldName = 'تاريخ البداية', endFieldName = 'تاريخ النهاية') => {
  const start = typeof startDate === 'string' ? new Date(startDate) : startDate;
  const end = typeof endDate === 'string' ? new Date(endDate) : endDate;

  if (start >= end) {
    throw new Error(`${startFieldName} يجب أن يكون قبل ${endFieldName}`);
  }

  return true;
};

/**
 * Validate percentage (0-100)
 *
 * @param {number} percentage - Percentage value
 * @param {string} fieldName - Field name for error message
 * @returns {boolean} True if valid
 * @throws {Error} If invalid
 */
export const validatePercentage = (percentage, fieldName = 'النسبة المئوية') => {
  if (typeof percentage !== 'number' || isNaN(percentage)) {
    throw new Error(`${fieldName} يجب أن تكون رقماً`);
  }

  if (percentage < 0 || percentage > 100) {
    throw new Error(`${fieldName} يجب أن تكون بين 0 و 100`);
  }

  return true;
};

/**
 * Validate amount (positive number)
 *
 * @param {number} amount - Amount value
 * @param {string} fieldName - Field name for error message
 * @returns {boolean} True if valid
 * @throws {Error} If invalid
 */
export const validateAmount = (amount, fieldName = 'المبلغ') => {
  if (typeof amount !== 'number' || isNaN(amount)) {
    throw new Error(`${fieldName} يجب أن يكون رقماً`);
  }

  if (amount < 0) {
    throw new Error(`${fieldName} يجب أن يكون موجباً`);
  }

  return true;
};

/**
 * Validate email format
 *
 * @param {string} email - Email to validate
 * @returns {boolean} True if valid
 * @throws {Error} If invalid
 */
export const validateEmail = (email) => {
  if (!email || typeof email !== 'string') {
    return true; // Optional field
  }

  const pattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!pattern.test(email)) {
    throw new Error('البريد الإلكتروني غير صحيح');
  }

  return true;
};

/**
 * Validate phone number (Libyan format)
 *
 * @param {string} phone - Phone number to validate
 * @returns {boolean} True if valid
 * @throws {Error} If invalid
 */
export const validatePhone = (phone) => {
  if (!phone || typeof phone !== 'string') {
    return true; // Optional field
  }

  // Libyan phone: starts with +218 or 0, followed by 9-10 digits
  const pattern = /^(\+218|0)\d{9,10}$/;
  if (!pattern.test(phone.replace(/[\s-]/g, ''))) {
    throw new Error('رقم الهاتف غير صحيح (مثال: +218912345678)');
  }

  return true;
};

/**
 * Validate National Number (optional but unique format)
 * Replaces civilId - unified field name per API Contract 2026.1
 *
 * @param {string} nationalNumber - National Number to validate
 * @returns {boolean} True if valid
 */
export const validateNationalNumber = (nationalNumber) => {
  if (!nationalNumber || typeof nationalNumber !== 'string') {
    return true; // Optional field
  }

  // Libyan National ID: 12 digits
  const pattern = /^\d{12}$/;
  if (!pattern.test(nationalNumber)) {
    throw new Error('الرقم الوطني يجب أن يكون 12 رقماً');
  }

  return true;
};

// ==================== SAFE ACCESS UTILITIES ====================

/**
 * Safely access nested object properties
 * @param {Object} obj - Source object
 * @param {string} path - Dot-separated path (e.g., 'member.employer.name')
 * @param {*} defaultValue - Default value if path not found
 * @returns {*} Value at path or defaultValue
 */
export const safeGet = (obj, path, defaultValue = null) => {
  if (!obj || !path) return defaultValue;
  return path.split('.').reduce((acc, key) => acc?.[key], obj) ?? defaultValue;
};

/**
 * Safely convert response to array
 * @param {*} data - Data to convert
 * @returns {Array} Array (empty if invalid input)
 */
export const safeArray = (data) => (Array.isArray(data) ? data : []);

/**
 * Safely unwrap API response (handles ApiResponse wrapper)
 * @param {Object} response - Axios response
 * @returns {*} Unwrapped data or null
 */
export const safeUnwrap = (response) => response?.data?.data ?? response?.data ?? null;

/**
 * Safely parse numeric value
 * @param {*} value - Value to parse
 * @param {number} defaultValue - Default if invalid
 * @returns {number} Parsed number or default
 */
export const safeNumber = (value, defaultValue = 0) => {
  const num = parseFloat(value);
  return isNaN(num) ? defaultValue : num;
};

export default {
  validateClaimNumber,
  validatePolicyCode,
  validateEmployerCode,
  validateCardNumber,
  validateNotFutureDate,
  validateDateRange,
  validatePercentage,
  validateAmount,
  validateEmail,
  validatePhone,
  validateNationalNumber,
  safeGet,
  safeArray,
  safeUnwrap,
  safeNumber
};
