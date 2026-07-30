import { useMemo } from 'react';
import { Box, Tab, Tabs } from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import {
  PeopleAlt as PeopleAltIcon,
  MergeType as MergeTypeIcon,
  Backup as BackupIcon,
  NotificationsActive as NotificationsIcon,
  BugReport as BugReportIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import KinshipMismatchChecker from './KinshipMismatchChecker';
import MemberDuplicatesResolver from './MemberDuplicatesResolver';
import BackupSettingsTab from './BackupSettingsTab';
import MonitoringSettingsTab from './MonitoringSettingsTab';
import SystemErrorLogTab from './SystemErrorLogTab';

const TABS = Object.freeze([
  { value: 'kinship', label: 'تصحيح بيانات المستفيدين', icon: PeopleAltIcon },
  { value: 'duplicates', label: 'دمج السجلات المتكررة', icon: MergeTypeIcon },
  { value: 'backup', label: 'النسخ الاحتياطي والاستعادة', icon: BackupIcon },
  { value: 'monitoring', label: 'التنبيهات والمراقبة', icon: NotificationsIcon },
  { value: 'errors', label: 'سجل أخطاء النظام', icon: BugReportIcon }
]);

const MaintenanceToolsPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab');
  const activeTab = useMemo(
    () => TABS.some((tab) => tab.value === requestedTab) ? requestedTab : TABS[0].value,
    [requestedTab]
  );

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', gap: 2 }}>
      <ModernPageHeader title="أدوات الصيانة" subtitle="أدوات الإدارة المتقدمة وصيانة بيانات النظام" icon={BugReportIcon} />
      <MainCard content={false} sx={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        <Tabs
          value={activeTab}
          onChange={(_event, value) => setSearchParams({ tab: value })}
          variant="scrollable"
          scrollButtons="auto"
          aria-label="أدوات الصيانة"
          sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}
        >
          {TABS.map(({ value, label, icon: Icon }) => (
            <Tab key={value} value={value} icon={<Icon />} iconPosition="start" label={label} />
          ))}
        </Tabs>
        <Box sx={{ height: 'calc(100% - 64px)', overflow: 'auto', p: 2 }}>
          {activeTab === 'kinship' && <KinshipMismatchChecker />}
          {activeTab === 'duplicates' && <MemberDuplicatesResolver />}
          {activeTab === 'backup' && <BackupSettingsTab />}
          {activeTab === 'monitoring' && <MonitoringSettingsTab />}
          {activeTab === 'errors' && <SystemErrorLogTab />}
        </Box>
      </MainCard>
    </Box>
  );
};

export default MaintenanceToolsPage;
