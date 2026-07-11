import { useNavigate } from 'react-router-dom';
import { useTheme } from '@mui/material/styles';
import { alpha } from '@mui/material/styles';

// material-ui
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardActionArea from '@mui/material/CardActionArea';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';

// icons
import {
  Assessment,
  Insights,
  CheckCircle,
  Pending,
  ArrowForward
} from '@mui/icons-material';

// project imports
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { getReportDomains, getDomainStats } from '../../reporting/reportEngine';

// ==============================|| REPORTS PAGE ||============================== //

/**
 * Reports Page - صفحة التقارير
 *
 * Dashboard للوصول إلى جميع التقارير المتاحة في النظام
 */

export default function ReportsPage() {
  const navigate = useNavigate();
  const theme = useTheme();
  const getColor = (c) => theme.palette[c]?.main ?? c;

  const domains = getReportDomains();

  const handleReportClick = (path) => {
    if (path) {
      navigate(path);
    }
  };

  return (
    <>
      <ModernPageHeader
        title="مركز التقارير الموحد"
        subtitle="Business-first Reports Center مع تصنيف تشغيلي وتحليلي"
        icon={Assessment}
      />

      {/* نطاقات التقارير */}
      <Box sx={{ mt: '2.0rem' }}>
        <Grid container spacing={3}>
          {domains.map((domain) => {
            const stats = getDomainStats(domain.key, 'report-center');
            return (
              <Grid key={domain.key} size={{ xs: 12, sm: 6, md: 4 }}>
                <Card
                  elevation={3}
                  sx={{
                    height: '100%',
                    borderRadius: '0.1875rem',
                    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                    '&:hover': {
                      boxShadow: 10,
                      transform: 'translateY(-8px)',
                      borderColor: getColor('primary')
                    },
                    border: '1px solid rgba(0,0,0,0.05)'
                  }}
                >
                  <CardActionArea onClick={() => handleReportClick(`/reports/domain/${domain.key}`)} sx={{ height: '100%', p: 1 }}>
                    <CardContent>
                      <Stack spacing={2.5}>
                        <Box
                          sx={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            width: '4.0rem',
                            height: '4.0rem',
                            borderRadius: '1.0rem',
                            bgcolor: alpha(getColor('primary'), 0.08),
                            boxShadow: `0 8px 16px -4px ${alpha(getColor('primary'), 0.2)}`
                          }}
                        >
                          <Insights sx={{ color: getColor('primary'), fontSize: '2.25rem' }} />
                        </Box>

                        <Box>
                          <Typography variant="h5" sx={{ fontWeight: 700, mb: 0.5, color: 'text.primary' }}>
                            {domain.titleAr}
                          </Typography>
                          <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500, letterSpacing: 0.5 }}>
                            {domain.titleEn}
                          </Typography>
                        </Box>

                        <Stack direction="row" spacing={1} flexWrap="wrap">
                          <Chip label={`Total ${stats.total}`} size="small" />
                          <Chip label={`Active ${stats.active}`} icon={<CheckCircle />} size="small" color="success" />
                          <Chip label={`Planned ${stats.planned}`} icon={<Pending />} size="small" color="warning" />
                        </Stack>

                        <Stack direction="row" spacing={1} alignItems="center">
                          <Box sx={{ flexGrow: 1 }} />
                          <Typography variant="button" sx={{ color: getColor('primary'), fontWeight: 700, fontSize: '0.75rem' }}>
                            دخول النطاق
                          </Typography>
                          <ArrowForward sx={{ color: getColor('primary'), fontSize: '1.125rem' }} />
                        </Stack>
                      </Stack>
                    </CardContent>
                  </CardActionArea>
                </Card>
              </Grid>
            );
          })}
        </Grid>
      </Box>

      {/* ملاحظة تذكيرية */}
      <Box sx={{ mt: '4.0rem', textAlign: 'center', opacity: 0.6 }}>
        <Typography variant="caption" color="text.secondary">
          جميع التقارير الجديدة تسجل أولاً في Report Registry وتعمل عبر Report Engine.
        </Typography>
      </Box>
    </>
  );
}



