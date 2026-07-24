import { useState } from 'react';
import { Card, CardContent, Chip, Stack, Typography } from '@mui/material';

/**
 * Collapsible section card used throughout the reviewer workspace.
 * Extracted as-is from the previous ClaimViewMedicalReview.jsx monolith.
 *
 * `headerExtra` renders additional content (e.g. a status chip, a help
 * tooltip icon) in the header row, before the expand/collapse chip — used to
 * surface compact status info without a separate full-width Alert banner.
 */
const SectionCard = ({ title, icon: Icon, children, defaultExpanded = true, headerExtra }) => {
  const [expanded, setExpanded] = useState(defaultExpanded);

  return (
    <Card
      sx={{
        border: 1,
        borderColor: 'divider',
        boxShadow: 1,
        height: '100%'
      }}
    >
      <CardContent sx={{ p: '0.75rem', '&:last-child': { pb: '0.75rem' } }}>
        <Stack
          direction="row"
          alignItems="center"
          justifyContent="space-between"
          sx={{ mb: expanded ? 1 : 0, cursor: 'pointer' }}
          onClick={() => setExpanded(!expanded)}
        >
          <Stack direction="row" spacing={1} alignItems="center">
            {Icon && <Icon color="primary" fontSize="small" />}
            <Typography variant="subtitle2" fontWeight={600}>
              {title}
            </Typography>
          </Stack>
          <Stack direction="row" spacing={1} alignItems="center" onClick={(e) => e.stopPropagation()}>
            {headerExtra}
            <Chip label={expanded ? 'إخفاء' : 'عرض'} size="small" variant="outlined" onClick={() => setExpanded(!expanded)} />
          </Stack>
        </Stack>
        {expanded && children}
      </CardContent>
    </Card>
  );
};

export default SectionCard;
