/**
 * DOCUMENTS-INTEGRITY-1: single, shared file-type detection used by every
 * attachment viewer in the app (Provider Portal + Claims Review), instead of
 * each screen re-implementing its own mime/extension guessing (previously
 * duplicated — and drifting — across UnifiedAttachmentViewer, DocumentPreview,
 * and AttachmentsPanel).
 *
 * Detection is mime-first, filename-extension fallback — matches the actual
 * shape of the backend's ClaimAttachmentDto (`fileType` holds the real MIME
 * string; there is no separate `mimeType` field anywhere in the API despite
 * some older frontend code referencing one).
 */

const IMAGE_MIME_TYPES = ['image/jpeg', 'image/jpg', 'image/png'];
const PDF_MIME_TYPE = 'application/pdf';
const WORD_MIME_TYPES = ['application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];
const EXCEL_MIME_TYPES = ['application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'];

/**
 * @param {{fileType?: string, fileName?: string}} attachment
 * @returns {'pdf'|'image'|'word'|'excel'|'other'}
 */
export function getAttachmentKind(attachment) {
  const mime = String(attachment?.fileType || '').toLowerCase();
  const name = String(attachment?.fileName || '').toLowerCase();

  if (mime === PDF_MIME_TYPE || name.endsWith('.pdf')) return 'pdf';
  if (IMAGE_MIME_TYPES.includes(mime) || /\.(jpe?g|png)$/.test(name)) return 'image';
  if (WORD_MIME_TYPES.includes(mime) || /\.docx?$/.test(name)) return 'word';
  if (EXCEL_MIME_TYPES.includes(mime) || /\.xlsx?$/.test(name)) return 'excel';
  return 'other';
}

export function isInlinePreviewable(kind) {
  return kind === 'pdf' || kind === 'image';
}

export function formatFileSize(bytes) {
  if (!bytes || Number.isNaN(Number(bytes))) return null;
  const kb = Number(bytes) / 1024;
  if (kb < 1024) return `${kb.toFixed(0)} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
}
