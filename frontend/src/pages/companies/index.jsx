import { Button } from '@mui/material';
import { Business, Add } from '@mui/icons-material';
import MainCard from 'components/MainCard';
import { ModernPageHeader, ModernEmptyState } from 'components/tba';

/**
 * Companies Page
 * Manage insurance companies (TPA companies)
 */
const CompaniesPage = () => {
  return (
    <>
      <ModernPageHeader
        title="شركات التأمين"
        subtitle="إدارة شركات التأمين (TPA)"
        icon={Business}
        breadcrumbs={[{ label: 'شركات التأمين' }]}
        actions={
          <Button variant="contained" startIcon={<Add />} disabled>
            إضافة شركة
          </Button>
        }
      />

      <MainCard>
        <ModernEmptyState title="صفحة شركات التأمين قيد التطوير" description="سيتم إضافة إدارة شركات التأمين قريباً" height={300} />
      </MainCard>
    </>
  );
};

export default CompaniesPage;
