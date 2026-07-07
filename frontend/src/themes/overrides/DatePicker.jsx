// material-ui
import { CalendarOutlined } from '@ant-design/icons';

// ==============================|| OVERRIDES - DATE PICKER ||============================== //

export default function DatePicker() {
  return {
    MuiDatePicker: {
      defaultProps: {
        slots: { openPickerIcon: () => <CalendarOutlined /> }
      }
    }
  };
}
