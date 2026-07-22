import { useState } from 'react';
import { Card, CardContent, Chip, Stack, Typography } from '@mui/material';

/**
 * Collapsible section card used throughout the reviewer workspace.
 * Extracted as-is from the previous ClaimViewMedicalReview.jsx monolith.
 */
const SectionCard = ({ title, icon: Icon, children, defaultExpanded = true }) => {
  const [expanded, setExpanded] = useState(defaultExpanded);

  return (
    <Card
      sx={{
        mb: '0.75rem',
        border: 1,
        borderColor: 'divider',
        boxShadow: 1
      }}
    >
      <CardContent sx={{ p: '1.0rem', '&:last-child': { pb: '1.0rem' } }}>
        <Stack
          direction="row"
          alignItems="center"
          justifyContent="space-between"
          sx={{ mb: expanded ? 2 : 0, cursor: 'pointer' }}
          onClick={() => setExpanded(!expanded)}
        >
          <Stack direction="row" spacing={1} alignItems="center">
            {Icon && <Icon color="primary" />}
            <Typography variant="subtitle2" fontWeight={600}>
              {title}
            </Typography>
          </Stack>
          <Chip
            label={expanded ? 'إخفاء' : 'عرض'}
            size="small"
            variant="outlined"
            onClick={(e) => {
              e.stopPropagation();
              setExpanded(!expanded);
            }}
          />
        </Stack>
        {expanded && children}
      </CardContent>
    </Card>
  );
};

export default SectionCard;
