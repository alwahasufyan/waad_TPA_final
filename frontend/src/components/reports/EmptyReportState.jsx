import PropTypes from 'prop-types';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import FilterAltOffIcon from '@mui/icons-material/FilterAltOff';

/**
 * EmptyReportState — shown when a report returns no rows for the active filters.
 * It never redirects to an operational page; it only offers to clear filters.
 */
export default function EmptyReportState({ onClear, message = 'لا توجد نتائج تطابق الفلاتر المحددة' }) {
  return (
    <Box sx={{ textAlign: 'center', py: 4, px: 2 }}>
      <SearchOffIcon sx={{ fontSize: 44, color: 'text.disabled', mb: 1 }} />
      <Typography variant="subtitle1" sx={{ fontWeight: 700, color: 'text.secondary', mb: 0.5 }}>
        {message}
      </Typography>
      <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block', mb: 2 }}>
        جرّب تعديل الفلاتر أو توسيع النطاق الزمني.
      </Typography>
      {onClear && (
        <Button size="small" variant="outlined" color="inherit" startIcon={<FilterAltOffIcon />} onClick={onClear}>
          مسح الفلاتر
        </Button>
      )}
    </Box>
  );
}

EmptyReportState.propTypes = {
  onClear: PropTypes.func,
  message: PropTypes.string
};
