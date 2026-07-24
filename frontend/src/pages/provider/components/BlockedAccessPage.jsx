import { Box, Card, Typography, Button } from '@mui/material';
import { Lock as LockIcon, ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { LABELS } from '../constants';

/**
 * Shown when the claim-submission page is opened without a visit context
 * (architectural rule: no claim without a visit, SUPER_ADMIN can bypass).
 * Extracted verbatim from the pre-Phase-3B monolith.
 */
export function BlockedAccessPage({ onBack }) {
  return (
    <Box sx={{ maxWidth: '37.5rem', mx: 'auto', mt: '4.0rem' }}>
      <Card variant="outlined" sx={{ borderRadius: '0.1875rem', textAlign: 'center', p: '2.0rem' }}>
        <Box
          sx={{
            width: '5.0rem',
            height: '5.0rem',
            borderRadius: '50%',
            bgcolor: 'warning.lighter',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            mx: 'auto',
            mb: '1.5rem'
          }}
        >
          <LockIcon sx={{ fontSize: '2.5rem', color: 'warning.main' }} />
        </Box>
        <Typography variant="h5" fontWeight={600} gutterBottom>
          الوصول المباشر غير مسموح
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: '2.0rem' }}>
          {LABELS.visitRequired}
          <br />
          يرجى الانتقال إلى سجل الزيارات واختيار زيارة لإنشاء مطالبة منها.
        </Typography>
        <Button
          variant="contained"
          size="large"
          startIcon={<ArrowBackIcon />}
          onClick={onBack}
          sx={{ borderRadius: '0.25rem', px: '2.0rem' }}
        >
          الذهاب إلى سجل الزيارات
        </Button>
      </Card>
    </Box>
  );
}
