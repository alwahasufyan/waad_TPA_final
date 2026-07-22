import { Box, Stack, Typography } from '@mui/material';

/**
 * Compact info row (label + value) used throughout the reviewer workspace.
 * Extracted as-is from the previous ClaimViewMedicalReview.jsx monolith.
 */
const InfoRow = ({ label, value, icon: Icon }) => (
  <Box sx={{ mb: 1 }}>
    <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
      {Icon && <Icon sx={{ fontSize: '1.0rem', color: 'text.secondary' }} />}
      <Typography variant="caption" color="text.secondary" fontWeight={500}>
        {label}
      </Typography>
    </Stack>
    <Typography variant="body2" fontWeight={500}>
      {value || '-'}
    </Typography>
  </Box>
);

export default InfoRow;
