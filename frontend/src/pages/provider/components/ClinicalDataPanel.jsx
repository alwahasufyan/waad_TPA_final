import { Grid, Stack, Typography, TextField, Autocomplete, Box, Alert, CircularProgress } from '@mui/material';
import { Description as DiagnosisIcon, Notes as NotesIcon } from '@mui/icons-material';
import { FormSection, SectionHeader } from './ClaimSectionPrimitives';
import { LABELS } from '../constants';

/**
 * Step 2 — البيانات السريرية (diagnosis + optional pre-authorization link).
 * Extracted verbatim from the pre-Phase-3B monolith's "Row 3" section; no
 * validation/behavior change, only moved into its own component receiving
 * hook state/handlers as props.
 */
export function ClinicalDataPanel({
  formData,
  handleFormChange,
  setFormData,
  attemptedSubmit,
  submitting,
  success,
  availablePreAuths,
  loadingPreAuths
}) {
  return (
    <FormSection>
      <SectionHeader icon={DiagnosisIcon} title="البيانات السريرية" subtitle="التشخيص وربط الموافقة المسبقة" color="info" />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 7 }}>
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

        <Grid size={{ xs: 12, lg: 5 }}>
          <Typography variant="subtitle2" fontWeight={700} sx={{ mb: '0.75rem' }}>
            {LABELS.preAuth}
          </Typography>
          <Alert severity="info" sx={{ mb: '1.0rem', borderRadius: '0.25rem' }}>
            {LABELS.preAuthOptional}
          </Alert>

          <Autocomplete
            fullWidth
            options={availablePreAuths}
            loading={loadingPreAuths}
            value={availablePreAuths.find((pa) => pa.id === formData.preAuthorizationId) || null}
            onChange={(event, newValue) => {
              setFormData((prev) => ({
                ...prev,
                preAuthorizationId: newValue ? newValue.id : ''
              }));
            }}
            getOptionLabel={(option) => `${option.referenceNumber} - ${option.serviceName}`}
            renderOption={(props, option) => (
              <Box component="li" {...props}>
                <Stack sx={{ width: '100%' }}>
                  <Typography variant="body2" fontWeight={500}>
                    {option.referenceNumber}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {option.serviceName}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    الحالة: {option.status === 'APPROVED' ? 'موافق عليه' : 'تم الاطلاع'}
                  </Typography>
                </Stack>
              </Box>
            )}
            renderInput={(params) => (
              <TextField
                {...params}
                label={LABELS.selectPreAuth}
                placeholder={loadingPreAuths ? 'جاري التحميل...' : LABELS.noPreAuth}
                InputProps={{
                  ...params.InputProps,
                  endAdornment: (
                    <>
                      {loadingPreAuths ? <CircularProgress color="inherit" size={20} /> : null}
                      {params.InputProps.endAdornment}
                    </>
                  )
                }}
              />
            )}
            disabled={submitting || success}
            noOptionsText={LABELS.noPreAuth}
          />

          {formData.preAuthorizationId && (
            <Alert severity="info" sx={{ mt: '1.0rem', borderRadius: '0.25rem' }}>
              تم اختيار موافقة مسبقة - سيتم ربطها بالمطالبة وتحديث حالتها إلى "مستخدم" تلقائياً
            </Alert>
          )}
        </Grid>
      </Grid>
    </FormSection>
  );
}
