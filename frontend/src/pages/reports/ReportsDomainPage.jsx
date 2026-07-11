import { useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { alpha, useTheme } from '@mui/material/styles';

import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardActionArea from '@mui/material/CardActionArea';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';

import { ArrowForward, Assessment } from '@mui/icons-material';

import ModernPageHeader from 'components/tba/ModernPageHeader';
import {
  getDomainByKey,
  getReportsByClassification,
  getReportsByDomain
} from '../../reporting/reportEngine';

const ClassificationSection = ({ title, reports, onReportClick }) => {
  if (!reports.length) return null;

  return (
    <Box sx={{ mt: 3 }}>
      <Typography variant="h4" sx={{ mb: 1.5, fontWeight: 700 }}>
        {title}
      </Typography>
      <Grid container spacing={2}>
        {reports.map((report) => (
          <Grid key={report.code} size={{ xs: 12, md: 6 }}>
            <Card variant="outlined" sx={{ height: '100%' }}>
              <CardActionArea disabled={!report.route || report.status !== 'active'} onClick={() => onReportClick(report.route)} sx={{ height: '100%' }}>
                <CardContent>
                  <Stack spacing={1.25}>
                    <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                        {report.titleAr}
                      </Typography>
                      <Chip
                        label={report.status === 'active' ? 'Active' : 'Planned'}
                        size="small"
                        color={report.status === 'active' ? 'success' : 'warning'}
                      />
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      {report.code} | v{report.version} | {report.owner}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {report.description}
                    </Typography>
                    <Stack direction="row" spacing={1} sx={{ pt: 0.5 }}>
                      <Chip label="Excel" size="small" variant="outlined" />
                      <Chip label="PDF" size="small" variant="outlined" />
                      <Chip label="Print" size="small" variant="outlined" />
                    </Stack>
                    <Stack direction="row" spacing={1} alignItems="center" justifyContent="flex-end">
                      <Typography variant="button" sx={{ fontSize: '0.75rem', fontWeight: 700 }}>
                        {report.status === 'active' ? 'دخول التقرير' : 'قيد التطوير'}
                      </Typography>
                      <ArrowForward sx={{ fontSize: '1rem' }} />
                    </Stack>
                  </Stack>
                </CardContent>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
};

export default function ReportsDomainPage() {
  const { domainKey } = useParams();
  const navigate = useNavigate();
  const theme = useTheme();

  const domain = useMemo(() => getDomainByKey(domainKey), [domainKey]);
  const reports = useMemo(() => getReportsByDomain(domainKey, 'report-center'), [domainKey]);
  const operationalReports = useMemo(
    () => getReportsByClassification(domainKey, 'operational', 'report-center'),
    [domainKey]
  );
  const analyticalReports = useMemo(
    () => getReportsByClassification(domainKey, 'analytical', 'report-center'),
    [domainKey]
  );

  if (!domain) {
    return (
      <Box sx={{ mt: 4 }}>
        <Typography variant="h4" sx={{ mb: 1 }}>النطاق غير موجود</Typography>
        <Typography variant="body2" sx={{ mb: 2 }} color="text.secondary">
          هذا النطاق غير مسجل في Report Registry.
        </Typography>
        <Chip label="العودة لمركز التقارير" onClick={() => navigate('/reports')} clickable />
      </Box>
    );
  }

  return (
    <>
      <ModernPageHeader
        title={domain.titleAr}
        subtitle={`Domain: ${domain.titleEn} | Registered reports: ${reports.length}`}
        icon={Assessment}
      />

      <Box
        sx={{
          mt: 2,
          p: 2,
          borderRadius: 2,
          border: `1px solid ${alpha(theme.palette.primary.main, 0.2)}`,
          bgcolor: alpha(theme.palette.primary.main, 0.04)
        }}
      >
        <Typography variant="body2" color="text.secondary">
          جميع التقارير في هذا النطاق تمر عبر Report Engine وتدار من خلال Report Registry.
        </Typography>
      </Box>

      <ClassificationSection title="Operational Reports" reports={operationalReports} onReportClick={(path) => path && navigate(path)} />
      <ClassificationSection title="Analytical Reports" reports={analyticalReports} onReportClick={(path) => path && navigate(path)} />
    </>
  );
}
