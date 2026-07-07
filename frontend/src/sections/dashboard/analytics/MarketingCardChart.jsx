// material-ui
import { useTheme } from '@mui/material/styles';

import { LineChart } from '@mui/x-charts';

// project imports
import { withAlpha } from 'utils/colorUtils';

// Define the data points for the line chart
const data = [100, 140, 100, 240, 115, 125, 90, 100, 80, 150, 160, 150, 170, 155, 150, 160, 145, 200, 140, 160];

const xAxisData = [
  '2025-03-26',
  '2025-03-27',
  '2025-03-28',
  '2025-03-29',
  '2025-03-30',
  '2025-03-31',
  '2025-04-01',
  '2025-04-02',
  '2025-04-03',
  '2025-04-04',
  '2025-04-05',
  '2025-04-06',
  '2025-04-07',
  '2025-04-08',
  '2025-04-09',
  '2025-04-10',
  '2025-04-11',
  '2025-04-12',
  '2025-04-13',
  '2025-04-14'
];

// ==============================|| ANALYTICS - MARKETING ||============================== //

export default function MarketingCardChart() {
  const theme = useTheme();

  return (
    <LineChart
      hideLegend
      height={100}
      xAxis={[{ id: 'my-x-axis', scaleType: 'band', data: xAxisData, position: 'none' }]}
      yAxis={[{ position: 'none' }]}
      margin={{ top: -49, bottom: 0, left: -10, right: -10 }}
      series={[
        {
          curve: 'linear',
          data,
          showMark: false,
          area: true,
          id: 'MarketingChart',
          color: theme.vars.palette.primary.main,
          label: 'Marketing',
          valueFormatter: (value) => `$ ${value}`
        }
      ]}
      slotProps={{ tooltip: { trigger: 'axis', sx: { caption: { display: 'none' } } } }}
      sx={{
        '& .MuiLineElement-root': { strokeWidth: 1.5 },
        '& .MuiAreaElement-series-MarketingChart': { fill: `url('#myGradient3')`, paintOrder: 'stroke' }
      }}
    >
      <defs>
        <linearGradient id="myGradient3" gradientTransform="rotate(90)">
          <stop offset="0%" stopColor={withAlpha(theme.vars.palette.primary.main, 0.2)} />
          <stop offset="100%" stopColor={withAlpha(theme.vars.palette.background.default, 0.4)} />
        </linearGradient>
      </defs>
    </LineChart>
  );
}
