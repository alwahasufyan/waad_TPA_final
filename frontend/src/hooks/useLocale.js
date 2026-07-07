/**
 * useLocale Hook - نظام وعد
 * Phase D1.5 - Simple Translation System
 *
 * Provides t() function for translations without react-intl dependency.
 * Arabic is default, uses localStorage for persistence.
 */

import { useCallback, useMemo } from 'react';
import useConfig from './useConfig';
import { locales, defaultLocale } from '../locales';

/**
 * Get nested value from object using dot notation
 * @param {Object} obj - The object to traverse
 * @param {string} path - Dot notation path (e.g., 'nav.dashboard')
 * @returns {string|undefined} The value at the path
 */
const getNestedValue = (obj, path) => {
  if (!obj || !path) return undefined;

  return path.split('.').reduce((acc, part) => {
    if (acc && typeof acc === 'object' && part in acc) {
      return acc[part];
    }
    return undefined;
  }, obj);
};

/**
 * Custom hook for translations
 * @returns {Object} Translation utilities
 */
const useLocale = () => {
  const { state } = useConfig();
  const i18n = state?.i18n;

  // Get current locale code (default to Arabic)
  const locale = useMemo(() => {
    return i18n && locales[i18n] ? i18n : defaultLocale;
  }, [i18n]);

  // Get current translations object
  const translations = useMemo(() => {
    return locales[locale] || locales[defaultLocale];
  }, [locale]);

  // Translation function
  const t = useCallback(
    (key, params = {}) => {
      if (!key) return '';

      // Get translation from current locale
      let translation = getNestedValue(translations, key);

      // Fallback to Arabic if not found in current locale
      if (translation === undefined && locale !== 'ar') {
        translation = getNestedValue(locales.ar, key);
      }

      // Fallback to English if not in Arabic either
      if (translation === undefined && locale !== 'en') {
        translation = getNestedValue(locales.en, key);
      }

      // If still not found, return the key
      if (translation === undefined) {
        console.warn(`Translation missing for key: ${key}`);
        return key;
      }

      // Handle parameters (e.g., {year})
      if (params && typeof translation === 'string') {
        Object.entries(params).forEach(([param, value]) => {
          translation = translation.replace(new RegExp(`\\{${param}\\}`, 'g'), value);
        });
      }

      return translation;
    },
    [translations, locale]
  );

  // Check if current locale is RTL
  const isRTL = useMemo(() => {
    return locale === 'ar';
  }, [locale]);

  // Get text direction
  const direction = useMemo(() => {
    return isRTL ? 'rtl' : 'ltr';
  }, [isRTL]);

  return {
    t,
    locale,
    isRTL,
    direction,
    translations
  };
};

/**
 * Standalone translation function for use outside React components
 * Uses default Arabic locale
 */
export const translate = (key, params = {}) => {
  const translations = locales[defaultLocale];
  let translation = getNestedValue(translations, key);

  if (translation === undefined) {
    translation = getNestedValue(locales.en, key);
  }

  if (translation === undefined) {
    return key;
  }

  if (params && typeof translation === 'string') {
    Object.entries(params).forEach(([param, value]) => {
      translation = translation.replace(new RegExp(`\\{${param}\\}`, 'g'), value);
    });
  }

  return translation;
};

export default useLocale;
