// Shared icon map for dashboard module cards / daily-work items.
// Keyed by the `iconKey` strings used in config/dashboardCategories.js and
// hooks/useDailyWorkItems.js. Uses MUI icons already in the project.
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import PeopleIcon from '@mui/icons-material/People';
import DescriptionIcon from '@mui/icons-material/Description';
import BusinessIcon from '@mui/icons-material/Business';
import AssessmentIcon from '@mui/icons-material/Assessment';
import SpaceDashboardIcon from '@mui/icons-material/SpaceDashboard';
import SettingsIcon from '@mui/icons-material/Settings';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import AppsIcon from '@mui/icons-material/Apps';

export const MODULE_ICONS = {
  claims: ReceiptLongIcon,
  providers: LocalHospitalIcon,
  members: PeopleIcon,
  contracts: DescriptionIcon,
  employers: BusinessIcon,
  reports: AssessmentIcon,
  dashboard: SpaceDashboardIcon,
  settings: SettingsIcon,
  preauth: FactCheckIcon,
  time: AccessTimeIcon,
  all: AppsIcon
};

export const resolveModuleIcon = (iconKey) => MODULE_ICONS[iconKey] || AppsIcon;

// Per-module accent colours — give the Odoo-style tiles a calm, colourful spread
// (each module keeps a stable identity colour). Muted, professional medical tones.
export const MODULE_COLORS = {
  claims: '#3B6F91', // blue
  providers: '#2E7D52', // green
  members: '#6C63A8', // purple
  contracts: '#C58A16', // amber
  employers: '#147D75', // teal
  reports: '#B64B43', // clay red
  dashboard: '#2F7DA1', // sky
  settings: '#5B6B7A', // slate
  preauth: '#3B6F91',
  time: '#C58A16',
  all: '#667573'
};

export const resolveModuleColor = (iconKey) => MODULE_COLORS[iconKey] || '#667573';
