import { SystemErrorBoundary as ErrorBoundary } from 'components/ErrorBoundary';
import ModernEmptyState from 'components/tba/ModernEmptyState';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

/**
 * Table Error Boundary
 *
 * Specialized error boundary for table/list pages.
 * Provides a compact fallback UI suitable for table contexts.
 *
 * Usage:
 * <TableErrorBoundary>
 *   <TbaDataTable ... />
 * </TableErrorBoundary>
 */
const TableErrorBoundary = ({ children }) => {
  const fallbackUI = (
    <ModernEmptyState
      icon={ErrorOutlineIcon}
      title="حدث خطأ في عرض البيانات"
      message="يرجى تحديث الصفحة أو المحاولة لاحقاً"
      action={{
        label: 'تحديث الصفحة',
        onClick: () => window.location.reload()
      }}
    />
  );

  return <ErrorBoundary fallback={() => fallbackUI}>{children}</ErrorBoundary>;
};

export default TableErrorBoundary;
