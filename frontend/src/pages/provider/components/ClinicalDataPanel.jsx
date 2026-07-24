import { Grid, Stack, Typography, TextField } from '@mui/material';
import { Description as DiagnosisIcon, Notes as NotesIcon } from '@mui/icons-material';
import { FormSection, SectionHeader } from './ClaimSectionPrimitives';
import { LABELS } from '../constants';

/**
 * Step 2 — البيانات السريرية (diagnosis only).
 *
 * CLAIM-REVIEW-FOLLOWUP-1: the pre-authorization link dropdown that used to
 * live here was removed — pre-authorizations are now handled exclusively on
 * the dedicated Pre-Authorization page. A service that requires PA is
 * blocked at selection time in the service picker (see
 * useProviderClaimSubmission.handleServiceSelect), so a normal claim can
 * never legitimately need one attached here.
 */
export function ClinicalDataPanel({ formData, handleFormChange, attemptedSubmit, submitting, success }) {
  return (
    <FormSection>
      <SectionHeader icon={DiagnosisIcon} title="البيانات السريرية" subtitle="التشخيص والملاحظات الطبية" color="info" />

      <Grid container spacing={3}>
        <Grid size={12}>
          <Typography variant="subtitle2" fontWeight={700} sx={{ mb: '0.75rem' }}>
            {LABELS.diagnosis}
          </Typography>
          <Stack spacing={2.5}>
            <TextField
              fullWidth
              label={LABELS.diagnosisCode}
              value={formData.diagnosisCode}
              onChange={handleFormChange('diagnosisCode')}
              disabled={submitting || success}
              required
              error={attemptedSubmit && !formData.diagnosisCode?.trim()}
              helperText={
                attemptedSubmit && !formData.diagnosisCode?.trim() ? LABELS.diagnosisCodeRequired : 'أدخل رمز التشخيص حسب تصنيف ICD-10'
              }
              placeholder="مثال: J06.9"
              InputProps={{
                sx: { fontFamily: 'monospace', fontWeight: 600 }
              }}
            />
            <TextField
              fullWidth
              label={LABELS.diagnosisDescription}
              value={formData.diagnosisDescription}
              onChange={handleFormChange('diagnosisDescription')}
              disabled={submitting || success}
              placeholder="وصف التشخيص الطبي..."
              multiline
              rows={2}
            />
            <TextField
              fullWidth
              multiline
              rows={2}
              label={LABELS.notes}
              value={formData.notes}
              onChange={handleFormChange('notes')}
              disabled={submitting || success}
              placeholder="أدخل أي ملاحظات طبية إضافية..."
              InputProps={{
                startAdornment: <NotesIcon color="action" sx={{ mr: 1, mt: 1, alignSelf: 'flex-start' }} />
              }}
            />
          </Stack>
        </Grid>
      </Grid>
    </FormSection>
  );
}
