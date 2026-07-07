// material-ui
import { useTheme } from '@mui/material/styles';

import { LineChart } from '@mui/x-charts';

// project imports
import { withAlpha } from 'utils/colorUtils';

// Define data for the chart
const data = [1800, 1500, 1800, 1700, 1400, 1200, 1000, 800, 600, 500, 600, 800, 500, 700, 400, 600, 500, 600];

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
  '2025-04-12'
];

// ==============================|| ANALYTICS - ORDERS ||============================== //

export default function OrdersCardChart() {
  const theme = useTheme();

  return (
    <LineChart
      hideLegend
      height={100}
      xAxis={[{ id: 'my-orders', scaleType: 'band', data: xAxisData, position: 'none' }]}
      yAxis={[{ position: 'none' }]}
      margin={{ top: 0, bottom: 0, left: -10, right: -10 }}
      series={[{ type: 'line', label: 'Orders', data, showMark: false, area: true, id: 'Order', color: theme.vars.palette.error.main }]}
      slotProps={{ tooltip: { trigger: 'axis', sx: { caption: { display: 'none' } } } }}
      sx={{
        '& .MuiLineElement-root': { strokeWidth: 1.5 },
        '& .MuiAreaElement-series-Order': { fill: "url('#orderGradient')" }
      }}
    >
      <defs>
        <linearGradient id="orderGradient" gradientTransform="rotate(90)">
          <stop offset="10%" stopColor={withAlpha(theme.vars.palette.error.main, 0.4)} />
          <stop offset="110%" stopColor={withAlpha(theme.vars.palette.background.default, 0.4)} />
        </linearGradient>
      </defs>
    </LineChart>
  );
}
