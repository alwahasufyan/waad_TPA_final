import { Button, Stack, Typography, Paper, Box, IconButton, TextField, LinearProgress, Alert } from '@mui/material';
import { AttachFile as AttachmentIcon, CloudUpload as UploadIcon, Delete as DeleteIcon } from '@mui/icons-material';
import { FormSection, SectionHeader } from './ClaimSectionPrimitives';
import { LABELS, MAX_UPLOAD_SIZE_MB, FILE_ACCEPT_ATTR } from '../constants';

/**
 * Step 3 (attachments half) — upload/list existing + pending attachments.
 * Extracted verbatim from the pre-Phase-3B monolith's "Row 4" section
 * (attachments portion only — the claim-conversation portion moved to its
 * own ClaimConversationPanel). No validation/upload-flow change.
 */
export function AttachmentsPanel({
  attemptedSubmit,
  hasAttachmentsReady,
  submitting,
  success,
  handleFileSelect,
  existingAttachments,
  handleDeleteExistingAttachment,
  pendingFiles,
  handleFileTypeChange,
  handleRemoveFile,
  uploading,
  uploadProgress
}) {
  return (
    <FormSection>
      <SectionHeader icon={AttachmentIcon} title={LABELS.attachments} subtitle="المستندات الداعمة للمطالبة" color="warning" />

      <Alert severity="info" sx={{ mb: '1.0rem', borderRadius: '0.25rem' }}>
        {LABELS.attachmentHint}
        <br />
        <strong>الامتدادات المسموحة:</strong> PDF, JPG, JPEG, PNG, GIF, DOC, DOCX — <strong>الحد الأقصى:</strong> {MAX_UPLOAD_SIZE_MB}MB لكل
        ملف.
      </Alert>

      <Button
        variant="outlined"
        component="label"
        fullWidth
        startIcon={<UploadIcon />}
        disabled={submitting || success}
        sx={{
          height: '5.0rem',
          borderStyle: 'dashed',
          borderWidth: 2,
          borderColor: attemptedSubmit && !hasAttachmentsReady ? 'error.main' : undefined,
          borderRadius: '0.25rem',
          mb: '1.0rem'
        }}
      >
        {LABELS.selectFiles}
        <input type="file" hidden multiple accept={FILE_ACCEPT_ATTR} onChange={handleFileSelect} />
      </Button>

      {existingAttachments.length > 0 && (
        <Stack spacing={1} sx={{ mb: '1.0rem' }}>
          <Typography variant="subtitle2" fontWeight={600} color="text.secondary">
            المرفقات المحفوظة في المسودة ({existingAttachments.length})
          </Typography>
          {existingAttachments.map((item) => (
            <Paper
              key={item.id}
              variant="outlined"
              sx={{ p: '0.75rem', borderRadius: '0.25rem', bgcolor: (theme) => `${theme.palette.success.main}0A` }}
            >
              <Stack direction="row" alignItems="center" spacing={2}>
                <AttachmentIcon color="success" />
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography variant="body2" noWrap fontWeight={500}>
                    {item.fileName || 'مرفق'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {item.attachmentType || item.fileType || 'OTHER'}
                  </Typography>
                </Box>
                <IconButton
                  size="small"
                  color="error"
                  onClick={() => handleDeleteExistingAttachment(item.id)}
                  disabled={submitting || success}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Stack>
            </Paper>
          ))}
        </Stack>
      )}

      {pendingFiles.length > 0 && (
        <Stack spacing={1} sx={{ mb: '1.0rem' }}>
          {pendingFiles.map((item, index) => (
            <Paper key={index} variant="outlined" sx={{ p: '0.75rem', borderRadius: '0.25rem' }}>
              <Stack direction="row" alignItems="center" spacing={2}>
                <AttachmentIcon color="action" />
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography variant="body2" noWrap fontWeight={500}>
                    {item.file.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {(item.file.size / 1024).toFixed(1)} KB
                  </Typography>
                </Box>
                <TextField
                  select
                  size="small"
                  value={item.type}
                  onChange={(e) => handleFileTypeChange(index, e.target.value)}
                  SelectProps={{ native: true }}
                  sx={{ width: '8.125rem' }}
                >
                  <option value="MEDICAL_REPORT">تقرير طبي</option>
                  <option value="INVOICE">فاتورة</option>
                  <option value="LAB_RESULT">نتائج مختبر</option>
                  <option value="XRAY">أشعة</option>
                  <option value="PRESCRIPTION">وصفة طبية</option>
                  <option value="OTHER">أخرى</option>
                </TextField>
                <IconButton size="small" color="error" onClick={() => handleRemoveFile(index)} disabled={submitting || success}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Stack>
            </Paper>
          ))}
          <Typography variant="caption" color="text.secondary" textAlign="center">
            📎 سيتم رفع {pendingFiles.length} ملف عند تقديم المطالبة
          </Typography>
        </Stack>
      )}

      {uploading && (
        <Box sx={{ mt: '1.0rem' }}>
          <LinearProgress variant="determinate" value={uploadProgress} sx={{ borderRadius: 1 }} />
          <Typography variant="caption" color="text.secondary" textAlign="center" display="block" mt={0.5}>
            {LABELS.uploadingFiles} {uploadProgress}%
          </Typography>
        </Box>
      )}
    </FormSection>
  );
}
