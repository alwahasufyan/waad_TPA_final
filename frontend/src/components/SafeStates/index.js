/**
 * SafeStates - Component Index
 * ============================
 *
 * Centralized exports for safe state handling components
 *
 * PRODUCTION STABILIZATION (2026-02-03):
 * - ApiErrorHandler: Handles API errors gracefully
 * - SafeDataWrapper: Prevents crashes from null/undefined data
 * - PageErrorBoundary: Catches runtime errors in pages
 */

export { ApiErrorHandler, ApiErrorDisplay, getErrorConfig, ERROR_CONFIGS } from './ApiErrorHandler';
export { SafeDataWrapper, LoadingState, EmptyState } from './SafeDataWrapper';
export { default as PageErrorBoundary } from './PageErrorBoundary';

// Default export
export { ApiErrorHandler as default } from './ApiErrorHandler';
