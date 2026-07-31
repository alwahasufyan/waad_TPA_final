/**
 * /admin/users — Users, Roles & Permissions
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1
 *
 * Tabbed shell at the existing /admin/users route (no new route created).
 * Tab 1 (المستخدمون) reuses the existing, already real-data-backed
 * UsersList/UserDetails/UserEdit pages (Phase 1/2/3A) unchanged. Tab 2
 * (الأدوار والصلاحيات) is new — the role/permission matrix backed by the
 * catalog endpoints added in this ticket. Tabs 3/4 have no backend endpoint
 * yet and are shown as documented placeholders, not faked functionality.
 */

import { useState } from 'react';

import { Box, Tabs, Tab, Alert, Typography } from '@mui/material';
import PeopleAltIcon from '@mui/icons-material/PeopleAlt';
import KeyIcon from '@mui/icons-material/Key';
import TuneIcon from '@mui/icons-material/Tune';
import HistoryIcon from '@mui/icons-material/History';

import ModernPageHeader from 'components/tba/ModernPageHeader';
import MainCard from 'components/MainCard';

import UsersList from './UsersList';
import RolePermissionsMatrix from './RolePermissionsMatrix';

const TabPanel = ({ value, index, children }) => {
  if (value !== index) return null;
  return <Box sx={{ pt: '1.5rem' }}>{children}</Box>;
};

const ComingSoonPanel = ({ message, followUp }) => (
  <MainCard>
    <Alert severity="info">{message}</Alert>
    <Typography variant="caption" color="text.secondary" sx={{ mt: '0.75rem', display: 'block' }}>
      المتابعة المطلوبة: {followUp}
    </Typography>
  </MainCard>
);

const AdminUsersRolesPage = () => {
  const [tab, setTab] = useState(0);

  return (
    <Box>
      <ModernPageHeader
        title="إدارة المستخدمين والأدوار والصلاحيات"
        subtitle="المستخدمون · الأدوار والصلاحيات · مصفوفة الصلاحيات"
        icon={PeopleAltIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'المستخدمين والصلاحيات' }]}
      />

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: '0.5rem' }}>
        <Tabs value={tab} onChange={(e, v) => setTab(v)} variant="scrollable" scrollButtons="auto">
          <Tab icon={<PeopleAltIcon sx={{ fontSize: '1.1rem' }} />} iconPosition="start" label="المستخدمون" />
          <Tab icon={<KeyIcon sx={{ fontSize: '1.1rem' }} />} iconPosition="start" label="الأدوار والصلاحيات" />
          <Tab icon={<TuneIcon sx={{ fontSize: '1.1rem' }} />} iconPosition="start" label="الصلاحيات الخاصة للمستخدمين" />
          <Tab icon={<HistoryIcon sx={{ fontSize: '1.1rem' }} />} iconPosition="start" label="سجل تغييرات الصلاحيات" />
        </Tabs>
      </Box>

      <TabPanel value={tab} index={0}>
        <UsersList />
      </TabPanel>

      <TabPanel value={tab} index={1}>
        <RolePermissionsMatrix />
      </TabPanel>

      <TabPanel value={tab} index={2}>
        <ComingSoonPanel
          message="الصلاحيات الخاصة للمستخدمين غير مفعّلة بعد — الجدول موجود في قاعدة البيانات (user_permission_overrides) وآلية الحساب موجودة (EffectivePermissionService)، لكن لا توجد واجهة برمجية (API) لإنشاء/سحب استثناء فردي بعد."
          followUp="WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI"
        />
      </TabPanel>

      <TabPanel value={tab} index={3}>
        <ComingSoonPanel
          message="سجل تغييرات الصلاحيات يحتاج endpoint في مرحلة لاحقة — سجلات التدقيق (user_audit_log) تُكتب فعليًا عند كل تغيير، لكن لا توجد واجهة قراءة مخصصة لعرضها هنا بعد."
          followUp="WAAD-RBAC-PERMISSION-AUDIT-UI-1"
        />
      </TabPanel>
    </Box>
  );
};

export default AdminUsersRolesPage;
