/**
 * Shared permission-group display metadata (labels + icons) for the
 * /admin/users "Roles & Permissions" and "Per-user permission overrides"
 * tabs — kept in one place so both matrices show identical group names/icons
 * instead of two independently-maintained copies.
 *
 * WAAD-RBAC-PERMISSION-GROUP-LABELING-1: names/icons deliberately mirror the
 * real sidebar's group titles/icons (menu-items/components.jsx,
 * components/dashboard/SystemCategoriesDialog.jsx). The 6 groups here are
 * still the coarser DB-level permissions.group_name buckets (each spans
 * several menu sections), not a 1:1 remap.
 */

import PeopleAltIcon from '@mui/icons-material/PeopleAlt';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import SettingsIcon from '@mui/icons-material/Settings';
import AssessmentIcon from '@mui/icons-material/Assessment';
import PaymentIcon from '@mui/icons-material/Payment';

export const GROUP_LABELS_AR = {
  records: 'المستفيدون وجهات العمل',
  network: 'الشبكة الطبية ومقدمو الخدمة',
  claims: 'المطالبات والموافقات',
  system: 'النظام والصيانة',
  reports: 'مركز التقارير',
  finance: 'التسويات والمالية'
};

export const GROUP_ICONS = {
  records: PeopleAltIcon,
  network: LocalHospitalIcon,
  claims: ReceiptLongIcon,
  system: SettingsIcon,
  reports: AssessmentIcon,
  finance: PaymentIcon
};

export default { GROUP_LABELS_AR, GROUP_ICONS };
