import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Tooltip,
  Typography
} from '@mui/material';
import {
  Visibility as PreviewIcon,
  Download as DownloadIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
  Close as CloseIcon,
  PictureAsPdf as PdfIcon,
  Image as ImageIcon,
  Description as WordIcon,
  TableChart as ExcelIcon,
  InsertDriveFile as FileIcon,
  FolderOpen as EmptyIcon,
  OpenInNew as OpenExternalIcon
} from '@mui/icons-material';

import { downloadClaimAttachment } from 'services/api/files.service';
import { getAttachmentKind, isInlinePreviewable, formatFileSize } from './attachmentTypeUtils';

const KIND_ICON = {
  pdf: <PdfIcon color="error" fontSize="small" />,
  image: <ImageIcon color="info" fontSize="small" />,
  word: <WordIcon color="primary" fontSize="small" />,
  excel: <ExcelIcon color="success" fontSize="small" />,
  other: <FileIcon color="disabled" fontSize="small" />
};

const KIND_LABEL = {
  pdf: 'PDF',
  image: 'صورة',
  word: 'Word',
  excel: 'Excel',
  other: 'ملف'
};

const SESSION_EXPIRED_MESSAGE = 'انتهت الجلسة، الرجاء تسجيل الدخول من جديد لمعاينة أو تنزيل المستندات.';

const errorMessageFor = (error) => {
  if (error?.response?.status === 401) return SESSION_EXPIRED_MESSAGE;
  if (error?.response?.status === 404) return 'تعذر العثور على الملف — قد يكون قد حُذف من التخزين.';
  return 'تعذر تحميل الملف. يرجى المحاولة مرة أخرى.';
};

const triggerBlobDownload = (blob, fileName) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName || 'attachment';
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
};

/**
 * DOCUMENTS-INTEGRITY-1: one shared attachment list + preview component,
 * used identically by the Provider Portal (already-submitted claim
 * attachments) and the Claims Review workspace — replacing several
 * independent, duplicated, drifting implementations.
 *
 * - Images (JPEG/PNG): inline preview in a dialog (<img>).
 * - PDF: opened in a new browser tab (native browser PDF viewer), NOT an
 *   in-app <iframe> — the app's CSP (default-src 'self', no frame-src)
 *   blocks framing a blob: URL, which silently broke the earlier iframe-based
 *   preview. A full top-level tab navigation is not subject to that
 *   restriction, and it gives the user the browser's own print/zoom/download
 *   controls for free (no in-app print button needed).
 * - Word/Excel: never rendered in-browser (no reliable, accurate renderer —
 *   wrong tradeoff for clinical/financial documents) — clicking always
 *   downloads the real file so it opens in the user's own Word/Excel.
 * - All fetches go through authenticated axios + blob (never a raw
 *   <img src>/<iframe src> pointed at the API) so a 401 shows a clear
 *   "session expired" message instead of a silently broken preview.
 */
