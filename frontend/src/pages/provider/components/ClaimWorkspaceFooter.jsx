import { Box, Stack, Button, Chip, CircularProgress } from '@mui/material';
import {
  Send as SendIcon,
  Notes as NotesIcon,
  ArrowBack as ArrowBackIcon,
  ArrowForward as ArrowForwardIcon,
  CloudDone as CloudDoneIcon,
  CloudOff as CloudOffIcon,
  Sync as SyncIcon
} from '@mui/icons-material';
import { LABELS } from '../constants';

/**
 * Workspace footer (Phase 3B, fixes D3) — lives inside the workspace's own flex
 * column with `flexShrink: 0`, so it reserves real layout space rather than
 * floating over content. ALSO `position: sticky; bottom: 0` as a resilience
 * fallback: if an ancestor layout ends up scrolling the whole page instead of
 * only the work area (owner-reported: the side panel's height pushed this
 * footer below the fold), sticky keeps it pinned to the bottom of whichever
 * ancestor actually scrolls, so it's still reachable without extra clicks.
 * Persistent across every step: Previous/Next always available, Save Draft
 * always available, final submit only enabled on the last step.
 */
export function ClaimWorkspaceFooter({
  isFirstStep,
  isLastStep,
  onPrev,
  onNext,
  autosaveStatus,
  autosaveAt,
  submitting,
  submitMode,
  success,
  claimLines,
  handleSubmit,
  handleBack
}) {
  return (
    <Box
      sx={{
        flexShrink: 0,
        position: 'sticky',
        bottom: 0,
        zIndex: 10,
        borderTop: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
        boxShadow: '0 -2px 8px rgba(0,0,0,0.06)',
        px: '1.25rem',
        py: '0.75rem'
      }}
    >
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between" alignItems="center">
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Chip
            size="small"
            color={autosaveStatus === 'error' ? 'error' : autosaveStatus === 'saved' ? 'success' : 'default'}
            icon={
              autosaveStatus === 'saving' ? (
                <SyncIcon fontSize="small" />
              ) : autosaveStatus === 'saved' ? (
                <CloudDoneIcon fontSize="small" />
              ) : (
                <CloudOffIcon fontSize="small" />
              )
            }
            label={
              autosaveStatus === 'saving'
                ? 'جاري الحفظ التلقائي...'
                : autosaveStatus === 'saved'
                  ? `تم الحفظ تلقائياً ${autosaveAt ? new Date(autosaveAt).toLocaleTimeString('ar-LY', { hour: '2-digit', minute: '2-digit' }) : ''}`
                  : autosaveStatus === 'error'
                    ? 'تعذر الحفظ التلقائي'
                    : 'الحفظ التلقائي جاهز'
            }
            variant="outlined"
          />
          <Button
            size="small"
            variant="outlined"
            startIcon={
              submitting && submitMode === 'draft' ? <CircularProgress size={16} color="inherit" /> : <NotesIcon fontSize="small" />
            }
            onClick={() => handleSubmit(false)}
            disabled={submitting || claimLines.length === 0 || success}
            sx={{ borderRadius: '0.25rem' }}
          >
            {submitting && submitMode === 'draft' ? LABELS.savingDraft : LABELS.saveDraft}
          </Button>
        </Stack>

        <Stack direction="row" spacing={1.5} alignItems="center">
          <Button variant="text" startIcon={<ArrowBackIcon />} onClick={handleBack} disabled={submitting} sx={{ borderRadius: '0.25rem' }}>
            {LABELS.cancel}
          </Button>
          <Button
            variant="outlined"
            onClick={onPrev}
            disabled={isFirstStep || submitting}
            sx={{ borderRadius: '0.25rem', minWidth: '6rem' }}
          >
            السابق
          </Button>
          {!isLastStep ? (
            <Button
              variant="contained"
              endIcon={<ArrowForwardIcon sx={{ transform: 'scaleX(-1)' }} />}
              onClick={onNext}
              disabled={submitting}
              sx={{ borderRadius: '0.25rem', minWidth: '6rem' }}
            >
              التالي
            </Button>
          ) : (
            <Button
              variant="contained"
              color="primary"
              startIcon={submitting && submitMode === 'final' ? <CircularProgress size={18} color="inherit" /> : <SendIcon />}
              onClick={() => handleSubmit(true)}
              disabled={submitting || claimLines.length === 0 || success}
              sx={{ borderRadius: '0.25rem', px: '2rem', fontWeight: 800 }}
            >
              {submitting && submitMode === 'final' ? LABELS.submittingFinal : LABELS.submitFinal}
            </Button>
          )}
        </Stack>
      </Stack>
    </Box>
  );
}
