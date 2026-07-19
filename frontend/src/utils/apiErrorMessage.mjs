/**
 * Resolves the user-facing message from a backend ApiError payload, always preferring the
 * Arabic message over the English one so Arabic-speaking users never see raw English/internal
 * business text. Centralized here so every provider form uses the same precedence instead of
 * each screen re-implementing (and risking getting wrong) its own fallback chain.
 */
export const resolveApiErrorMessage = (errorData, fallback) => {
  if (!errorData) return fallback;
  if (typeof errorData === 'string') return errorData;
  if (errorData.messageAr) return errorData.messageAr;
  if (errorData.message) return errorData.message;
  if (errorData.error) return errorData.error;
  return fallback;
};