const SharedAttachmentViewer = ({ attachments, claimId, onDelete, onRefresh, emptyMessage }) => {
  const safeAttachments = Array.isArray(attachments) ? attachments : [];

  const [preview, setPreview] = useState(null); // { attachment, kind, blobUrl } | null (images only)
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState(null);
  const [downloadingId, setDownloadingId] = useState(null);
  const blobUrlRef = useRef(null);

  const revokeCurrentBlobUrl = useCallback(() => {
    if (blobUrlRef.current) {
      URL.revokeObjectURL(blobUrlRef.current);
      blobUrlRef.current = null;
    }
  }, []);

  useEffect(() => () => revokeCurrentBlobUrl(), [revokeCurrentBlobUrl]);

  const handleDownload = useCallback(
    async (attachment) => {
      setDownloadingId(attachment.id);
      try {
        const blob = await downloadClaimAttachment(claimId, attachment.id);
        triggerBlobDownload(blob, attachment.fileName);
      } catch (error) {
        setPreviewError(errorMessageFor(error));
      } finally {
        setDownloadingId(null);
      }
    },
    [claimId]
  );

  const openPdfInNewTab = useCallback(
    async (attachment) => {
      setPreviewError(null);
      setDownloadingId(attachment.id);
      try {
        const blob = await downloadClaimAttachment(claimId, attachment.id, { inline: true });
        const blobUrl = URL.createObjectURL(blob);
        const newTab = window.open(blobUrl, '_blank');
        if (!newTab) {
          // Popup blocked — fall back to a real download so the user still gets the file.
          triggerBlobDownload(blob, attachment.fileName);
          URL.revokeObjectURL(blobUrl);
        } else {
          // Revoke once the new tab has had time to load the blob.
          setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
        }
      } catch (error) {
        setPreviewError(errorMessageFor(error));
      } finally {
        setDownloadingId(null);
      }
    },
    [claimId]
  );

  const openPreview = useCallback(
    async (attachment) => {
      const kind = getAttachmentKind(attachment);

      if (kind === 'pdf') {
        openPdfInNewTab(attachment);
        return;
      }

      if (!isInlinePreviewable(kind)) {
        // Word/Excel — never open a preview dialog, just download the real file.
        handleDownload(attachment);
        return;
      }

      // Images only from here on.
      setPreviewError(null);
      setPreviewLoading(true);
      setPreview({ attachment, kind, blobUrl: null });

      try {
        const blob = await downloadClaimAttachment(claimId, attachment.id, { inline: true });
        revokeCurrentBlobUrl();
        const blobUrl = URL.createObjectURL(blob);
        blobUrlRef.current = blobUrl;
        setPreview({ attachment, kind, blobUrl });
      } catch (error) {
        setPreviewError(errorMessageFor(error));
      } finally {
        setPreviewLoading(false);
      }
    },
    [claimId, handleDownload, revokeCurrentBlobUrl, openPdfInNewTab]
  );

  const closePreview = useCallback(() => {
    revokeCurrentBlobUrl();
    setPreview(null);
    setPreviewError(null);
  }, [revokeCurrentBlobUrl]);

  return (
    <Box>
      <Stack direction="row" justifyContent="flex-end" sx={{ mb: safeAttachments.length ? 0.5 : 0 }}>
        {onRefresh && (
          <Tooltip title="تحديث">
            <IconButton size="small" onClick={onRefresh}>
              <RefreshIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Stack>

      {safeAttachments.length === 0 ? (
        <Box
          sx={{
            minHeight: '9.5rem',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '0.5rem',
            color: 'text.secondary'
          }}
        >
          <EmptyIcon sx={{ fontSize: '2.25rem', color: 'text.disabled' }} />
          <Typography variant="body2" color="text.secondary">
            {emptyMessage || 'لا توجد مستندات مرفقة'}
          </Typography>
        </Box>
      ) : (
        <List dense disablePadding sx={{ maxHeight: '15.625rem', overflowY: 'auto' }}>
          {safeAttachments.map((attachment) => {
            const kind = getAttachmentKind(attachment);
            const size = formatFileSize(attachment.fileSize);
            const previewable = isInlinePreviewable(kind);
            return (
              <ListItemButton
                key={attachment.id}
                onClick={() => openPreview(attachment)}
                sx={{ borderRadius: '0.375rem', mb: 0.5, border: 1, borderColor: 'divider' }}
              >
                <ListItemIcon sx={{ minWidth: '2rem' }}>{KIND_ICON[kind]}</ListItemIcon>
                <ListItemText
                  primary={attachment.fileName || 'ملف'}
                  secondary={[KIND_LABEL[kind], size].filter(Boolean).join(' · ')}
                  primaryTypographyProps={{ variant: 'body2', fontWeight: 600, noWrap: true }}
                  secondaryTypographyProps={{ variant: 'caption' }}
                />
                <Tooltip title={kind === 'pdf' ? 'فتح في تبويب جديد' : previewable ? 'معاينة' : 'تنزيل وفتح خارجياً'}>
                  <span>
                    <IconButton
                      size="small"
                      disabled={downloadingId === attachment.id}
                      onClick={(e) => {
                        e.stopPropagation();
                        openPreview(attachment);
                      }}
                    >
                      {downloadingId === attachment.id ? (
                        <CircularProgress size={16} />
                      ) : previewable ? (
                        <PreviewIcon fontSize="small" />
                      ) : (
                        <OpenExternalIcon fontSize="small" />
                      )}
                    </IconButton>
                  </span>
                </Tooltip>
                <Tooltip title="تحميل">
                  <IconButton
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDownload(attachment);
                    }}
                  >
                    <DownloadIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                {onDelete && (
                  <Tooltip title="حذف">
                    <IconButton
                      size="small"
                      color="error"
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(attachment);
                      }}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </ListItemButton>
            );
          })}
        </List>
      )}

      {previewError && !preview && (
        <Alert severity="error" sx={{ mt: 1 }} onClose={() => setPreviewError(null)}>
          {previewError}
        </Alert>
      )}

      <Dialog open={!!preview} onClose={closePreview} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="subtitle1" fontWeight={700} noWrap>
            {preview?.attachment?.fileName}
          </Typography>
          <IconButton size="small" onClick={closePreview}>
            <CloseIcon fontSize="small" />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers sx={{ minHeight: '25rem', display: 'flex', alignItems: 'center', justifyContent: 'center', p: 0 }}>
          {previewLoading && <CircularProgress />}
          {!previewLoading && previewError && (
            <Alert severity="error" sx={{ m: 2, width: '100%' }}>
              {previewError}
            </Alert>
          )}
          {!previewLoading && !previewError && preview?.blobUrl && preview.kind === 'image' && (
            <Box
              component="img"
              src={preview.blobUrl}
              alt={preview.attachment?.fileName}
              sx={{ maxWidth: '100%', maxHeight: '37.5rem', objectFit: 'contain' }}
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button startIcon={<DownloadIcon />} onClick={() => preview?.attachment && handleDownload(preview.attachment)}>
            تحميل
          </Button>
          <Button onClick={closePreview}>إغلاق</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SharedAttachmentViewer;
