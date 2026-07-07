/**
 * Locales Index - نظام وعد
 * Phase D1.5 - Simple Translation System
 */

import ar from './ar';
import en from './en';

// Available locales
export const locales = {
  ar,
  en
};

// Default locale
export const defaultLocale = 'ar';

// Supported locales list
export const supportedLocales = [
  { code: 'ar', name: 'العربية', direction: 'rtl' },
  { code: 'en', name: 'English', direction: 'ltr' }
];

export { ar, en };
export default locales;
