import PropTypes from 'prop-types';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import RefreshIcon from '@mui/icons-material/Refresh';
import PrintIcon from '@mui/icons-material/Print';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import AssessmentIcon from '@mui/icons-material/Assessment';

/**
 * ReportShell — the standard layout wrapper for every WAAD report page.
 * Header (title/description/last-refreshed + refresh/export/print) → filters →
 * summary → table (children). Strictly presentational and read-only: it exposes
 * no create/edit/delete/approve actions.
 */
export default function ReportShell({
  title,
  description,
  icon: Icon = AssessmentIcon,
  lastRefreshed,
  loading = false,
  onRefresh,
  onPrint,
  onExport,
  exporting = false,
  exportDisabled = false,
  printDisabled = false,
  error,
  filters,
  summary,
  children
}) {
  const refreshedText = lastRefreshed ? `آخر تحديث: ${new Date(lastRefreshed).toLocaleString('ar-LY')}` : '';

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, py: 1 }}>
      {/* Header */}
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ md: 'flex-start' }}>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Box
            sx={{
              width: 44,
              height: 44,
              borderRadius: 2,
              bgcolor: 'primary.lighter',
              color: 'primary.main',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <Icon />
          </Box>
          <Box>
            <Typography variant="h4" sx={{ fontWeight: 800, lineHeight: 1.2 }}>
              {title}
            </Typography>
            {description && (
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {description}
              </Typography>
            )}
            {refreshedText && (
              <Typography variant="caption" sx={{ color: 'text.disabled' }}>
                {refreshedText}
              </Typography>
            )}
          </Box>
        </Stack>

        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }} useFlexGap>
          {onRefresh && (
            <Button variant="outlined" color="primary" startIcon={<RefreshIcon />} onClick={onRefresh} disabled={loading}>
              تحديث
            </Button>
          )}
          {onExport && (
            <Button
              variant="outlined"
              color="success"
              startIcon={exporting ? <CircularProgress size={16} color="inherit" /> : <FileDownloadIcon />}
              onClick={onExport}
              disabled={exportDisabled || exporting || loading}
            >
              تصدير Excel
            </Button>
          )}
          {onPrint && (
            <Button variant="outlined" color="inherit" startIcon={<PrintIcon />} onClick={onPrint} disabled={printDisabled || loading}>
              طباعة
            </Button>
          )}
        </Stack>
      </Stack>

      {error && <Alert severity="error">{error}</Alert>}

      {filters}
      {summary}
      {children}
    </Box>
  );
}

ReportShell.propTypes = {
  title: PropTypes.string.isRequired,
  description: PropTypes.string,
  icon: PropTypes.elementType,
  lastRefreshed: PropTypes.oneOfType([PropTypes.string, PropTypes.number, PropTypes.instanceOf(Date)]),
  loading: PropTypes.bool,
  onRefresh: PropTypes.func,
  onPrint: PropTypes.func,
  onExport: PropTypes.func,
  exporting: PropTypes.bool,
  exportDisabled: PropTypes.bool,
  printDisabled: PropTypes.bool,
  error: PropTypes.string,
  filters: PropTypes.node,
  summary: PropTypes.node,
  children: PropTypes.node
};
