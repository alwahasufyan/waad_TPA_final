import PropTypes from 'prop-types';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';

/**
 * ActiveFilterChips — compact summary of the filters currently applied to the
 * report. Each chip is optionally removable (calls onRemove with the filter key).
 *
 * chips: [{ key, label, value }]  — only pass the filters that are actually set.
 */
export default function ActiveFilterChips({ chips = [], onRemove }) {
  if (!chips.length) return null;

  return (
    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
      <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700 }}>
        الفلاتر النشطة:
      </Typography>
      {chips.map((chip) => (
        <Chip
          key={chip.key}
          size="small"
          variant="outlined"
          color="primary"
          label={`${chip.label}: ${chip.value}`}
          onDelete={onRemove ? () => onRemove(chip.key) : undefined}
        />
      ))}
    </Stack>
  );
}

ActiveFilterChips.propTypes = {
  chips: PropTypes.arrayOf(PropTypes.shape({ key: PropTypes.string.isRequired, label: PropTypes.node.isRequired, value: PropTypes.node })),
  onRemove: PropTypes.func
};
