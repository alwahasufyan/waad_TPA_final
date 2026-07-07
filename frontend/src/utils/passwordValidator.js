/**
 * Password Policy Validator
 * Contract: USER_API_CONTRACT v1.0
 *
 * Requirements:
 * - Minimum 8 characters
 * - At least 1 uppercase letter (A-Z)
 * - At least 1 lowercase letter (a-z)
 * - At least 1 digit (0-9)
 * - At least 1 special character (!@#$%^&*...)
 *
 * Usage:
 * import { validatePassword, getPasswordStrength } from 'utils/passwordValidator';
 *
 * const result = validatePassword('MyPassword123!');
 * if (!result.valid) {
 *   console.error(result.errors);
 * }
 */

export const PASSWORD_POLICY = {
  minLength: 8,
  maxLength: 100,
  requireUppercase: true,
  requireLowercase: true,
  requireDigit: true,
  requireSpecialChar: true
};

/**
 * Validate password against policy
 * @param {string} password - Password to validate
 * @returns {object} { valid: boolean, errors: string[] }
 */
export const validatePassword = (password) => {
  const errors = [];

  if (!password) {
    return { valid: false, errors: ['كلمة المرور مطلوبة'] };
  }

  if (password.length < PASSWORD_POLICY.minLength) {
    errors.push(`كلمة المرور يجب أن تكون ${PASSWORD_POLICY.minLength} أحرف على الأقل`);
  }

  if (password.length > PASSWORD_POLICY.maxLength) {
    errors.push(`كلمة المرور يجب أن لا تتجاوز ${PASSWORD_POLICY.maxLength} حرف`);
  }

  if (PASSWORD_POLICY.requireUppercase && !/[A-Z]/.test(password)) {
    errors.push('كلمة المرور يجب أن تحتوي على حرف كبير واحد على الأقل (A-Z)');
  }

  if (PASSWORD_POLICY.requireLowercase && !/[a-z]/.test(password)) {
    errors.push('كلمة المرور يجب أن تحتوي على حرف صغير واحد على الأقل (a-z)');
  }

  if (PASSWORD_POLICY.requireDigit && !/\d/.test(password)) {
    errors.push('كلمة المرور يجب أن تحتوي على رقم واحد على الأقل (0-9)');
  }

  if (PASSWORD_POLICY.requireSpecialChar && !/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password)) {
    errors.push('كلمة المرور يجب أن تحتوي على رمز خاص واحد على الأقل (!@#$%^&*...)');
  }

  return {
    valid: errors.length === 0,
    errors
  };
};

/**
 * Get password strength indicator
 * @param {string} password - Password to evaluate
 * @returns {object} { strength: number (0-100), label: string, color: string }
 */
export const getPasswordStrength = (password) => {
  if (!password) return { strength: 0, label: 'ضعيف جداً', color: 'error' };

  let strength = 0;

  // Length scoring
  if (password.length >= 8) strength += 20;
  if (password.length >= 12) strength += 10;
  if (password.length >= 16) strength += 10;

  // Character type scoring
  if (/[a-z]/.test(password)) strength += 15;
  if (/[A-Z]/.test(password)) strength += 15;
  if (/\d/.test(password)) strength += 15;
  if (/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password)) strength += 15;

  // Determine label and color
  if (strength <= 40) return { strength, label: 'ضعيف', color: 'error' };
  if (strength <= 60) return { strength, label: 'متوسط', color: 'warning' };
  if (strength <= 80) return { strength, label: 'جيد', color: 'info' };
  return { strength, label: 'قوي جداً', color: 'success' };
};

/**
 * Get password requirements list
 * @returns {Array} List of password requirements in Arabic
 */
export const getPasswordRequirements = () => {
  return [
    `${PASSWORD_POLICY.minLength} أحرف على الأقل`,
    'حرف كبير واحد على الأقل (A-Z)',
    'حرف صغير واحد على الأقل (a-z)',
    'رقم واحد على الأقل (0-9)',
    'رمز خاص واحد على الأقل (!@#$%...)'
  ];
};

/**
 * Check if password meets all requirements (for visual indicators)
 * @param {string} password - Password to check
 * @returns {object} Object with boolean flags for each requirement
 */
export const getPasswordChecklist = (password) => {
  return {
    minLength: password && password.length >= PASSWORD_POLICY.minLength,
    hasUppercase: password && /[A-Z]/.test(password),
    hasLowercase: password && /[a-z]/.test(password),
    hasDigit: password && /\d/.test(password),
    hasSpecialChar: password && /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password)
  };
};

export default {
  PASSWORD_POLICY,
  validatePassword,
  getPasswordStrength,
  getPasswordRequirements,
  getPasswordChecklist
};
