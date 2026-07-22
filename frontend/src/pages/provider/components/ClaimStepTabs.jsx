import { Box, Stack, Button, Typography } from '@mui/material';
import { CheckCircle as CheckCircleIcon } from '@mui/icons-material';

/**
 * Real, navigable workflow steps (Phase 3B) — replaces the old decorative
 * MUI `Stepper` whose active index was only ever derived from form-completion
 * state. Clicking a step (or a completed step) navigates directly to it;
 * forward navigation past the current step is still allowed (no hard block),
 * matching "block forward navigation only when required data truly prevents
 * continuation" — validation happens at Save Draft / Submit time, same as before.
 */
export function ClaimStepTabs({ steps, activeStep, completedSteps, onStepChange }) {
  return (
    <Stack direction="row" spacing={0.5} sx={{ overflowX: 'auto' }}>
      {steps.map((step, index) => {
        const isActive = index === activeStep;
        const isComplete = completedSteps[index];
        return (
          <Button
            key={step}
            onClick={() => onStepChange(index)}
            variant="text"
            sx={{
              flexShrink: 0,
              px: '0.9rem',
              py: '0.5rem',
              borderRadius: '0.375rem',
              textTransform: 'none',
              fontWeight: isActive ? 700 : 500,
              fontSize: '0.8rem',
              color: isActive ? 'primary.main' : isComplete ? 'success.main' : 'text.secondary',
              bgcolor: isActive ? 'primary.lighter' : 'transparent',
              '&:hover': { bgcolor: isActive ? 'primary.lighter' : 'action.hover' }
            }}
            startIcon={
              isComplete && !isActive ? (
                <CheckCircleIcon fontSize="small" />
              ) : (
                <Box
                  sx={{
                    width: '1.25rem',
                    height: '1.25rem',
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '0.7rem',
                    fontWeight: 700,
                    bgcolor: isActive ? 'primary.main' : 'action.selected',
                    color: isActive ? 'primary.contrastText' : 'text.secondary'
                  }}
                >
                  {index + 1}
                </Box>
              )
            }
          >
            <Typography component="span" variant="body2" sx={{ fontWeight: 'inherit', fontSize: 'inherit' }}>
              {step}
            </Typography>
          </Button>
        );
      })}
    </Stack>
  );
}
