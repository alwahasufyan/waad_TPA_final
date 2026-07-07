// ==============================|| OVERRIDES - INPUT BASE ||============================== //

export default function InputBase() {
  return {
    MuiInputBase: {
      styleOverrides: {
        root: {
          fontSize: '0.9375rem', // 15px - default input font size
          lineHeight: 1.6
        },
        sizeSmall: {
          fontSize: '0.875rem' // 14px - increased from 12px
        },
        input: {
          '&::placeholder': {
            opacity: 0.7,
            fontSize: '0.9375rem'
          }
        }
      }
    }
  };
}
