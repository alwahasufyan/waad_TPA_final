import PropTypes from 'prop-types';
import { Chip } from '@mui/material';
import { PREAUTH_STATUS } from 'hooks/usePreApprovalsReport';

/**
 * PreAuthStatusChip Component
 *
 * Renders pre-authorization status as a colored chip
 */
const PreAuthStatusChip = ({ status }) => {
  const statusConfig = {
    [PREAUTH_STATUS.PENDING]: { color: 'warning', label: 'معلقة' },
    [PREAUTH_STATUS.UNDER_REVIEW]: { color: 'primary', label: 'قيد المراجعة' },
    [PREAUTH_STATUS.APPROVAL_IN_PROGRESS]: { color: 'warning', label: 'جاري معالجة الموافقة' },
    [PREAUTH_STATUS.APPROVED]: { color: 'success', label: 'موافق عليها' },
    [PREAUTH_STATUS.ACKNOWLEDGED]: { color: 'info', label: 'تم الاطلاع' },
    [PREAUTH_STATUS.REJECTED]: { color: 'error', label: 'مرفوضة' },
    [PREAUTH_STATUS.NEEDS_CORRECTION]: { color: 'warning', label: 'تحتاج تصحيح' },
    [PREAUTH_STATUS.EXPIRED]: { color: 'default', label: 'منتهية' },
    [PREAUTH_STATUS.CANCELLED]: { color: 'default', label: 'ملغاة' },
    [PREAUTH_STATUS.USED]: { color: 'secondary', label: 'مستخدمة' }
  };

  const config = statusConfig[status] || { color: 'default', label: status };

  return <Chip label={config.label} color={config.color} size="small" variant="outlined" />;
};

PreAuthStatusChip.propTypes = {
  status: PropTypes.string.isRequired
};

export default PreAuthStatusChip;
