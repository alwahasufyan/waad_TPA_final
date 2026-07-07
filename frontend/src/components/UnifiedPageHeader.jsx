/**
 * UnifiedPageHeader - Standardized Page Header for List Pages
 *
 * Provides consistent header across all list pages with:
 * - Page title and subtitle
 * - Breadcrumbs
 * - Add new button
 *
 * PDF export disabled - Excel is the official reporting format.
 * GenericDataTable remains UI-only.
 */

import PropTypes from 'prop-types';

// MUI Components
import { Button, Stack } from '@mui/material';

// MUI Icons
import AddIcon from '@mui/icons-material/Add';

// Project Components
import ModernPageHeader from 'components/tba/ModernPageHeader';

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const UnifiedPageHeader = ({
  // Page info
  title,
  subtitle,
  icon,
  breadcrumbs = [],

  // Add button
  showAddButton = true,
  addButtonLabel = 'إضافة جديد',
  onAddClick,
  addButtonDisabled = false,

  // Additional actions (legacy support)
  additionalActions = null,

  // Direct actions prop (takes precedence if provided)
  actions = null
}) => {
  // If actions prop is provided directly, use it as-is
  // Otherwise, build actions from additionalActions + add button
  const resolvedActions = actions || (
    <Stack direction="row" spacing={2}>
      {/* Additional Custom Actions */}
      {additionalActions}

      {/* Add New Button */}
      {showAddButton && onAddClick && (
        <Button variant="contained" startIcon={<AddIcon />} onClick={onAddClick} disabled={addButtonDisabled}>
          {addButtonLabel}
        </Button>
      )}
    </Stack>
  );

  return <ModernPageHeader title={title} subtitle={subtitle} icon={icon} breadcrumbs={breadcrumbs} actions={resolvedActions} />;
};

// ============================================================================
// PROP TYPES
// ============================================================================

UnifiedPageHeader.propTypes = {
  // Page info
  title: PropTypes.string.isRequired,
  subtitle: PropTypes.string,
  icon: PropTypes.elementType.isRequired,
  breadcrumbs: PropTypes.arrayOf(
    PropTypes.shape({
      label: PropTypes.string.isRequired,
      path: PropTypes.string
    })
  ),

  // Add button
  showAddButton: PropTypes.bool,
  addButtonLabel: PropTypes.string,
  onAddClick: PropTypes.func,
  addButtonDisabled: PropTypes.bool,

  // Additional actions (legacy support)
  additionalActions: PropTypes.node,

  // Direct actions prop (takes precedence)
  actions: PropTypes.node
};

export default UnifiedPageHeader;
