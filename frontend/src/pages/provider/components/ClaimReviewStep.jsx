import { Stack, Typography, Table, TableBody, TableRow, TableCell, Divider, Chip, Alert, Box } from '@mui/material';
import { formatCurrency } from 'utils/currency-formatter';
import { FormSection, SectionHeader } from './ClaimSectionPrimitives';
import { FactCheck as ReviewIcon } from '@mui/icons-material';

/**
 * Step 5 — المراجعة والإرسال. A read-only recap of everything entered in the
 * previous steps (diagnosis, services, attachments), so the clerk can confirm
 * before submitting instead of scrolling back up through a long page. Reads
 * the same state the other steps already populate — no new calculation, no
 * new API call.
 */
export function ClaimReviewStep({
  formData,
  claimLines,
  calculateLineTotal,
  totalClaimAmount,
  pendingFiles,
  existingAttachments,
  isFormValid
}) {
  const attachmentsCount = pendingFiles.length + existingAttachments.length;

  return (
    <FormSection highlighted>
      <SectionHeader
        icon={ReviewIcon}
        title="مراجعة المطالبة قبل الإرسال"
        subtitle="تحقق من البيانات قبل التقديم النهائي"
        color="primary"
      />
      <Divider sx={{ mb: '1.25rem' }} />

      {!isFormValid && (
        <Alert severity="warning" sx={{ mb: '1.0rem', borderRadius: '0.25rem' }}>
          البيانات غير مكتملة بعد — راجع خطوة "الخدمات الطبية" قبل التقديم النهائي.
        </Alert>
      )}

      <Stack spacing={2}>
        <Box>
          <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 0.5 }}>
            التشخيص
          </Typography>
          <Typography variant="body2" color={formData.diagnosisCode ? 'text.primary' : 'error.main'}>
            {formData.diagnosisCode || 'لم يُدخل رمز تشخيص بعد'}
          </Typography>
          {formData.diagnosisDescription && (
            <Typography variant="caption" color="text.secondary">
              {formData.diagnosisDescription}
            </Typography>
          )}
        </Box>

        <Box>
          <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 0.5 }}>
            الخدمات ({claimLines.length})
          </Typography>
          {claimLines.length === 0 ? (
            <Typography variant="body2" color="error.main">
              لا توجد خدمات مضافة
            </Typography>
          ) : (
            <Table size="small">
              <TableBody>
                {claimLines.map((line) => (
                  <TableRow key={line.id}>
                    <TableCell sx={{ pl: 0 }}>{line.serviceName || '—'}</TableCell>
                    <TableCell align="center">×{line.quantity || 1}</TableCell>
                    <TableCell align="left" sx={{ pr: 0 }}>
                      {formatCurrency(calculateLineTotal(line))}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Box>

        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle1" fontWeight={700}>
            الإجمالي
          </Typography>
          <Typography variant="h6" fontWeight={800} color="primary.main">
            {formatCurrency(totalClaimAmount)}
          </Typography>
        </Box>

        <Box>
          <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 0.5 }}>
            المرفقات
          </Typography>
          <Chip
            size="small"
            label={attachmentsCount > 0 ? `${attachmentsCount} مرفق` : 'بدون مرفقات (اختياري)'}
            color={attachmentsCount > 0 ? 'success' : 'default'}
          />
        </Box>
      </Stack>
    </FormSection>
  );
}
