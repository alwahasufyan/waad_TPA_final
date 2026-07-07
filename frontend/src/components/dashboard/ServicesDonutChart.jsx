import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Card, CardContent, CardHeader, Box, Skeleton, useTheme } from '@mui/material';
import ReactApexChart from 'react-apexcharts';
import { useColorScheme } from '@mui/material/styles';
import { ThemeMode } from 'config';
import useConfig from 'hooks/useConfig';

/**
 * Donut Chart: توزيع الخدمات الطبية
 * Using ApexCharts
 */
const ServicesDonutChart = ({ data, loading }) => {
  const theme = useTheme();
  const { colorScheme } = useColorScheme();
  const {
    state: { fontFamily }
  } = useConfig();

  const [options, setOptions] = useState({
    chart: {
      id: 'services-distribution',
      type: 'donut',
      fontFamily: fontFamily,
      background: 'transparent'
    },
    labels: [],
    colors: [
      theme.palette.primary.main,
      theme.palette.secondary.main,
      theme.palette.success.main,
      theme.palette.warning.main,
      theme.palette.error.main,
      theme.palette.info.main
    ],
    legend: {
      show: true,
      position: 'bottom',
      horizontalAlign: 'center',
      fontFamily: fontFamily
    },
    dataLabels: {
      enabled: true,
      formatter: (val) => val.toFixed(1) + '%',
      style: {
        colors: [theme.palette.background.paper],
        fontFamily: fontFamily
      }
    },
    plotOptions: {
      pie: {
        donut: {
          size: '65%',
          labels: {
            show: true,
            total: {
              show: true,
              label: 'إجمالي',
              formatter: () => {
                const total = data?.reduce((sum, item) => sum + (item.count || item.value || 0), 0) || 0;
                return total.toLocaleString('en-US');
              },
              fontFamily: fontFamily
            }
          }
        }
      }
    },
    tooltip: {
      theme: colorScheme === ThemeMode.DARK ? 'dark' : 'light',
      y: {
        formatter: (val) => val.toLocaleString('en-US')
      }
    }
  });

  const [series, setSeries] = useState([]);

  useEffect(() => {
    if (data && data.length > 0) {
      const labels = data.map((item) => item.serviceName || item.name || 'غير محدد');
      const values = data.map((item) => item.count || item.value || 0);

      setOptions((prev) => ({
        ...prev,
        labels
      }));

      setSeries(values);
    }
  }, [data]);

  if (loading) {
    return (
      <Card>
        <CardHeader title="توزيع الخدمات الطبية" />
        <CardContent>
          <Skeleton variant="circular" width={350} height={350} sx={{ mx: 'auto' }} />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title="توزيع الخدمات الطبية" subheader="حسب نوع الخدمة" />
      <CardContent>
        <Box sx={{ direction: 'rtl' }}>
          <ReactApexChart options={options} series={series} type="donut" height={350} />
        </Box>
      </CardContent>
    </Card>
  );
};

ServicesDonutChart.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      serviceName: PropTypes.string,
      name: PropTypes.string,
      count: PropTypes.number,
      value: PropTypes.number
    })
  ),
  loading: PropTypes.bool
};

export default ServicesDonutChart;
